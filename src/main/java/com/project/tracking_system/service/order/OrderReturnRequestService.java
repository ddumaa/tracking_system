package com.project.tracking_system.service.order;

import com.project.tracking_system.dto.ReturnRequestUpdateResponse;
import com.project.tracking_system.exception.ActionNotAllowedException;
import com.project.tracking_system.exception.IdempotencyConflictException;
import com.project.tracking_system.exception.ValidationException;
import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.OrderEpisode;
import com.project.tracking_system.entity.OrderReturnRequest;
import com.project.tracking_system.entity.OrderReturnRequestActionRequest;
import com.project.tracking_system.entity.OrderReturnRequestHistoryEntry;
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
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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
    public ReturnRequestUpdateResponse updateReverseTrack(Long requestId,
                                                          Long parcelId,
                                                          User user,
                                                          String reverseTrack,
                                                          String comment) {
        OrderReturnRequest request = loadOwnedRequest(requestId, parcelId, user);
        if (!ACTIVE_STATUSES.contains(request.getStatus())) {
            throw new ActionNotAllowedException("Заявку нельзя изменить в текущем статусе");
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
            throw new ValidationException("Не указан идентификатор посылки");
        }
        if (user == null || user.getId() == null) {
            throw new ValidationException("Не указан пользователь");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ValidationException("Не указан идемпотентный ключ заявки");
        }

        String normalizedReason = normalizeReason(reason);
        String normalizedComment = normalizeComment(comment);
        ZonedDateTime normalizedRequestedAt = normalizeRequestedAt(requestedAt);
        String normalizedReverse = normalizeReverseTrackNumber(reverseTrack);

        String canonicalKey = canonicalizeIdempotencyKey(idempotencyKey);
        if (canonicalKey == null) {
            throw new ValidationException("Некорректный идемпотентный ключ заявки");
        }
        Optional<OrderReturnRequest> existingByKey = findByIdempotencyKey(canonicalKey);
        if (existingByKey.isPresent()) {
            OrderReturnRequest existing = existingByKey.get();
            ensureOwnership(existing, user.getId());
            if (!Objects.equals(existing.getReason(), normalizedReason)
                    || !Objects.equals(existing.getComment(), normalizedComment)
                    || !Objects.equals(existing.getRequestedAt(), normalizedRequestedAt)
                    || !Objects.equals(existing.getReverseTrackNumber(), normalizedReverse)
                    || existing.isExchangeRequested() != exchangeRequested) {
                throw new IdempotencyConflictException("Заявка с таким ключом уже зарегистрирована с другими данными");
            }
            return existing;
        }

        TrackParcel parcel = trackParcelService.findOwnedById(parcelId, user.getId())
                .orElseThrow(() -> new AccessDeniedException("Посылка не принадлежит пользователю"));

        if (parcel.getStatus() != GlobalStatus.DELIVERED) {
            throw new ActionNotAllowedException("Заявка на возврат доступна только для статуса \"Вручена\"");
        }

        Optional<OrderReturnRequest> active = returnRequestRepository
                .findFirstByParcel_IdAndStatusIn(parcelId, ACTIVE_STATUSES);
        if (active.isPresent()) {
            log.debug("По посылке {} уже есть активная заявка {}", parcelId, active.get().getId());
            throw new ActionNotAllowedException("У посылки уже есть активная заявка на возврат");
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
        request.setIdempotencyKey(canonicalKey);
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
     * Переводит заявку в режим обмена.
     * <p>
     * Метод фиксирует решение менеджера, подготавливает заявку к созданию обменной посылки
     * и оставляет подготовку отправления за отдельным действием {@link #createExchangeParcel(Long, Long, User)},
     * соблюдая принцип единой ответственности.
     * </p>
     *
     * @param requestKey UUID идентификатор заявки
     * @param parcelId  идентификатор посылки
     * @param user      автор решения
     * @return обновлённая заявка после переключения режима
     */
    @Transactional
    public OrderReturnRequest setModeExchange(Long requestId, Long parcelId, User user) {
        return setModeExchange(requestId, parcelId, user, ModeSwitchTrigger.MANUAL_DECISION);
    }

    /**
     * Переводит заявку в режим обмена с указанием причины переключения.
     */
    @Transactional
    public OrderReturnRequest setModeExchange(Long requestId,
                                              Long parcelId,
                                              User user,
                                              ModeSwitchTrigger trigger) {
        return switchModeInternal(requestId, parcelId, user, ReturnRequestMode.EXCHANGE, trigger);
    }

    /**
     * Переключает режим обработки заявки на возврат или обмен.
     * <p>
     * Метод реализует единый шаблон перехода между режимами: проверяет допустимость операции
     * согласно матрице состояний, нормализует стадию через {@link ReturnRequestWorkflow}
     * и выполняет побочные действия с обменными посылками через профильные сервисы.
     * </p>
     */
    private OrderReturnRequest switchModeInternal(Long requestId,
                                                  Long parcelId,
                                                  User user,
                                                  ReturnRequestMode targetMode,
                                                  ModeSwitchTrigger trigger) {
        if (targetMode == null) {
            throw new ValidationException("Не указан целевой режим заявки");
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
     * Возвращает заявку в классический режим возврата.
     * <p>
     * Используется при отмене обмена или когда менеджер решил продолжать сценарий возврата.
     * </p>
     */
    @Transactional
    public OrderReturnRequest setModeReturn(Long requestId,
                                            Long parcelId,
                                            User user) {
        return setModeReturn(requestId, parcelId, user, ModeSwitchTrigger.MANUAL_DECISION);
    }

    /**
     * Возвращает заявку в режим возврата с кастомным триггером (например, запрос клиента).
     */
    @Transactional
    public OrderReturnRequest setModeReturn(Long requestId,
                                            Long parcelId,
                                            User user,
                                            ModeSwitchTrigger trigger) {
        return switchModeInternal(requestId, parcelId, user, ReturnRequestMode.RETURN, trigger);
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
            throw new ActionNotAllowedException("Обменная посылка доступна только после одобрения обмена");
        }
        if (!canCreateExchangeParcel(request)) {
            throw new ActionNotAllowedException("Обменная посылка уже создана или находится в работе");
        }
        TrackParcel replacement = orderExchangeService.createExchangeParcel(request);
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime assignedMoment = Optional.ofNullable(replacement)
                .map(TrackParcel::getTimestamp)
                .orElse(now);
        request.setResponsibleManager(user);
        request.setExchangeTrackNumber(Optional.ofNullable(replacement).map(TrackParcel::getNumber).orElse(null));
        request.setExchangeTrackAssignedAt(assignedMoment);
        returnRequestWorkflow.transitionSequentially(request, ReturnRequestStage.EXCHANGE_SENT, true, user, assignedMoment);
        OrderReturnRequest saved = returnRequestRepository.save(request);
        evictTrackDetailsCache(saved);
        log.info("Создана обменная посылка {} для заявки {}",
                Optional.ofNullable(replacement).map(TrackParcel::getId).orElse(null),
                saved.getId());
        return replacement;
    }

    /**
     * Закрывает заявку после завершения обработки возврата без запуска обмена.
     */
    @Transactional
    public OrderReturnRequest closeRequest(Long requestId, Long parcelId, User user) {
        OrderReturnRequest request = loadOwnedRequest(requestId, parcelId, user);

        if (request.getStatus() != OrderReturnRequestStatus.REGISTERED) {
            throw new ActionNotAllowedException("Заявка уже обработана");
        }

        ZonedDateTime closeMoment = ZonedDateTime.now(ZoneOffset.UTC);
        request.setStatus(OrderReturnRequestStatus.CLOSED_NO_EXCHANGE);
        request.setClosedBy(user);
        request.setClosedAt(closeMoment);
        request.setMode(ReturnRequestMode.RETURN);
        request.setResponsibleManager(user);
        returnRequestWorkflow.transitionSequentially(request, ReturnRequestStage.INBOUND_PICKED_UP, true, user, closeMoment);

        OrderReturnRequest saved = returnRequestRepository.save(request);
        evictTrackDetailsCache(saved);
        log.info("Заявка {} закрыта", saved.getId());
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
     * @param requestKey UUID идентификатор заявки
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
            throw new ActionNotAllowedException("Подтверждение доступно только для активной заявки или закрытия без обмена");
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
            throw new ActionNotAllowedException("Стадия отправки возврата недоступна для текущего состояния заявки");
        }
        ZonedDateTime normalizedMoment = normalizeStageMoment(stageMoment);
        request.setResponsibleManager(user);
        returnRequestWorkflow.transitionSequentially(request, ReturnRequestStage.OUTBOUND_SENT, true, user, normalizedMoment);
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
            throw new ActionNotAllowedException("Стадия прибытия возврата недоступна для текущего состояния заявки");
        }
        ZonedDateTime normalizedMoment = normalizeStageMoment(stageMoment);
        request.setResponsibleManager(user);
        returnRequestWorkflow.transitionSequentially(request, ReturnRequestStage.INBOUND_ARRIVED, true, user, normalizedMoment);
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
            throw new ActionNotAllowedException("Стадия приёма возврата недоступна для текущего состояния заявки");
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
            throw new ActionNotAllowedException("Ручная регистрация доступна только для одобренного обмена");
        }
        if (!canRegisterExchangeParcel(request)) {
            throw new ActionNotAllowedException("Обменная посылка уже зарегистрирована или ожидает обработки");
        }
        String normalizedTrack = normalizeExchangeTrackNumber(exchangeTrack);
        ZonedDateTime normalizedMoment = normalizeStageMoment(stageMoment);
        assignExchangeTrack(request, normalizedTrack, normalizedMoment, user);
        returnRequestWorkflow.transitionSequentially(request, ReturnRequestStage.EXCHANGE_REGISTERED, true, user, normalizedMoment);
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
            throw new ActionNotAllowedException("Стадия отправки обмена недоступна для текущего состояния заявки");
        }
        if (!canTransitionToStage(request, ReturnRequestStage.EXCHANGE_SENT)) {
            throw new ActionNotAllowedException("Переход на стадию отправки обмена запрещён текущими правилами");
        }
        String normalizedTrack = normalizeExchangeTrackNumber(exchangeTrack);
        ZonedDateTime normalizedMoment = normalizeStageMoment(stageMoment);
        assignExchangeTrack(request, normalizedTrack, normalizedMoment, user);
        returnRequestWorkflow.transitionSequentially(request, ReturnRequestStage.EXCHANGE_SENT, true, user, normalizedMoment);
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
            throw new ActionNotAllowedException("Стадия доставки обмена недоступна для текущего состояния заявки");
        }
        ZonedDateTime normalizedMoment = normalizeStageMoment(stageMoment);
        request.setResponsibleManager(user);
        returnRequestWorkflow.transitionSequentially(request, ReturnRequestStage.EXCHANGE_DELIVERED, true, user, normalizedMoment);
        OrderReturnRequest saved = returnRequestRepository.save(request);
        evictTrackDetailsCache(saved);
        log.info("Стадия EXCHANGE_DELIVERED зафиксирована вручную для заявки {}", saved.getId());
        return saved;
    }

    /**
     * Автоматически фиксирует прибытие возврата по данным трекинга.
     * <p>
     * Метод идемпотентен: повторные события для одной и той же стадии только
     * логируются и не вызывают повторного сохранения сущности. Это позволяет
     * безопасно обрабатывать шум в источнике статусов и соответствует
     * требованиям к интеграции с автоматическими обновлениями.
     * </p>
     *
     * @param parcelId    идентификатор исходной посылки
     * @param stageMoment момент события из системы трекинга
     * @return обновлённая заявка или пустой результат, если переход недоступен
     */
    @Transactional
    public Optional<OrderReturnRequest> autoMarkInboundArrived(Long parcelId, ZonedDateTime stageMoment) {
        Optional<OrderReturnRequest> requestOpt = findCurrentForParcel(parcelId);
        if (requestOpt.isEmpty()) {
            return Optional.empty();
        }
        OrderReturnRequest request = requestOpt.get();
        ReturnRequestStage targetStage = ReturnRequestStage.INBOUND_ARRIVED;
        if (hasReachedStage(request, targetStage)) {
            log.debug("Повторное событие прибытия возврата для заявки {} пропущено", request.getId());
            return Optional.empty();
        }
        if (!canMarkInboundArrived(request)) {
            log.debug("Автоматическая отметка INBOUND_ARRIVED отклонена для заявки {} со статусом {}",
                    request.getId(), request.getStatus());
            return Optional.empty();
        }
        ZonedDateTime normalizedMoment = normalizeStageMoment(stageMoment);
        returnRequestWorkflow.transitionSequentially(request, targetStage, false, null, normalizedMoment);
        OrderReturnRequest saved = returnRequestRepository.save(request);
        evictTrackDetailsCache(saved);
        log.info("Стадия INBOUND_ARRIVED зафиксирована автоматически для заявки {}", saved.getId());
        return Optional.of(saved);
    }

    /**
     * Автоматически подтверждает получение возврата магазином.
     * <p>
     * Использует те же проверки, что и ручная команда, и предотвращает повторную
     * фиксацию при приходе идентичных событий трекинга.
     * </p>
     *
     * @param parcelId    идентификатор исходной посылки
     * @param stageMoment момент события трекинга
     * @return обновлённая заявка или пустой результат, если переход невозможен
     */
    @Transactional
    public Optional<OrderReturnRequest> autoMarkInboundPickedUp(Long parcelId, ZonedDateTime stageMoment) {
        Optional<OrderReturnRequest> requestOpt = findCurrentForParcel(parcelId);
        if (requestOpt.isEmpty()) {
            return Optional.empty();
        }
        OrderReturnRequest request = requestOpt.get();
        ReturnRequestStage targetStage = ReturnRequestStage.INBOUND_PICKED_UP;
        if (hasReachedStage(request, targetStage)) {
            log.debug("Повторное подтверждение приёма возврата для заявки {} пропущено", request.getId());
            return Optional.empty();
        }
        if (!canMarkInboundPickedUp(request)) {
            log.debug("Автоматическое подтверждение INBOUND_PICKED_UP отклонено для заявки {}", request.getId());
            return Optional.empty();
        }
        confirmReturnProcessing(request, null, stageMoment, false);
        OrderReturnRequest saved = returnRequestRepository.save(request);
        evictTrackDetailsCache(saved);
        log.info("Стадия INBOUND_PICKED_UP зафиксирована автоматически для заявки {}", saved.getId());
        return Optional.of(saved);
    }

    /**
     * Автоматически отмечает доставку обменной посылки покупателю.
     * <p>
     * Метод проверяет, что обмен действительно запущен и подтверждён трекингом,
     * после чего продвигает стадию вперёд без участия пользователя.
     * </p>
     *
     * @param parcelId    идентификатор исходной посылки, инициировавшей обмен
     * @param stageMoment момент доставки обменной посылки
     * @return обновлённая заявка или пустой результат при отсутствии перехода
     */
    @Transactional
    public Optional<OrderReturnRequest> autoMarkExchangeDelivered(Long parcelId, ZonedDateTime stageMoment) {
        Optional<OrderReturnRequest> requestOpt = findCurrentForParcel(parcelId);
        if (requestOpt.isEmpty()) {
            return Optional.empty();
        }
        OrderReturnRequest request = requestOpt.get();
        ReturnRequestStage targetStage = ReturnRequestStage.EXCHANGE_DELIVERED;
        if (hasReachedStage(request, targetStage)) {
            log.debug("Повторное событие доставки обмена для заявки {} пропущено", request.getId());
            return Optional.empty();
        }
        if (!canMarkExchangeDelivered(request)) {
            log.debug("Автоматическая отметка EXCHANGE_DELIVERED отклонена для заявки {}", request.getId());
            return Optional.empty();
        }
        ZonedDateTime normalizedMoment = normalizeStageMoment(stageMoment);
        returnRequestWorkflow.transitionSequentially(request, targetStage, false, null, normalizedMoment);
        OrderReturnRequest saved = returnRequestRepository.save(request);
        evictTrackDetailsCache(saved);
        log.info("Стадия EXCHANGE_DELIVERED зафиксирована автоматически для заявки {}", saved.getId());
        return Optional.of(saved);
    }

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
            throw new ValidationException("Не указан тип запроса к магазину");
        }
        if (customer == null || customer.getId() == null) {
            throw new ValidationException("Не указан покупатель");
        }
        OrderReturnRequest request = loadOwnedRequest(requestId, parcelId, user);
        if (request.getStatus() != OrderReturnRequestStatus.EXCHANGE_APPROVED) {
            throw new ActionNotAllowedException("Запрос возможен только для обменных заявок");
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
     * <p>
     * Метод полагается на статус и стадию обработки, чтобы не блокировать заявки,
     * зарегистрированные с флагом обмена, но ещё не одобренные менеджером.
     * Благодаря этому заявки с режимом {@link ReturnRequestMode#EXCHANGE},
     * созданные покупателем, остаются доступны для перехода в стадию обмена.
     * </p>
     */
    @Transactional(readOnly = true)
    public boolean canSetModeExchange(OrderReturnRequest request) {
        if (request == null || request.getStatus() != OrderReturnRequestStatus.REGISTERED) {
            return false;
        }
        ReturnRequestStage normalizedStage = returnRequestWorkflow
                .adjustStageForMode(ReturnRequestMode.EXCHANGE, resolveStage(request));
        if (!returnRequestWorkflow.canReachStage(ReturnRequestMode.EXCHANGE, normalizedStage,
                ReturnRequestStage.EXCHANGE_REGISTERED)) {
            return false;
        }
        OrderEpisode episode = request.getEpisode();
        Long episodeId = episode != null ? episode.getId() : null;
        if (episodeId == null) {
            return true;
        }
        return !returnRequestRepository.existsByEpisode_IdAndStatus(episodeId,
                OrderReturnRequestStatus.EXCHANGE_APPROVED);
    }

    /**
     * Проверяет, можно ли вернуть обменную заявку в режим возврата без закрытия обращения.
     */
    private boolean canSetModeReturn(OrderReturnRequest request) {
        if (request == null) {
            return false;
        }
        ReturnRequestMode mode = resolveMode(request);
        if (mode != ReturnRequestMode.EXCHANGE) {
            return false;
        }
        if (isExchangeShipmentDispatched(request)) {
            return false;
        }
        return getExchangeCancellationBlockReason(request).isEmpty();
    }

    /**
     * Возвращает причину недоступности отмены обмена, если магазин уже указал трек.
     *
     * @param request заявка на обмен
     * @return текстовое сообщение или пустое значение, если ограничений нет
     */
    @Transactional(readOnly = true)
    public Optional<String> getExchangeCancellationBlockReason(OrderReturnRequest request) {
        if (request == null) {
            return Optional.empty();
        }
        if (resolveMode(request) != ReturnRequestMode.EXCHANGE) {
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
        if (request == null) {
            return false;
        }
        ReturnRequestMode mode = resolveMode(request);
        if (mode != ReturnRequestMode.EXCHANGE) {
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
        if (request == null) {
            return false;
        }
        ReturnRequestMode mode = resolveMode(request);
        if (mode != ReturnRequestMode.EXCHANGE) {
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
        if (request == null) {
            return false;
        }
        ReturnRequestMode mode = resolveMode(request);
        if (mode != ReturnRequestMode.EXCHANGE) {
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
        if (request == null || request.isReturnReceiptConfirmed()) {
            return false;
        }
        OrderReturnRequestStatus status = request.getStatus();
        if (status != OrderReturnRequestStatus.REGISTERED
                && status != OrderReturnRequestStatus.CLOSED_NO_EXCHANGE) {
            return false;
        }
        ReturnRequestMode mode = resolveMode(request);
        if (mode != ReturnRequestMode.RETURN) {
            return false;
        }
        ReturnRequestStage normalizedStage = returnRequestWorkflow
                .adjustStageForMode(mode, resolveStage(request));
        return normalizedStage == ReturnRequestStage.OUTBOUND_SENT
                || normalizedStage == ReturnRequestStage.INBOUND_ARRIVED
                || normalizedStage == ReturnRequestStage.INBOUND_PICKED_UP;
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
        if (request == null) {
            return false;
        }
        ReturnRequestMode mode = resolveMode(request);
        if (mode != ReturnRequestMode.EXCHANGE) {
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
        ReturnRequestMode mode = resolveMode(request);
        ReturnRequestStage normalizedStage = returnRequestWorkflow.adjustStageForMode(mode, resolveStage(request));
        ReturnRequestActionContext.Builder builder = ReturnRequestActionContext.builder(request)
                .withMode(mode)
                .withStage(normalizedStage)
                .withStatus(request.getStatus());

        for (ReturnRequestAction action : ReturnRequestAction.values()) {
            builder.allow(action, isActionAllowed(request, action));
        }

        return builder.build();
    }

    /**
     * Проверяет доступность конкретного действия для заявки с учётом бизнес-ограничений.
     */
    private boolean isActionAllowed(OrderReturnRequest request, ReturnRequestAction action) {
        if (request == null || action == null) {
            return false;
        }
        return switch (action) {
            case SET_MODE_EXCHANGE -> canSetModeExchange(request);
            case SET_MODE_RETURN -> canSetModeReturn(request);
            case REGISTER_EXCHANGE_PARCEL -> canRegisterExchangeParcel(request);
            case CLOSE_REQUEST -> canCloseRequest(request);
            case UPDATE_REVERSE_TRACK -> canUpdateReverseTrack(request);
            case MARK_OUTBOUND_SENT -> canMarkOutboundSent(request);
            case MARK_INBOUND_ARRIVED -> canMarkInboundArrived(request);
            case MARK_INBOUND_PICKED_UP -> canMarkInboundPickedUp(request);
            case MARK_EXCHANGE_SENT -> canMarkExchangeSent(request);
            case MARK_EXCHANGE_DELIVERED -> canMarkExchangeDelivered(request);
            default -> false;
        };
    }

    /**
     * Применяет правила перевода заявки в режим обмена.
     */
    private OrderReturnRequest applyExchangeMode(OrderReturnRequest request, User actor) {
        if (request == null) {
            throw new EntityNotFoundException("Не найдена заявка для переключения режима");
        }
        if (request.getStatus() == OrderReturnRequestStatus.EXCHANGE_APPROVED) {
            request.setMode(ReturnRequestMode.EXCHANGE);
            return request;
        }
        if (request.getStatus() != OrderReturnRequestStatus.REGISTERED) {
            throw new ActionNotAllowedException("Перевод в обмен доступен только для активной заявки");
        }
        Long episodeId = Optional.ofNullable(request.getEpisode())
                .map(OrderEpisode::getId)
                .orElse(null);
        if (episodeId != null && returnRequestRepository.existsByEpisode_IdAndStatus(episodeId,
                OrderReturnRequestStatus.EXCHANGE_APPROVED)) {
            throw new ActionNotAllowedException("В эпизоде уже запущен обмен");
        }
        ZonedDateTime decisionMoment = ZonedDateTime.now(ZoneOffset.UTC);
        request.setStatus(OrderReturnRequestStatus.EXCHANGE_APPROVED);
        request.setDecisionBy(actor);
        request.setDecisionAt(decisionMoment);
        request.setClosedBy(null);
        request.setClosedAt(null);
        request.setMode(ReturnRequestMode.EXCHANGE);
        request.setResponsibleManager(actor);
        returnRequestWorkflow.transitionSequentially(request, ReturnRequestStage.EXCHANGE_REGISTERED, true, actor, decisionMoment);
        return request;
    }

    /**
     * Применяет правила перевода заявки в режим возврата.
     */
    private OrderReturnRequest applyReturnMode(OrderReturnRequest request,
                                               User actor,
                                               ModeSwitchTrigger trigger) {
        if (request == null) {
            throw new EntityNotFoundException("Не найдена заявка для переключения режима");
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
            ReturnRequestStage targetStage = enforceReturnHistoryFloor(request, normalized);
            returnRequestWorkflow.transitionSequentially(request, targetStage, true, actor, moment);
            return request;
        }
        if (status != OrderReturnRequestStatus.EXCHANGE_APPROVED) {
            throw new ActionNotAllowedException("Заявка не находится в режиме обмена");
        }
        ensureExchangeCancellationPossible(request, trigger);
        TrackParcel replacement;
        try {
            replacement = orderExchangeService.getLatestExchangeParcelOrThrowIfTracked(request)
                    .orElse(null);
        } catch (IllegalStateException ex) {
            log.warn("Нельзя перевести обмен по заявке {} в возврат: {}", request.getId(), ex.getMessage());
            throw new ActionNotAllowedException(ex.getMessage(), ex);
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
        ReturnRequestStage targetStage = returnRequestWorkflow.adjustStageForMode(
                ReturnRequestMode.RETURN,
                Optional.ofNullable(request.getStage()).orElse(ReturnRequestStage.NEW)
        );
        targetStage = enforceReturnHistoryFloor(request, targetStage);
        returnRequestWorkflow.transitionSequentially(request, targetStage, true, actor, reopenMoment);
        orderExchangeService.cancelExchangeParcel(request, replacement);
        episodeLifecycleService.decrementExchangeCount(request.getEpisode());
        return request;
    }

    /**
     * Определяет минимальный допустимый этап для возврата, опираясь на историю.
     * <p>
     * Правило {@code doNotLowerBelowKnownHistory} запрещает понижать этап ниже тех,
     * которые уже подтверждены в режиме возврата. Метод находит максимальный этап,
     * достигнутый в истории при режиме {@link ReturnRequestMode#RETURN}, и гарантирует,
     * что целевой этап не опустится ниже него.
     * </p>
     *
     * @param request  заявка, историю которой анализируем
     * @param candidateStage стадия, выбранная по текущим правилам переключения
     * @return стадия, скорректированная с учётом истории
     */
    private ReturnRequestStage enforceReturnHistoryFloor(OrderReturnRequest request,
                                                         ReturnRequestStage candidateStage) {
        ReturnRequestStage effectiveCandidate = Optional.ofNullable(candidateStage)
                .orElse(returnRequestWorkflow.initialStage(ReturnRequestMode.RETURN));
        ReturnRequestStage historyStage = resolveHighestReturnStage(request);
        boolean candidateBeforeHistory = returnRequestWorkflow.canReachStage(
                ReturnRequestMode.RETURN,
                effectiveCandidate,
                historyStage
        ) && !Objects.equals(effectiveCandidate, historyStage);
        if (candidateBeforeHistory) {
            return historyStage;
        }
        return effectiveCandidate;
    }

    /**
     * Находит максимальный этап, достигнутый заявкой в режиме возврата.
     * <p>
     * Метод последовательно проходит историю и нормализует этапы через
     * {@link ReturnRequestWorkflow#adjustStageForMode(ReturnRequestMode, ReturnRequestStage)}.
     * Для сравнения используется порядок этапов, определённый матрицей переходов:
     * если история уже содержит более поздний этап, он возвращается как опорный.
     * </p>
     *
     * @param request заявка с накопленной историей
     * @return максимальный этап возврата из истории или стартовый этап, если записей нет
     */
    private ReturnRequestStage resolveHighestReturnStage(OrderReturnRequest request) {
        ReturnRequestStage floor = returnRequestWorkflow.initialStage(ReturnRequestMode.RETURN);
        if (request == null) {
            return floor;
        }
        for (OrderReturnRequestHistoryEntry entry : request.getHistoryEntries()) {
            if (entry == null || entry.getMode() != ReturnRequestMode.RETURN) {
                continue;
            }
            ReturnRequestStage historyStage = returnRequestWorkflow.adjustStageForMode(
                    ReturnRequestMode.RETURN,
                    entry.getStage()
            );
            boolean historyAfterFloor = returnRequestWorkflow.canReachStage(
                    ReturnRequestMode.RETURN,
                    floor,
                    historyStage
            ) && !Objects.equals(floor, historyStage);
            if (historyAfterFloor) {
                floor = historyStage;
            }
        }
        return floor;
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
            throw new ActionNotAllowedException("Отмена обмена недоступна: обменная посылка уже отправлена или доставлена");
        }
        getExchangeCancellationBlockReason(request).ifPresent(reason -> {
            throw new ActionNotAllowedException(reason);
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
        if (request == null) {
            return false;
        }
        if (request.getStatus() != OrderReturnRequestStatus.REGISTERED) {
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
        if (request == null) {
            return false;
        }
        if (request.getStatus() != OrderReturnRequestStatus.REGISTERED) {
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
        if (!ACTIVE_STATUSES.contains(status)) {
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
        if (request == null) {
            return false;
        }
        if (resolveMode(request) != ReturnRequestMode.EXCHANGE) {
            return false;
        }
        ReturnRequestStage stage = resolveStage(request);
        if (returnRequestWorkflow.adjustStageForMode(ReturnRequestMode.EXCHANGE, stage)
                != ReturnRequestStage.EXCHANGE_REGISTERED) {
            return false;
        }
        return canCreateExchangeParcel(request);
    }

    /**
     * Проверяет, можно ли отметить отправку обменной посылки.
     */
    private boolean canMarkExchangeSent(OrderReturnRequest request) {
        if (request == null) {
            return false;
        }
        if (resolveMode(request) != ReturnRequestMode.EXCHANGE) {
            return false;
        }
        if (!canTransitionToStage(request, ReturnRequestStage.EXCHANGE_SENT)) {
            return false;
        }
        if (hasExchangeTrack(request)) {
            return true;
        }
        return orderExchangeService.findLatestExchangeParcel(request).isPresent();
    }

    /**
     * Проверяет, можно ли отметить доставку обменной посылки, подтверждая финальный статус в трекинге.
     */
    private boolean canMarkExchangeDelivered(OrderReturnRequest request) {
        if (request == null) {
            return false;
        }
        if (resolveMode(request) != ReturnRequestMode.EXCHANGE) {
            return false;
        }
        if (!canTransitionToStage(request, ReturnRequestStage.EXCHANGE_DELIVERED)) {
            return false;
        }
        return orderExchangeService.findLatestExchangeParcel(request)
                .map(this::isParcelDelivered)
                .orElse(false);
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
     * Проверяет, достигла ли заявка указанной стадии или продвинулась дальше.
     */
    private boolean hasReachedStage(OrderReturnRequest request, ReturnRequestStage targetStage) {
        if (request == null || targetStage == null) {
            return false;
        }
        ReturnRequestMode mode = resolveMode(request);
        ReturnRequestStage normalized = returnRequestWorkflow.adjustStageForMode(mode, resolveStage(request));
        return returnRequestWorkflow.canReachStage(mode, targetStage, normalized);
    }

    /**
     * Проверяет, разрешён ли переход на указанную стадию.
     */
    private boolean canTransitionToStage(OrderReturnRequest request, ReturnRequestStage targetStage) {
        ReturnRequestMode mode = resolveMode(request);
        ReturnRequestStage normalized = returnRequestWorkflow.adjustStageForMode(mode, resolveStage(request));
        if (normalized == targetStage) {
            return false;
        }
        return returnRequestWorkflow.canTransition(mode, normalized, targetStage);
    }

    /**
     * Проверяет, можно ли редактировать обратный трек заявки согласно матрице переходов.
     */
    private boolean canUpdateReverseTrack(OrderReturnRequest request) {
        if (request == null) {
            return false;
        }
        if (!ACTIVE_STATUSES.contains(request.getStatus())) {
            return false;
        }
        ReturnRequestMode mode = resolveMode(request);
        ReturnRequestStage normalized = returnRequestWorkflow.adjustStageForMode(mode, resolveStage(request));
        if (mode == ReturnRequestMode.RETURN) {
            return normalized != ReturnRequestStage.INBOUND_PICKED_UP;
        }
        if (mode == ReturnRequestMode.EXCHANGE) {
            return normalized != ReturnRequestStage.EXCHANGE_DELIVERED;
        }
        return false;
    }

    /**
     * Возвращает режим заявки, подставляя возврат по умолчанию.
     */
    private ReturnRequestMode resolveMode(OrderReturnRequest request) {
        return Optional.ofNullable(request.getMode()).orElse(ReturnRequestMode.RETURN);
    }

    /**
     * Возвращает актуальную стадию с безопасным значением по умолчанию.
     */
    private ReturnRequestStage resolveStage(OrderReturnRequest request) {
        return Optional.ofNullable(request.getStage()).orElse(ReturnRequestStage.NEW);
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
     * Ищет заявку по строковому идемпотентному ключу с нормализацией ввода.
     * <p>
     * Метод обрабатывает пустые значения и удаляет лишние пробелы, чтобы
     * клиенты могли передавать ключ без учёта регистра и форматирования.
     * </p>
     *
     * @param idempotencyKey исходный ключ
     * @return найденная заявка или {@link Optional#empty()}, если ключ некорректен или заявка отсутствует
     */
    @Transactional(readOnly = true)
    public Optional<OrderReturnRequest> findByIdempotencyKey(String idempotencyKey) {
        String normalizedKey = canonicalizeIdempotencyKey(idempotencyKey);
        if (normalizedKey == null) {
            return Optional.empty();
        }
        return returnRequestRepository.findByIdempotencyKey(normalizedKey);
    }

    /**
     * Ищет заявку по UUID идемпотентного ключа.
     *
     * @param requestKey UUID ключа заявки
     * @return найденная заявка или {@link Optional#empty()}, если ключ отсутствует
     */
    @Transactional(readOnly = true)
    public Optional<OrderReturnRequest> findByIdempotencyKey(UUID requestKey) {
        if (requestKey == null) {
            return Optional.empty();
        }
        return findByIdempotencyKey(requestKey.toString());
    }

    /**
     * Загружает заявку по UUID ключа вместе с ключевыми связями для REST-слоя.
     *
     * @param requestKey UUID ключа заявки
     * @return заявка с инициализированными связями или {@link Optional#empty()}, если она не найдена
     */
    @Transactional(readOnly = true)
    public Optional<OrderReturnRequest> findByIdempotencyKeyWithDetails(UUID requestKey) {
        if (requestKey == null) {
            return Optional.empty();
        }
        String normalizedKey = canonicalizeIdempotencyKey(requestKey.toString());
        if (normalizedKey == null) {
            return Optional.empty();
        }
        return returnRequestRepository.findByIdempotencyKeyWithDetails(normalizedKey);
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
    public OrderReturnRequest getOwnedRequest(UUID requestKey, User user) {
        if (requestKey == null) {
            throw new ValidationException("Не указан идентификатор заявки");
        }
        if (user == null || user.getId() == null) {
            throw new ValidationException("Не указан пользователь");
        }
        OrderReturnRequest request = findByIdempotencyKeyWithDetails(requestKey)
                .orElseThrow(() -> new EntityNotFoundException("Заявка не найдена"));
        ensureOwnership(request, user.getId());
        return request;
    }

    /**
     * Загружает заявку и проверяет, что пользователь владеет посылкой.
     */
    private OrderReturnRequest loadOwnedRequest(Long requestId, Long parcelId, User user) {
        if (requestId == null || parcelId == null) {
            throw new ValidationException("Идентификаторы заявки и посылки обязательны");
        }
        if (user == null || user.getId() == null) {
            throw new ValidationException("Не указан пользователь");
        }
        OrderReturnRequest request = returnRequestRepository.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException("Заявка не найдена"));
        ensureOwnership(request, user.getId());
        if (!parcelId.equals(Optional.ofNullable(request.getParcel()).map(TrackParcel::getId).orElse(null))) {
            throw new ValidationException("Заявка не относится к указанной посылке");
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
     * Приводит идемпотентный ключ к каноническому виду для хранения и поиска.
     * <p>
     * Метод обрезает пробелы по краям и переводит значение в нижний регистр,
     * чтобы одинаковые UUID, записанные в разных форматах, считались одной и той же заявкой.
     * Возвращает {@code null}, если ключ не содержит символов после обрезки.
     * </p>
     *
     * @param idempotencyKey исходное значение ключа
     * @return нормализованный ключ или {@code null}, если строка пуста
     */
    private String canonicalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            return null;
        }
        String trimmed = idempotencyKey.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
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
        confirmReturnProcessing(request, actor, stageMoment, true);
    }

    /**
     * Фиксирует получение возврата с учётом источника события.
     * <p>
     * Метод переиспользуется как ручными, так и автоматическими сценариями,
     * поэтому устанавливает ответственного менеджера только при явном указании
     * пользователя. Это позволяет не затирать данные при обработке триггеров
     * из трекинга и соблюсти принцип единой ответственности.
     * </p>
     */
    private void confirmReturnProcessing(OrderReturnRequest request,
                                         User actor,
                                         ZonedDateTime stageMoment,
                                         boolean manualTransition) {
        if (request == null) {
            return;
        }
        ZonedDateTime normalizedMoment = normalizeStageMoment(stageMoment);
        if (!request.isReturnReceiptConfirmed()) {
            request.setReturnReceiptConfirmed(true);
            request.setReturnReceiptConfirmedAt(normalizedMoment);
        }
        if (actor != null) {
            request.setResponsibleManager(actor);
        }
        returnRequestWorkflow.transitionSequentially(request,
                ReturnRequestStage.INBOUND_PICKED_UP,
                manualTransition,
                actor,
                normalizedMoment);
    }

    /**
     * Нормализует причину возврата и валидирует длину строки.
     */
    private String normalizeReason(String reason) {
        if (reason == null) {
            throw new ValidationException("Не указана причина возврата");
        }
        String normalized = reason.trim();
        if (normalized.isEmpty()) {
            throw new ValidationException("Не указана причина возврата");
        }
        if (normalized.length() > 255) {
            throw new ValidationException("Причина возврата не должна превышать 255 символов");
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
            throw new ValidationException("Комментарий не должен превышать 2000 символов");
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
            throw new ValidationException("Дата запроса возврата не может быть в будущем");
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
            throw new ValidationException("Трек обратной отправки не должен превышать 64 символа");
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
            throw new ValidationException("Не указан трек обменной посылки");
        }
        String normalized = exchangeTrackNumber.trim();
        if (normalized.isEmpty()) {
            throw new ValidationException("Не указан трек обменной посылки");
        }
        if (normalized.length() > 64) {
            throw new ValidationException("Трек обменной посылки не должен превышать 64 символа");
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
            throw new ValidationException("Момент фиксации стадии не может быть в будущем");
        }
        return utc;
    }
}

