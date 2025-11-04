package com.project.tracking_system.service.order;

import com.project.tracking_system.dto.ReturnRequestUpdateResponse;
import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.OrderEpisode;
import com.project.tracking_system.entity.OrderReturnRequest;
import com.project.tracking_system.entity.OrderReturnRequestActionRequest;
import com.project.tracking_system.entity.OrderReturnRequestStatus;
import com.project.tracking_system.entity.ReturnRequestMode;
import com.project.tracking_system.entity.ReturnRequestStage;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.entity.ReturnRequestAction;
import com.project.tracking_system.repository.OrderReturnRequestActionRequestRepository;
import com.project.tracking_system.repository.OrderReturnRequestRepository;
import com.project.tracking_system.service.order.context.ReturnRequestActionContext;
import com.project.tracking_system.service.track.TrackParcelService;
import com.project.tracking_system.service.track.TrackViewCacheInvalidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Сервис управления заявками на возврат и обмен.
 * <p>
 * Инкапсулирует проверки идемпотентности, прав доступа и бизнес-инварианты:
 * запуск обмена возможен только один раз на эпизод и только из статуса «вручено».
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderReturnRequestService {

    private static final Set<OrderReturnRequestStatus> ACTIVE_STATUSES =
            Set.of(OrderReturnRequestStatus.REGISTERED, OrderReturnRequestStatus.EXCHANGE_APPROVED);

    private final OrderReturnRequestRepository returnRequestRepository;
    private final OrderReturnRequestActionRequestRepository actionRequestRepository;
    private final TrackParcelService trackParcelService;
    private final OrderEpisodeLifecycleService episodeLifecycleService;
    private final OrderExchangeService orderExchangeService;
    private final TrackViewCacheInvalidator trackViewCacheInvalidator;
    private final ReturnRequestWorkflow returnRequestWorkflow;

    /**
     * Типовые причины переключения режима заявки.
     * <p>
     * Значения помогают фиксировать контекст действия в логах и поддерживать единый интерфейс
     * при работе из разных каналов (админ-панель, Telegram, автоматические сценарии).
     * </p>
     */
    public enum ModeSwitchTrigger {
        /** Ручное решение менеджера из административного интерфейса. */
        MANUAL_DECISION,
        /** Отмена обмена с последующим закрытием обращения. */
        EXCHANGE_CANCELLATION,
        /** Инициатива покупателя из Telegram или другого внешнего канала. */
        CUSTOMER_REQUEST
    }

    /**
     * Обновляет трек обратной отправки и комментарий активной заявки.
     * <p>
     * Метод убеждается, что заявка принадлежит пользователю и находится в активном статусе,
     * затем нормализует ввод, сохраняя данные в репозитории.
     * </p>
     *
     * @param requestId    идентификатор заявки
     * @param parcelId     идентификатор посылки, к которой относится заявка
     * @param user         владелец посылки
     * @param reverseTrack новый трек обратной отправки или значение для очистки
     * @param comment      новый комментарий или значение для очистки
     * @return DTO с подтверждением сохранённых данных
     */
    @Transactional
    public ReturnRequestUpdateResponse updateReverseTrackAndComment(Long requestId,
                                                                    Long parcelId,
                                                                    User user,
                                                                    String reverseTrack,
                                                                    String comment) {
        OrderReturnRequest request = loadOwnedRequest(requestId, parcelId, user);
        if (!ACTIVE_STATUSES.contains(request.getStatus())) {
            throw new IllegalStateException("Заявку нельзя изменить в текущем статусе");
        }

        String normalizedTrack = normalizeReverseTrackNumber(reverseTrack);
        String normalizedComment = normalizeComment(comment);
        request.setReverseTrackNumber(normalizedTrack);
        request.setComment(normalizedComment);
        boolean trackProvided = normalizedTrack != null && !normalizedTrack.isBlank();
        request.setManualTrackOverride(request.isManualTrackOverride() || trackProvided);
        request.setResponsibleManager(user);
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        request.setStageUpdatedAt(now);
        request.snapshotHistory(true, user, now);

        OrderReturnRequest saved = returnRequestRepository.save(request);
        evictTrackDetailsCache(saved);
        log.info("Обновлены данные обратной отправки заявки {}", saved.getId());
        return new ReturnRequestUpdateResponse(
                saved.getId(),
                saved.getReverseTrackNumber(),
                saved.getComment(),
                saved.getStatus()
        );
    }

    /**
     * Регистрирует возврат для посылки, обеспечивая идемпотентность.
     *
     * @param parcelId        идентификатор посылки
     * @param user            автор заявки
     * @param idempotencyKey  внешний ключ, предотвращающий повторные регистрации
     * @param reason          текстовая причина возврата
     * @param comment         дополнительный комментарий пользователя
     * @param requestedAt     время запроса возврата пользователем
     * @param reverseTrack    трек обратной отправки (если есть)
     * @param exchangeRequested признак, что пользователь сразу запросил обмен
     * @return созданная или ранее зарегистрированная заявка
     */
    @Transactional
    public OrderReturnRequest registerReturn(Long parcelId,
                                             User user,
                                             String idempotencyKey,
                                             String reason,
                                             String comment,
                                             ZonedDateTime requestedAt,
                                             String reverseTrack,
                                             boolean exchangeRequested) {
        if (parcelId == null) {
            throw new IllegalArgumentException("Не указан идентификатор посылки");
        }
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Не указан пользователь");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Не указан идемпотентный ключ заявки");
        }

        String normalizedReason = normalizeReason(reason);
        String normalizedComment = normalizeComment(comment);
        ZonedDateTime normalizedRequestedAt = normalizeRequestedAt(requestedAt);
        String normalizedReverse = normalizeReverseTrackNumber(reverseTrack);

        Optional<OrderReturnRequest> existingByKey = returnRequestRepository.findByIdempotencyKey(idempotencyKey);
        if (existingByKey.isPresent()) {
            OrderReturnRequest existing = existingByKey.get();
            ensureOwnership(existing, user.getId());
            if (!Objects.equals(existing.getReason(), normalizedReason)
                    || !Objects.equals(existing.getComment(), normalizedComment)
                    || !Objects.equals(existing.getRequestedAt(), normalizedRequestedAt)
                    || !Objects.equals(existing.getReverseTrackNumber(), normalizedReverse)
                    || existing.isExchangeRequested() != exchangeRequested) {
                throw new IllegalStateException("Заявка с таким ключом уже зарегистрирована с другими данными");
            }
            return existing;
        }

        TrackParcel parcel = trackParcelService.findOwnedById(parcelId, user.getId())
                .orElseThrow(() -> new AccessDeniedException("Посылка не принадлежит пользователю"));

        if (parcel.getStatus() != GlobalStatus.DELIVERED) {
            throw new IllegalStateException("Заявка на возврат доступна только для статуса \"Вручена\"");
        }

        Optional<OrderReturnRequest> active = returnRequestRepository
                .findFirstByParcel_IdAndStatusIn(parcelId, ACTIVE_STATUSES);
        if (active.isPresent()) {
            log.debug("По посылке {} уже есть активная заявка {}", parcelId, active.get().getId());
            throw new IllegalStateException("У посылки уже есть активная заявка на возврат");
        }

        OrderEpisode episode = episodeLifecycleService.ensureEpisode(parcel);

        OrderReturnRequest request = new OrderReturnRequest();
        request.setEpisode(episode);
        request.setParcel(parcel);
        request.setCreatedBy(user);
        request.setCreatedAt(ZonedDateTime.now(ZoneOffset.UTC));
        request.setRequestedAt(normalizedRequestedAt);
        request.setReason(normalizedReason);
        request.setComment(normalizedComment);
        request.setReverseTrackNumber(normalizedReverse);
        request.setStatus(OrderReturnRequestStatus.REGISTERED);
        request.setIdempotencyKey(idempotencyKey);
        request.setExchangeRequested(exchangeRequested);
        request.setStore(parcel.getStore());
        request.setResponsibleManager(user);
        ReturnRequestMode mode = exchangeRequested ? ReturnRequestMode.EXCHANGE : ReturnRequestMode.RETURN;
        request.setMode(mode);
        request.setManualTrackOverride(normalizedReverse != null && !normalizedReverse.isBlank());
        request.setExchangeTrackNumber(null);
        request.setExchangeTrackAssignedAt(null);
        ZonedDateTime stageMoment = normalizedRequestedAt != null ? normalizedRequestedAt : request.getCreatedAt();
        ReturnRequestStage initialStage = returnRequestWorkflow.initialStage(mode);
        request.setStage(initialStage);
        request.setStageStartedAt(stageMoment);
        request.setStageUpdatedAt(stageMoment);
        request.setManualStageOverride(false);
        request.snapshotHistory(false, user, stageMoment);

        // Автоматический запуск обмена оставляем ручным, чтобы менеджер успел проверить данные перед созданием посылки.

        OrderReturnRequest saved = returnRequestRepository.save(request);
        evictTrackDetailsCache(saved);
        log.info("Зарегистрирована заявка на возврат {} для посылки {}", saved.getId(), parcelId);
        return saved;
    }

    /**
     * Одобряет запуск обмена по заявке.
     * <p>
     * Метод переводит заявку в статус обмена и фиксирует менеджера, принявшего решение.
     * Создание обменной посылки выполняется отдельным действием через {@link #createExchangeParcel(Long, Long, User)},
     * чтобы соблюсти SRP и дать менеджеру время на проверку данных перед оформлением отправления.
     * </p>
     *
     * @param requestId идентификатор заявки
     * @param parcelId  идентификатор посылки
     * @param user      автор решения
     * @return обновлённая заявка после одобрения обмена
     */
    @Transactional
    public OrderReturnRequest approveExchange(Long requestId, Long parcelId, User user) {
        return switchMode(requestId, parcelId, user, ReturnRequestMode.EXCHANGE, ModeSwitchTrigger.MANUAL_DECISION);
    }

    /**
     * Переключает режим обработки заявки на возврат или обмен.
     * <p>
     * Метод реализует единый шаблон перехода между режимами: проверяет допустимость операции
     * согласно матрице состояний, нормализует стадию через {@link ReturnRequestWorkflow}
     * и выполняет побочные действия с обменными посылками через профильные сервисы.
     * </p>
     *
     * @param requestId   идентификатор заявки
     * @param parcelId    идентификатор посылки
     * @param user        менеджер, инициировавший действие
     * @param targetMode  целевой режим обработки
     * @param trigger     причина переключения (используется в логировании)
     * @return обновлённая заявка после сохранения
     */
    @Transactional
    public OrderReturnRequest switchMode(Long requestId,
                                         Long parcelId,
                                         User user,
                                         ReturnRequestMode targetMode,
                                         ModeSwitchTrigger trigger) {
        if (targetMode == null) {
            throw new IllegalArgumentException("Не указан целевой режим заявки");
        }
        ModeSwitchTrigger effectiveTrigger = trigger != null ? trigger : ModeSwitchTrigger.MANUAL_DECISION;
        OrderReturnRequest request = loadOwnedRequest(requestId, parcelId, user);
        OrderReturnRequest updated = switch (targetMode) {
            case EXCHANGE -> applyExchangeMode(request, user);
            case RETURN -> applyReturnMode(request, user, effectiveTrigger);
        };
        OrderReturnRequest saved = returnRequestRepository.save(updated);
        evictTrackDetailsCache(saved);
        log.info("Заявка {} переведена в режим {} по причине {}", saved.getId(), targetMode, effectiveTrigger);
        return saved;
    }

    /**
     * Перегрузка переключения режима без указания причины (используется для совместимости вызовов).
     */
    @Transactional
    public OrderReturnRequest switchMode(Long requestId,
                                         Long parcelId,
                                         User user,
                                         ReturnRequestMode targetMode) {
        return switchMode(requestId, parcelId, user, targetMode, ModeSwitchTrigger.MANUAL_DECISION);
    }

    /**
     * Создаёт обменную посылку по ранее одобренной заявке.
     * <p>
     * Метод убеждается, что заявка принадлежит пользователю, переведена в статус обмена
     * и что ранее не было создано активной обменной посылки. После валидации делегируется
     * {@link OrderExchangeService} для фактического создания отправления.
     * </p>
     *
     * @param requestId идентификатор заявки на обмен
     * @param parcelId  идентификатор исходной посылки
     * @param user      пользователь магазина, выполняющий действие
     * @return созданная обменная посылка
     */
    @Transactional
    public TrackParcel createExchangeParcel(Long requestId, Long parcelId, User user) {
        OrderReturnRequest request = loadOwnedRequest(requestId, parcelId, user);
        if (request.getStatus() != OrderReturnRequestStatus.EXCHANGE_APPROVED) {
            throw new IllegalStateException("Обменная посылка доступна только после одобрения обмена");
        }
        if (!canCreateExchangeParcel(request)) {
            throw new IllegalStateException("Обменная посылка уже создана или находится в работе");
        }
        TrackParcel replacement = orderExchangeService.createExchangeParcel(request);
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime assignedMoment = Optional.ofNullable(replacement)
                .map(TrackParcel::getTimestamp)
                .orElse(now);
        request.setResponsibleManager(user);
        request.setExchangeTrackNumber(Optional.ofNullable(replacement).map(TrackParcel::getNumber).orElse(null));
        request.setExchangeTrackAssignedAt(assignedMoment);
        returnRequestWorkflow.transitionToStage(request, ReturnRequestStage.EXCHANGE_SENT, true, user, assignedMoment);
        OrderReturnRequest saved = returnRequestRepository.save(request);
        evictTrackDetailsCache(saved);
        log.info("Создана обменная посылка {} для заявки {}",
                Optional.ofNullable(replacement).map(TrackParcel::getId).orElse(null),
                saved.getId());
        return replacement;
    }

    /**
     * Закрывает заявку без запуска обмена.
     */
    @Transactional
    public OrderReturnRequest closeWithoutExchange(Long requestId, Long parcelId, User user) {
        OrderReturnRequest request = loadOwnedRequest(requestId, parcelId, user);

        if (request.getStatus() != OrderReturnRequestStatus.REGISTERED) {
            throw new IllegalStateException("Заявка уже обработана");
        }

        ZonedDateTime closeMoment = ZonedDateTime.now(ZoneOffset.UTC);
        request.setStatus(OrderReturnRequestStatus.CLOSED_NO_EXCHANGE);
        request.setClosedBy(user);
        request.setClosedAt(closeMoment);
        request.setMode(ReturnRequestMode.RETURN);
        request.setResponsibleManager(user);
        returnRequestWorkflow.transitionToStage(request, ReturnRequestStage.INBOUND_PICKED_UP, true, user, closeMoment);

        OrderReturnRequest saved = returnRequestRepository.save(request);
        evictTrackDetailsCache(saved);
        log.info("Заявка {} закрыта без обмена", saved.getId());
        return saved;
    }

    /**
     * Подтверждает вручную обработку возврата магазином.
     * <p>
     * Метод фиксирует момент приёма возврата, не переводя заявку в финальный статус,
     * что соответствует принципу SRP: сервис управляет только бизнес-событиями,
     * а решение об обмене принимается отдельно. Отдельный флаг используется для
     * отображения завершённости этапа «Приём возврата магазином».
     * </p>
     *
     * @param requestId идентификатор заявки
     * @param parcelId  идентификатор исходной посылки
     * @param user      менеджер, подтверждающий обработку
     * @return обновлённая заявка
     * @implNote Метод разрешает подтверждение и после закрытия без обмена, чтобы менеджер мог
     * завершить этап обработки даже после отмены обмена.
     */
    @Transactional
    public OrderReturnRequest confirmReturnProcessing(Long requestId, Long parcelId, User user) {
        OrderReturnRequest request = loadOwnedRequest(requestId, parcelId, user);
        OrderReturnRequestStatus status = request.getStatus();
        if (status != OrderReturnRequestStatus.REGISTERED
                && status != OrderReturnRequestStatus.CLOSED_NO_EXCHANGE) {
            throw new IllegalStateException("Подтверждение доступно только для активной заявки или закрытия без обмена");
        }
        if (request.isReturnReceiptConfirmed()) {
            return request;
        }
        markReturnProcessingConfirmed(request, user, null);
        OrderReturnRequest saved = returnRequestRepository.save(request);
        evictTrackDetailsCache(saved);
        log.info("Получение возврата подтверждено вручную для заявки {}", saved.getId());
        return saved;
    }

    /**
     * Фиксирует вручную отправку возврата покупателем.
     */
    @Transactional
    public OrderReturnRequest markOutboundSent(Long requestId,
                                               Long parcelId,
                                               User user,
                                               ZonedDateTime stageMoment) {
        OrderReturnRequest request = loadOwnedRequest(requestId, parcelId, user);
        if (!canMarkOutboundSent(request)) {
            throw new IllegalStateException("Стадия отправки возврата недоступна для текущего состояния заявки");
        }
        ZonedDateTime normalizedMoment = normalizeStageMoment(stageMoment);
        request.setResponsibleManager(user);
        returnRequestWorkflow.transitionToStage(request, ReturnRequestStage.OUTBOUND_SENT, true, user, normalizedMoment);
        OrderReturnRequest saved = returnRequestRepository.save(request);
        evictTrackDetailsCache(saved);
        log.info("Стадия OUTBOUND_SENT зафиксирована вручную для заявки {}", saved.getId());
        return saved;
    }

    /**
     * Фиксирует вручную прибытие возврата в пункт назначения магазина.
     */
    @Transactional
    public OrderReturnRequest markInboundArrived(Long requestId,
                                                 Long parcelId,
                                                 User user,
                                                 ZonedDateTime stageMoment) {
        OrderReturnRequest request = loadOwnedRequest(requestId, parcelId, user);
        if (!canMarkInboundArrived(request)) {
            throw new IllegalStateException("Стадия прибытия возврата недоступна для текущего состояния заявки");
        }
        ZonedDateTime normalizedMoment = normalizeStageMoment(stageMoment);
        request.setResponsibleManager(user);
        returnRequestWorkflow.transitionToStage(request, ReturnRequestStage.INBOUND_ARRIVED, true, user, normalizedMoment);
        OrderReturnRequest saved = returnRequestRepository.save(request);
        evictTrackDetailsCache(saved);
        log.info("Стадия INBOUND_ARRIVED зафиксирована вручную для заявки {}", saved.getId());
        return saved;
    }

    /**
     * Фиксирует вручную получение возврата магазином и завершает этап обработки.
     */
    @Transactional
    public OrderReturnRequest markInboundPickedUp(Long requestId,
                                                  Long parcelId,
                                                  User user,
                                                  ZonedDateTime stageMoment) {
        OrderReturnRequest request = loadOwnedRequest(requestId, parcelId, user);
        if (!canMarkInboundPickedUp(request)) {
            throw new IllegalStateException("Стадия приёма возврата недоступна для текущего состояния заявки");
        }
        ZonedDateTime normalizedMoment = normalizeStageMoment(stageMoment);
        markReturnProcessingConfirmed(request, user, normalizedMoment);
        OrderReturnRequest saved = returnRequestRepository.save(request);
        evictTrackDetailsCache(saved);
        log.info("Стадия INBOUND_PICKED_UP зафиксирована вручную для заявки {}", saved.getId());
        return saved;
    }

    /**
     * Регистрирует обменную посылку без автоматического создания в системе.
     */
    @Transactional
    public OrderReturnRequest registerExchangeParcel(Long requestId,
                                                     Long parcelId,
                                                     User user,
                                                     String exchangeTrack,
                                                     ZonedDateTime stageMoment) {
        OrderReturnRequest request = loadOwnedRequest(requestId, parcelId, user);
        if (request.getStatus() != OrderReturnRequestStatus.EXCHANGE_APPROVED) {
            throw new IllegalStateException("Ручная регистрация доступна только для одобренного обмена");
        }
        if (!canRegisterExchangeParcel(request)) {
            throw new IllegalStateException("Обменная посылка уже зарегистрирована или ожидает обработки");
        }
        String normalizedTrack = normalizeExchangeTrackNumber(exchangeTrack);
        ZonedDateTime normalizedMoment = normalizeStageMoment(stageMoment);
        assignExchangeTrack(request, normalizedTrack, normalizedMoment, user);
        returnRequestWorkflow.transitionToStage(request, ReturnRequestStage.EXCHANGE_REGISTERED, true, user, normalizedMoment);
        OrderReturnRequest saved = returnRequestRepository.save(request);
        evictTrackDetailsCache(saved);
        log.info("Обменная посылка зарегистрирована вручную для заявки {}", saved.getId());
        return saved;
    }

    /**
     * Фиксирует отправку обменной посылки, обновляя её трек-номер.
     */
    @Transactional
    public OrderReturnRequest markExchangeSent(Long requestId,
                                               Long parcelId,
                                               User user,
                                               String exchangeTrack,
                                               ZonedDateTime stageMoment) {
        OrderReturnRequest request = loadOwnedRequest(requestId, parcelId, user);
        if (request.getStatus() != OrderReturnRequestStatus.EXCHANGE_APPROVED) {
            throw new IllegalStateException("Стадия отправки обмена недоступна для текущего состояния заявки");
        }
        if (!canTransitionToStage(request, ReturnRequestStage.EXCHANGE_SENT)) {
            throw new IllegalStateException("Переход на стадию отправки обмена запрещён текущими правилами");
        }
        String normalizedTrack = normalizeExchangeTrackNumber(exchangeTrack);
        ZonedDateTime normalizedMoment = normalizeStageMoment(stageMoment);
        assignExchangeTrack(request, normalizedTrack, normalizedMoment, user);
        returnRequestWorkflow.transitionToStage(request, ReturnRequestStage.EXCHANGE_SENT, true, user, normalizedMoment);
        OrderReturnRequest saved = returnRequestRepository.save(request);
        evictTrackDetailsCache(saved);
        log.info("Стадия EXCHANGE_SENT зафиксирована вручную для заявки {}", saved.getId());
        return saved;
    }

    /**
     * Фиксирует вручную доставку обменной посылки.
     */
    @Transactional
    public OrderReturnRequest markExchangeDelivered(Long requestId,
                                                    Long parcelId,
                                                    User user,
                                                    ZonedDateTime stageMoment) {
        OrderReturnRequest request = loadOwnedRequest(requestId, parcelId, user);
        if (!canMarkExchangeDelivered(request)) {
            throw new IllegalStateException("Стадия доставки обмена недоступна для текущего состояния заявки");
        }
        ZonedDateTime normalizedMoment = normalizeStageMoment(stageMoment);
        request.setResponsibleManager(user);
        returnRequestWorkflow.transitionToStage(request, ReturnRequestStage.EXCHANGE_DELIVERED, true, user, normalizedMoment);
        OrderReturnRequest saved = returnRequestRepository.save(request);
        evictTrackDetailsCache(saved);
        log.info("Стадия EXCHANGE_DELIVERED зафиксирована вручную для заявки {}", saved.getId());
        return saved;
    }

    /**
     * Отменяет обмен по активной заявке пользователя.
     */
    @Transactional
    /**
     * Регистрирует запрос покупателя магазину по активной заявке обмена.
     * <p>
     * Используется, когда отмена или перевод обмена невозможны автоматически,
     * например, из-за отправки обменной посылки. Метод проверяет принадлежность
     * заявки пользователю и создаёт единственный невыполненный запрос на указанное
     * действие. Повторные обращения возвращают уже существующий запрос.
     * </p>
     *
     * @param requestId идентификатор заявки
     * @param parcelId  идентификатор посылки
     * @param user      владелец посылки
     * @param customer  покупатель, обратившийся в Telegram
     * @param action    тип желаемого действия магазина
     * @return созданный или ранее сохранённый запрос
     */
    @Transactional
    public OrderReturnRequestActionRequest requestMerchantAction(Long requestId,
                                                                Long parcelId,
                                                                User user,
                                                                Customer customer,
                                                                ReturnRequestAction action) {
        if (action == null) {
            throw new IllegalArgumentException("Не указан тип запроса к магазину");
        }
        if (customer == null || customer.getId() == null) {
            throw new IllegalArgumentException("Не указан покупатель");
        }
        OrderReturnRequest request = loadOwnedRequest(requestId, parcelId, user);
        if (request.getStatus() != OrderReturnRequestStatus.EXCHANGE_APPROVED) {
            throw new IllegalStateException("Запрос возможен только для обменных заявок");
        }

        Optional<OrderReturnRequestActionRequest> existing = actionRequestRepository
                .findFirstByReturnRequest_IdAndActionAndProcessedAtIsNull(request.getId(), action);
        if (existing.isPresent()) {
            return existing.get();
        }

        OrderReturnRequestActionRequest actionRequest = new OrderReturnRequestActionRequest();
        actionRequest.setReturnRequest(request);
        actionRequest.setCustomer(customer);
        actionRequest.setAction(action);
        OrderReturnRequestActionRequest saved = actionRequestRepository.save(actionRequest);
        log.info("Создан запрос {} к магазину по заявке {}", action, request.getId());
        return saved;
    }

    /**
     * Возвращает активную заявку по посылке, если она существует.
     */
    @Transactional(readOnly = true)
    public Optional<OrderReturnRequest> findCurrentForParcel(Long parcelId) {
        if (parcelId == null) {
            return Optional.empty();
        }
        return returnRequestRepository.findFirstByParcel_IdAndStatusIn(parcelId, ACTIVE_STATUSES);
    }

    /**
     * Проверяет, можно ли запускать обмен по заявке.
     */
    @Transactional(readOnly = true)
    public boolean canStartExchange(OrderReturnRequest request) {
        if (request == null || request.getStatus() != OrderReturnRequestStatus.REGISTERED) {
            return false;
        }
        OrderEpisode episode = request.getEpisode();
        Long episodeId = episode != null ? episode.getId() : null;
        ReturnRequestStage currentStage = request.getStage() != null
                ? request.getStage()
                : ReturnRequestStage.NEW;
        ReturnRequestStage normalizedStage = returnRequestWorkflow
                .adjustStageForMode(ReturnRequestMode.EXCHANGE, currentStage);
        if (!returnRequestWorkflow.canTransition(ReturnRequestMode.EXCHANGE, normalizedStage, ReturnRequestStage.EXCHANGE_REGISTERED)) {
            return false;
        }
        if (episodeId == null) {
            return true;
        }
        return !returnRequestRepository.existsByEpisode_IdAndStatus(episodeId,
                OrderReturnRequestStatus.EXCHANGE_APPROVED);
    }

    /**
     * Проверяет, можно ли вернуть обменную заявку в режим возврата без закрытия обращения.
     */
    private boolean canSwitchToReturnMode(OrderReturnRequest request) {
        if (request == null || request.getStatus() != OrderReturnRequestStatus.EXCHANGE_APPROVED) {
            return false;
        }
        if (isExchangeShipmentDispatched(request)) {
            return false;
        }
        return getExchangeCancellationBlockReason(request).isEmpty();
    }

    /**
     * Проверяет, доступна ли отмена обмена с последующим закрытием заявки.
     */
    private boolean canCancelExchangeAction(OrderReturnRequest request) {
        return canSwitchToReturnMode(request);
    }

    /**
     * Возвращает причину недоступности отмены обмена, если магазин уже указал трек.
     *
     * @param request заявка на обмен
     * @return текстовое сообщение или пустое значение, если ограничений нет
     */
    @Transactional(readOnly = true)
    public Optional<String> getExchangeCancellationBlockReason(OrderReturnRequest request) {
        if (request == null || request.getStatus() != OrderReturnRequestStatus.EXCHANGE_APPROVED) {
            return Optional.empty();
        }
        try {
            orderExchangeService.getLatestExchangeParcelOrThrowIfTracked(request);
            return Optional.empty();
        } catch (IllegalStateException ex) {
            return Optional.ofNullable(ex.getMessage());
        }
    }

    /**
     * Проверяет, можно ли вернуть обменную заявку в статус возврата.
     *
     * @param request заявка на обмен
     * @return {@code true}, если обмен ещё не отправлен и не заблокирован
     */
    @Transactional(readOnly = true)
    public boolean canReopenAsReturn(OrderReturnRequest request) {
        if (request == null || request.getStatus() != OrderReturnRequestStatus.EXCHANGE_APPROVED) {
            return false;
        }
        if (isExchangeShipmentDispatched(request)) {
            return false;
        }
        return getExchangeCancellationBlockReason(request).isEmpty();
    }

    /**
     * Проверяет, можно ли отменить обмен без закрытия эпизода.
     *
     * @param request заявка на обмен
     * @return {@code true}, если обмен можно отменить автоматически
     */
    @Transactional(readOnly = true)
    public boolean canCancelExchange(OrderReturnRequest request) {
        if (request == null || request.getStatus() != OrderReturnRequestStatus.EXCHANGE_APPROVED) {
            return false;
        }
        if (isExchangeShipmentDispatched(request)) {
            return false;
        }
        return getExchangeCancellationBlockReason(request).isEmpty();
    }

    /**
     * Проверяет, может ли магазин создать новую обменную посылку по заявке.
     * <p>
     * К созданию допускаются только заявки в статусе обмена без активной посылки
     * или после отмены предыдущего обмена.
     * </p>
     *
     * @param request заявка на обмен
     * @return {@code true}, если обменную посылку можно создать
     */
    @Transactional(readOnly = true)
    public boolean canCreateExchangeParcel(OrderReturnRequest request) {
        if (request == null || request.getStatus() != OrderReturnRequestStatus.EXCHANGE_APPROVED) {
            return false;
        }
        Optional<TrackParcel> latest = orderExchangeService.findLatestExchangeParcel(request);
        if (latest.isEmpty()) {
            return true;
        }
        TrackParcel replacement = latest.get();
        GlobalStatus status = replacement.getStatus();
        if (status == GlobalStatus.REGISTRATION_CANCELLED) {
            return true;
        }
        return false;
    }

    /**
     * Проверяет, доступно ли ручное подтверждение приёма возврата.
     */
    @Transactional(readOnly = true)
    public boolean canConfirmReceipt(OrderReturnRequest request) {
        if (request == null) {
            return false;
        }
        OrderReturnRequestStatus status = request.getStatus();
        return (status == OrderReturnRequestStatus.REGISTERED
                || status == OrderReturnRequestStatus.CLOSED_NO_EXCHANGE)
                && !request.isReturnReceiptConfirmed();
    }

    /**
     * Проверяет, была ли отправлена обменная посылка по заявке.
     * <p>
     * Фиксирует факт отправки, если у посылки появился трек-номер либо она
     * достигла финального статуса доставки. Используется для ограничения
     * действий в интерфейсе Telegram.
     * </p>
     *
     * @param request заявка на обмен
     * @return {@code true}, если обменная посылка отправлена или доставлена
     */
    @Transactional(readOnly = true)
    public boolean isExchangeShipmentDispatched(OrderReturnRequest request) {
        if (request == null || request.getStatus() != OrderReturnRequestStatus.EXCHANGE_APPROVED) {
            return false;
        }
        return orderExchangeService.findLatestExchangeParcel(request)
                .map(this::isParcelDispatched)
                .orElseGet(() -> hasExchangeTrack(request));
    }

    /**
     * Возвращает перечень действий, доступных пользователю для текущей заявки.
     *
     * @param request заявка на возврат/обмен
     * @return множество доступных действий
     */
    @Transactional(readOnly = true)
    public EnumSet<ReturnRequestAction> resolveAvailableActions(OrderReturnRequest request) {
        if (request == null) {
            return EnumSet.noneOf(ReturnRequestAction.class);
        }
        ReturnRequestActionContext context = buildActionContext(request);
        return returnRequestWorkflow.resolveBaseActions(context);
    }

    /**
     * Формирует контекст доступности действий для workflow на основании бизнес-ограничений.
     *
     * @param request заявка на возврат/обмен
     * @return заполненный контекст действий
     */
    private ReturnRequestActionContext buildActionContext(OrderReturnRequest request) {
        ReturnRequestMode mode = Optional.ofNullable(request.getMode()).orElse(ReturnRequestMode.RETURN);
        ReturnRequestStage stage = Optional.ofNullable(request.getStage()).orElse(ReturnRequestStage.NEW);
        ReturnRequestStage normalizedStage = returnRequestWorkflow.adjustStageForMode(mode, stage);
        ReturnRequestActionContext.Builder builder = ReturnRequestActionContext.builder(request)
                .withMode(mode)
                .withStage(normalizedStage)
                .withStatus(request.getStatus());

        builder.allow(ReturnRequestAction.SET_MODE_EXCHANGE, canStartExchange(request));
        builder.allow(ReturnRequestAction.SET_MODE_RETURN, canSwitchToReturnMode(request));
        builder.allow(ReturnRequestAction.CANCEL_EXCHANGE, canCancelExchangeAction(request));
        builder.allow(ReturnRequestAction.REGISTER_EXCHANGE_PARCEL, canRegisterExchangeParcel(request));
        builder.allow(ReturnRequestAction.CLOSE_REQUEST, canCloseRequest(request));
        builder.allow(ReturnRequestAction.UPDATE_REVERSE_TRACK, canUpdateDetails(request));
        builder.allow(ReturnRequestAction.MARK_OUTBOUND_SENT, canMarkOutboundSent(request));
        builder.allow(ReturnRequestAction.MARK_INBOUND_ARRIVED, canMarkInboundArrived(request));
        builder.allow(ReturnRequestAction.MARK_INBOUND_PICKED_UP, canMarkInboundPickedUp(request));
        builder.allow(ReturnRequestAction.MARK_EXCHANGE_SENT, canMarkExchangeSent(request));
        builder.allow(ReturnRequestAction.MARK_EXCHANGE_DELIVERED, canMarkExchangeDelivered(request));

        return builder.build();
    }

    /**
     * Применяет правила перевода заявки в режим обмена.
     */
    private OrderReturnRequest applyExchangeMode(OrderReturnRequest request, User actor) {
        if (request == null) {
            throw new IllegalArgumentException("Не найдена заявка для переключения режима");
        }
        if (request.getStatus() == OrderReturnRequestStatus.EXCHANGE_APPROVED) {
            request.setMode(ReturnRequestMode.EXCHANGE);
            return request;
        }
        if (request.getStatus() != OrderReturnRequestStatus.REGISTERED) {
            throw new IllegalStateException("Перевод в обмен доступен только для активной заявки");
        }
        Long episodeId = Optional.ofNullable(request.getEpisode())
                .map(OrderEpisode::getId)
                .orElse(null);
        if (episodeId != null && returnRequestRepository.existsByEpisode_IdAndStatus(episodeId,
                OrderReturnRequestStatus.EXCHANGE_APPROVED)) {
            throw new IllegalStateException("В эпизоде уже запущен обмен");
        }
        ZonedDateTime decisionMoment = ZonedDateTime.now(ZoneOffset.UTC);
        request.setStatus(OrderReturnRequestStatus.EXCHANGE_APPROVED);
        request.setDecisionBy(actor);
        request.setDecisionAt(decisionMoment);
        request.setClosedBy(null);
        request.setClosedAt(null);
        request.setMode(ReturnRequestMode.EXCHANGE);
        request.setResponsibleManager(actor);
        returnRequestWorkflow.transitionToStage(request, ReturnRequestStage.EXCHANGE_REGISTERED, true, actor, decisionMoment);
        return request;
    }

    /**
     * Применяет правила перевода заявки в режим возврата.
     */
    private OrderReturnRequest applyReturnMode(OrderReturnRequest request,
                                               User actor,
                                               ModeSwitchTrigger trigger) {
        if (request == null) {
            throw new IllegalArgumentException("Не найдена заявка для переключения режима");
        }
        OrderReturnRequestStatus status = request.getStatus();
        if (status == OrderReturnRequestStatus.CLOSED_NO_EXCHANGE) {
            request.setMode(ReturnRequestMode.RETURN);
            return request;
        }
        if (status == OrderReturnRequestStatus.REGISTERED) {
            request.setMode(ReturnRequestMode.RETURN);
            request.setResponsibleManager(actor);
            ZonedDateTime moment = ZonedDateTime.now(ZoneOffset.UTC);
            ReturnRequestStage normalized = returnRequestWorkflow.adjustStageForMode(
                    ReturnRequestMode.RETURN,
                    Optional.ofNullable(request.getStage()).orElse(ReturnRequestStage.NEW)
            );
            returnRequestWorkflow.transitionToStage(request, normalized, true, actor, moment);
            return request;
        }
        if (status != OrderReturnRequestStatus.EXCHANGE_APPROVED) {
            throw new IllegalStateException("Заявка не находится в режиме обмена");
        }
        ensureExchangeCancellationPossible(request, trigger);
        TrackParcel replacement;
        try {
            replacement = orderExchangeService.getLatestExchangeParcelOrThrowIfTracked(request)
                    .orElse(null);
        } catch (IllegalStateException ex) {
            log.warn("Нельзя перевести обмен по заявке {} в возврат: {}", request.getId(), ex.getMessage());
            throw ex;
        }
        ZonedDateTime reopenMoment = ZonedDateTime.now(ZoneOffset.UTC);
        request.setStatus(OrderReturnRequestStatus.REGISTERED);
        request.setDecisionBy(null);
        request.setDecisionAt(null);
        request.setClosedBy(null);
        request.setClosedAt(null);
        request.setExchangeRequested(false);
        request.setMode(ReturnRequestMode.RETURN);
        request.setResponsibleManager(actor);
        request.setExchangeTrackNumber(null);
        request.setExchangeTrackAssignedAt(null);
        ReturnRequestStage normalized = returnRequestWorkflow.adjustStageForMode(
                ReturnRequestMode.RETURN,
                Optional.ofNullable(request.getStage()).orElse(ReturnRequestStage.NEW)
        );
        returnRequestWorkflow.transitionToStage(request, normalized, true, actor, reopenMoment);
        orderExchangeService.cancelExchangeParcel(request, replacement);
        episodeLifecycleService.decrementExchangeCount(request.getEpisode());
        return request;
    }

    /**
     * Убеждается, что отмена обмена разрешена матрицей переходов и состоянием посылок.
     */
    private void ensureExchangeCancellationPossible(OrderReturnRequest request, ModeSwitchTrigger trigger) {
        if (request == null) {
            return;
        }
        log.debug("Проверка возможности отмены обмена по заявке {} (триггер {})",
                request.getId(), trigger);
        if (isExchangeShipmentDispatched(request)) {
            throw new IllegalStateException("Отмена обмена недоступна: обменная посылка уже отправлена или доставлена");
        }
        getExchangeCancellationBlockReason(request).ifPresent(reason -> {
            throw new IllegalStateException(reason);
        });
    }

    /**
     * Определяет, считается ли обменная посылка отправленной.
     *
     * @param parcel обменная посылка
     * @return {@code true}, если у посылки есть трек или финальный статус
     */
    private boolean isParcelDispatched(TrackParcel parcel) {
        if (parcel == null) {
            return false;
        }
        boolean hasTrack = parcel.getNumber() != null && !parcel.getNumber().isBlank();
        GlobalStatus status = parcel.getStatus();
        boolean finalStatus = status != null && status.isFinal();
        return hasTrack || finalStatus;
    }

    /**
     * Проверяет, достигла ли обменная посылка финального статуса доставки.
     */
    private boolean isParcelDelivered(TrackParcel parcel) {
        if (parcel == null) {
            return false;
        }
        GlobalStatus status = parcel.getStatus();
        return status == GlobalStatus.DELIVERED || status == GlobalStatus.RETURNED;
    }

    /**
     * Проверяет, разрешено ли действие для конкретной заявки с учётом всех ограничений.
     */
    /**
     * Проверяет, можно ли закрыть заявку на текущем этапе.
     */
    private boolean canCloseRequest(OrderReturnRequest request) {
        if (request == null) {
            return false;
        }
        OrderReturnRequestStatus status = request.getStatus();
        return status == OrderReturnRequestStatus.REGISTERED;
    }

    /**
     * Проверяет, достаточно ли данных для фиксации отправки возврата пользователем.
     */
    private boolean canMarkOutboundSent(OrderReturnRequest request) {
        if (request == null || request.getStatus() != OrderReturnRequestStatus.REGISTERED) {
            return false;
        }
        if (!hasReverseTrack(request)) {
            return false;
        }
        return canTransitionToStage(request, ReturnRequestStage.OUTBOUND_SENT);
    }

    /**
     * Проверяет, можно ли отметить прибытие возврата в пункт назначения.
     */
    private boolean canMarkInboundArrived(OrderReturnRequest request) {
        if (request == null || request.getStatus() != OrderReturnRequestStatus.REGISTERED) {
            return false;
        }
        if (!hasReverseTrack(request)) {
            return false;
        }
        return canTransitionToStage(request, ReturnRequestStage.INBOUND_ARRIVED);
    }

    /**
     * Проверяет, можно ли подтвердить получение возврата магазином.
     */
    private boolean canMarkInboundPickedUp(OrderReturnRequest request) {
        if (request == null) {
            return false;
        }
        OrderReturnRequestStatus status = request.getStatus();
        if (status != OrderReturnRequestStatus.REGISTERED && status != OrderReturnRequestStatus.EXCHANGE_APPROVED) {
            return false;
        }
        if (!hasReverseTrack(request)) {
            return false;
        }
        return canTransitionToStage(request, ReturnRequestStage.INBOUND_PICKED_UP);
    }

    /**
     * Проверяет, можно ли зарегистрировать обменную посылку вручную.
     */
    private boolean canRegisterExchangeParcel(OrderReturnRequest request) {
        if (request == null || request.getStatus() != OrderReturnRequestStatus.EXCHANGE_APPROVED) {
            return false;
        }
        ReturnRequestStage stage = request.getStage() != null ? request.getStage() : ReturnRequestStage.NEW;
        if (stage != ReturnRequestStage.EXCHANGE_REGISTERED) {
            return false;
        }
        return canCreateExchangeParcel(request);
    }

    /**
     * Проверяет, можно ли отметить отправку обменной посылки.
     */
    private boolean canMarkExchangeSent(OrderReturnRequest request) {
        if (request == null || request.getStatus() != OrderReturnRequestStatus.EXCHANGE_APPROVED) {
            return false;
        }
        if (!canTransitionToStage(request, ReturnRequestStage.EXCHANGE_SENT)) {
            return false;
        }
        return orderExchangeService.findLatestExchangeParcel(request)
                .map(this::isParcelDispatched)
                .orElseGet(() -> hasExchangeTrack(request));
    }

    /**
     * Проверяет, можно ли отметить доставку обменной посылки.
     */
    private boolean canMarkExchangeDelivered(OrderReturnRequest request) {
        if (request == null || request.getStatus() != OrderReturnRequestStatus.EXCHANGE_APPROVED) {
            return false;
        }
        if (!canTransitionToStage(request, ReturnRequestStage.EXCHANGE_DELIVERED)) {
            return false;
        }
        return orderExchangeService.findLatestExchangeParcel(request)
                .map(this::isParcelDelivered)
                .orElseGet(() -> hasExchangeTrack(request));
    }

    /**
     * Проверяет, есть ли у заявки валидный обратный трек.
     */
    private boolean hasReverseTrack(OrderReturnRequest request) {
        String track = request.getReverseTrackNumber();
        return track != null && !track.isBlank();
    }

    /**
     * Проверяет, указан ли трек обменной посылки.
     */
    private boolean hasExchangeTrack(OrderReturnRequest request) {
        String track = request != null ? request.getExchangeTrackNumber() : null;
        return track != null && !track.isBlank();
    }

    /**
     * Проверяет, разрешён ли переход на указанную стадию.
     */
    private boolean canTransitionToStage(OrderReturnRequest request, ReturnRequestStage targetStage) {
        ReturnRequestMode mode = Optional.ofNullable(request.getMode()).orElse(ReturnRequestMode.RETURN);
        ReturnRequestStage currentStage = Optional.ofNullable(request.getStage()).orElse(ReturnRequestStage.NEW);
        ReturnRequestStage normalized = returnRequestWorkflow.adjustStageForMode(mode, currentStage);
        if (normalized == targetStage) {
            return false;
        }
        return returnRequestWorkflow.canTransition(mode, normalized, targetStage);
    }

    /**
     * Проверяет, можно ли редактировать детали обратной отправки для заявки.
     */
    private boolean canUpdateDetails(OrderReturnRequest request) {
        if (request == null) {
            return false;
        }
        return ACTIVE_STATUSES.contains(request.getStatus());
    }

    /**
     * Возвращает идентификаторы посылок пользователя, по которым требуются действия.
     */
    @Transactional(readOnly = true)
    public List<Long> findParcelsRequiringAction(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return returnRequestRepository.findParcelIdsByUserAndStatus(userId, OrderReturnRequestStatus.REGISTERED);
    }

    /**
     * Возвращает активные заявки пользователя вместе с их посылками, магазинами и эпизодами.
     * <p>
     * Метод используется для вкладки «Требуют действия» и загружает связанные сущности
     * одним запросом, чтобы избежать ленивых подгрузок за пределами транзакции.
     * </p>
     *
     * @param userId идентификатор пользователя
     * @return список активных заявок или пустой список, если пользователь не указан
     */
    @Transactional(readOnly = true)
    public List<OrderReturnRequest> findActiveRequestsWithDetails(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return returnRequestRepository.findActiveRequestsWithDetails(userId, ACTIVE_STATUSES);
    }

    /**
     * Возвращает заявку пользователя, гарантируя принадлежность посылки.
     * <p>
     * Загружает связанные сущности (посылку, магазин и ответственного), чтобы контроллер
     * и мапперы могли использовать данные за пределами транзакции без ленивых подгрузок.
     * </p>
     *
     * @param requestId идентификатор заявки
     * @param user      владелец заявки
     * @return найденная заявка
     */
    @Transactional(readOnly = true)
    public OrderReturnRequest getOwnedRequest(Long requestId, User user) {
        if (requestId == null) {
            throw new IllegalArgumentException("Не указан идентификатор заявки");
        }
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Не указан пользователь");
        }
        OrderReturnRequest request = returnRequestRepository.findByIdWithDetails(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Заявка не найдена"));
        ensureOwnership(request, user.getId());
        return request;
    }

    /**
     * Загружает заявку и проверяет, что пользователь владеет посылкой.
     */
    private OrderReturnRequest loadOwnedRequest(Long requestId, Long parcelId, User user) {
        if (requestId == null || parcelId == null) {
            throw new IllegalArgumentException("Идентификаторы заявки и посылки обязательны");
        }
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Не указан пользователь");
        }
        OrderReturnRequest request = returnRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Заявка не найдена"));
        ensureOwnership(request, user.getId());
        if (!parcelId.equals(Optional.ofNullable(request.getParcel()).map(TrackParcel::getId).orElse(null))) {
            throw new IllegalArgumentException("Заявка не относится к указанной посылке");
        }
        return request;
    }

    private void ensureOwnership(OrderReturnRequest request, Long userId) {
        Long ownerId = Optional.ofNullable(request)
                .map(OrderReturnRequest::getParcel)
                .map(TrackParcel::getUser)
                .map(User::getId)
                .orElse(null);
        if (ownerId == null || !ownerId.equals(userId)) {
            throw new AccessDeniedException("Заявка принадлежит другому пользователю");
        }
    }

    /**
     * Очищает кэш деталей трека для посылки изменённой заявки.
     * <p>
     * Вспомогательный метод инкапсулирует построение ключа кэша, чтобы не
     * дублировать логику и соблюдать принцип DRY. Если идентификаторы
     * отсутствуют (например, заявка ещё не привязана к посылке), очистка
     * пропускается.
     * </p>
     *
     * @param request заявка, для которой сохранены изменения
     */
    private void evictTrackDetailsCache(OrderReturnRequest request) {
        TrackParcel parcel = Optional.ofNullable(request)
                .map(OrderReturnRequest::getParcel)
                .orElse(null);
        Long parcelId = Optional.ofNullable(parcel)
                .map(TrackParcel::getId)
                .orElse(null);
        Long userId = Optional.ofNullable(parcel)
                .map(TrackParcel::getUser)
                .map(User::getId)
                .orElse(null);
        if (parcelId != null && userId != null) {
            trackViewCacheInvalidator.evictTrackDetails(userId, parcelId);
        }
    }

    /**
     * Устанавливает отметку о фактическом приёме возврата.
     * <p>
     * Приватный хелпер гарантирует, что фиксация момента обработки собрана в одном месте,
     * что облегчает расширение бизнес-правил и соответствует принципу DRY.
     * </p>
     */
    private void markReturnProcessingConfirmed(OrderReturnRequest request,
                                               User actor,
                                               ZonedDateTime stageMoment) {
        if (request == null || request.isReturnReceiptConfirmed()) {
            return;
        }
        ZonedDateTime normalizedMoment = normalizeStageMoment(stageMoment);
        request.setReturnReceiptConfirmed(true);
        request.setReturnReceiptConfirmedAt(normalizedMoment);
        request.setResponsibleManager(actor);
        returnRequestWorkflow.transitionToStage(request, ReturnRequestStage.INBOUND_PICKED_UP, true, actor, normalizedMoment);
    }

    /**
     * Нормализует причину возврата и валидирует длину строки.
     */
    private String normalizeReason(String reason) {
        if (reason == null) {
            throw new IllegalArgumentException("Не указана причина возврата");
        }
        String normalized = reason.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Не указана причина возврата");
        }
        if (normalized.length() > 255) {
            throw new IllegalArgumentException("Причина возврата не должна превышать 255 символов");
        }
        return normalized;
    }

    /**
     * Подготавливает комментарий пользователя к сохранению.
     */
    private String normalizeComment(String comment) {
        if (comment == null) {
            return null;
        }
        String normalized = comment.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > 2000) {
            throw new IllegalArgumentException("Комментарий не должен превышать 2000 символов");
        }
        return normalized;
    }

    /**
     * Переводит момент запроса в UTC и проверяет, что дата не из будущего.
     */
    private ZonedDateTime normalizeRequestedAt(ZonedDateTime requestedAt) {
        ZonedDateTime utc = requestedAt != null
                ? requestedAt.withZoneSameInstant(ZoneOffset.UTC)
                : ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(1);
        if (utc.isAfter(now)) {
            throw new IllegalArgumentException("Дата запроса возврата не может быть в будущем");
        }
        return utc;
    }

    /**
     * Валидирует и нормализует трек обратной отправки.
     */
    private String normalizeReverseTrackNumber(String reverseTrackNumber) {
        if (reverseTrackNumber == null) {
            return null;
        }
        String normalized = reverseTrackNumber.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > 64) {
            throw new IllegalArgumentException("Трек обратной отправки не должен превышать 64 символа");
        }
        return normalized.toUpperCase();
    }

    /**
     * Присваивает заявки трек обменной посылки и отмечает ответственного менеджера.
     */
    private void assignExchangeTrack(OrderReturnRequest request,
                                     String exchangeTrack,
                                     ZonedDateTime assignedAt,
                                     User actor) {
        if (request == null) {
            return;
        }
        request.setExchangeTrackNumber(exchangeTrack);
        request.setExchangeTrackAssignedAt(assignedAt);
        request.setResponsibleManager(actor);
    }

    /**
     * Валидирует и нормализует трек обменной посылки.
     */
    private String normalizeExchangeTrackNumber(String exchangeTrackNumber) {
        if (exchangeTrackNumber == null) {
            throw new IllegalArgumentException("Не указан трек обменной посылки");
        }
        String normalized = exchangeTrackNumber.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Не указан трек обменной посылки");
        }
        if (normalized.length() > 64) {
            throw new IllegalArgumentException("Трек обменной посылки не должен превышать 64 символа");
        }
        return normalized.toUpperCase();
    }

    /**
     * Нормализует момент фиксации стадии и ограничивает значения будущим временем.
     */
    private ZonedDateTime normalizeStageMoment(ZonedDateTime stageMoment) {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        if (stageMoment == null) {
            return now;
        }
        ZonedDateTime utc = stageMoment.withZoneSameInstant(ZoneOffset.UTC);
        if (utc.isAfter(now.plusMinutes(1))) {
            throw new IllegalArgumentException("Момент фиксации стадии не может быть в будущем");
        }
        return utc;
    }
}

