package com.project.tracking_system.service.order;

import com.project.tracking_system.dto.ReturnRequestUpdateResponse;
import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.OrderEpisode;
import com.project.tracking_system.entity.OrderReturnRequest;
import com.project.tracking_system.entity.OrderReturnRequestActionRequest;
import com.project.tracking_system.entity.OrderReturnRequestActionType;
import com.project.tracking_system.entity.OrderReturnRequestStatus;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.repository.OrderReturnRequestActionRequestRepository;
import com.project.tracking_system.repository.OrderReturnRequestRepository;
import com.project.tracking_system.service.track.TrackParcelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
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

        OrderReturnRequest saved = returnRequestRepository.save(request);
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

        // Автоматический запуск обмена оставляем ручным, чтобы менеджер успел проверить данные перед созданием посылки.

        OrderReturnRequest saved = returnRequestRepository.save(request);
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
        OrderReturnRequest request = loadOwnedRequest(requestId, parcelId, user);

        if (request.getStatus() != OrderReturnRequestStatus.REGISTERED) {
            throw new IllegalStateException("Заявка уже обработана");
        }

        Long episodeId = Optional.ofNullable(request.getEpisode())
                .map(OrderEpisode::getId)
                .orElse(null);
        if (episodeId != null && returnRequestRepository.existsByEpisode_IdAndStatus(episodeId,
                OrderReturnRequestStatus.EXCHANGE_APPROVED)) {
            throw new IllegalStateException("В эпизоде уже запущен обмен");
        }

        request.setStatus(OrderReturnRequestStatus.EXCHANGE_APPROVED);
        request.setDecisionBy(user);
        request.setDecisionAt(ZonedDateTime.now(ZoneOffset.UTC));

        OrderReturnRequest saved = returnRequestRepository.save(request);
        log.info("Одобрен обмен по заявке {} без автоматического создания обменной посылки", saved.getId());
        return saved;
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
        log.info("Создана обменная посылка {} для заявки {}", replacement.getId(), request.getId());
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

        request.setStatus(OrderReturnRequestStatus.CLOSED_NO_EXCHANGE);
        request.setClosedBy(user);
        request.setClosedAt(ZonedDateTime.now(ZoneOffset.UTC));

        OrderReturnRequest saved = returnRequestRepository.save(request);
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
        markReturnProcessingConfirmed(request);
        OrderReturnRequest saved = returnRequestRepository.save(request);
        log.info("Получение возврата подтверждено вручную для заявки {}", saved.getId());
        return saved;
    }

    /**
     * Отменяет обмен по активной заявке пользователя.
     */
    @Transactional
    public OrderReturnRequest cancelExchange(Long requestId, Long parcelId, User user) {
        OrderReturnRequest request = loadOwnedRequest(requestId, parcelId, user);
        if (request.getStatus() != OrderReturnRequestStatus.EXCHANGE_APPROVED) {
            throw new IllegalStateException("Обмен ещё не запущен или заявка уже закрыта");
        }
        TrackParcel replacement;
        try {
            replacement = orderExchangeService.getLatestExchangeParcelOrThrowIfTracked(request)
                    .orElse(null);
        } catch (IllegalStateException ex) {
            log.warn("Нельзя отменить обмен по заявке {}: {}", request.getId(), ex.getMessage());
            throw ex;
        }
        request.setStatus(OrderReturnRequestStatus.CLOSED_NO_EXCHANGE);
        request.setClosedBy(user);
        request.setClosedAt(ZonedDateTime.now(ZoneOffset.UTC));
        orderExchangeService.cancelExchangeParcel(request, replacement);
        episodeLifecycleService.decrementExchangeCount(request.getEpisode());
        OrderReturnRequest saved = returnRequestRepository.save(request);
        log.info("Обмен по заявке {} отменён пользователем", saved.getId());
        return saved;
    }

    /**
     * Переводит одобренный обмен обратно в статус возврата без удаления заявки.
     */
    @Transactional
    public OrderReturnRequest reopenAsReturn(Long requestId, Long parcelId, User user) {
        OrderReturnRequest request = loadOwnedRequest(requestId, parcelId, user);
        if (request.getStatus() != OrderReturnRequestStatus.EXCHANGE_APPROVED) {
            throw new IllegalStateException("Заявка не находится в статусе обмена");
        }
        TrackParcel replacement;
        try {
            replacement = orderExchangeService.getLatestExchangeParcelOrThrowIfTracked(request)
                    .orElse(null);
        } catch (IllegalStateException ex) {
            log.warn("Нельзя перевести обмен по заявке {} в возврат: {}", request.getId(), ex.getMessage());
            throw ex;
        }
        request.setStatus(OrderReturnRequestStatus.REGISTERED);
        request.setDecisionBy(null);
        request.setDecisionAt(null);
        request.setClosedBy(null);
        request.setClosedAt(null);
        request.setExchangeRequested(false);
        orderExchangeService.cancelExchangeParcel(request, replacement);
        episodeLifecycleService.decrementExchangeCount(request.getEpisode());
        OrderReturnRequest saved = returnRequestRepository.save(request);
        log.info("Заявка {} переведена из обмена в возврат", saved.getId());
        return saved;
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
                                                                 OrderReturnRequestActionType action) {
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
        if (episodeId == null) {
            return true;
        }
        return !returnRequestRepository.existsByEpisode_IdAndStatus(episodeId,
                OrderReturnRequestStatus.EXCHANGE_APPROVED);
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
                .orElse(false);
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
     * Устанавливает отметку о фактическом приёме возврата.
     * <p>
     * Приватный хелпер гарантирует, что фиксация момента обработки собрана в одном месте,
     * что облегчает расширение бизнес-правил и соответствует принципу DRY.
     * </p>
     */
    private void markReturnProcessingConfirmed(OrderReturnRequest request) {
        if (request == null || request.isReturnReceiptConfirmed()) {
            return;
        }
        request.setReturnReceiptConfirmed(true);
        request.setReturnReceiptConfirmedAt(ZonedDateTime.now(ZoneOffset.UTC));
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
}

