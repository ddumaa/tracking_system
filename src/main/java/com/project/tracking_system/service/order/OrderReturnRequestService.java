package com.project.tracking_system.service.order;

import com.project.tracking_system.dto.ReturnRequestUpdateResponse;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.OrderEpisode;
import com.project.tracking_system.entity.OrderReturnRequest;
import com.project.tracking_system.entity.OrderReturnRequestStatus;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.User;
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
     * @return созданная или ранее зарегистрированная заявка
     */
    @Transactional
    public OrderReturnRequest registerReturn(Long parcelId,
                                             User user,
                                             String idempotencyKey,
                                             String reason,
                                             String comment,
                                             ZonedDateTime requestedAt,
                                             String reverseTrack) {
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
                    || !Objects.equals(existing.getReverseTrackNumber(), normalizedReverse)) {
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

        OrderReturnRequest saved = returnRequestRepository.save(request);
        log.info("Зарегистрирована заявка на возврат {} для посылки {}", saved.getId(), parcelId);
        return saved;
    }

    /**
     * Одобряет запуск обмена по заявке.
     *
     * @param requestId идентификатор заявки
     * @param parcelId  идентификатор посылки
     * @param user      автор решения
     * @return результат с обновлённой заявкой и созданной обменной посылкой
     */
    @Transactional
    public ExchangeApprovalResult approveExchange(Long requestId, Long parcelId, User user) {
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
        TrackParcel exchangeParcel = orderExchangeService.createExchangeParcel(saved);
        log.info("Одобрен обмен по заявке {}", saved.getId());
        return new ExchangeApprovalResult(saved, exchangeParcel);
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
     * Отменяет обмен по активной заявке пользователя.
     */
    @Transactional
    public OrderReturnRequest cancelExchange(Long requestId, Long parcelId, User user) {
        OrderReturnRequest request = loadOwnedRequest(requestId, parcelId, user);
        if (request.getStatus() != OrderReturnRequestStatus.EXCHANGE_APPROVED) {
            throw new IllegalStateException("Обмен ещё не запущен или заявка уже закрыта");
        }
        request.setStatus(OrderReturnRequestStatus.CLOSED_NO_EXCHANGE);
        request.setClosedBy(user);
        request.setClosedAt(ZonedDateTime.now(ZoneOffset.UTC));
        orderExchangeService.cancelExchangeParcel(request);
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
        request.setStatus(OrderReturnRequestStatus.REGISTERED);
        request.setDecisionBy(null);
        request.setDecisionAt(null);
        request.setClosedBy(null);
        request.setClosedAt(null);
        orderExchangeService.cancelExchangeParcel(request);
        episodeLifecycleService.decrementExchangeCount(request.getEpisode());
        OrderReturnRequest saved = returnRequestRepository.save(request);
        log.info("Заявка {} переведена из обмена в возврат", saved.getId());
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

