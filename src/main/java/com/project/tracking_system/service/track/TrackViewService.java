package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.OrderReturnRequestDto;
import com.project.tracking_system.dto.TrackChainItemDto;
import com.project.tracking_system.dto.TrackDetailsDto;
import com.project.tracking_system.dto.TrackLifecycleStageDto;
import com.project.tracking_system.dto.TrackLifecycleStageState;
import com.project.tracking_system.dto.TrackStatusEventDto;
import com.project.tracking_system.entity.DeliveryHistory;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.OrderEpisode;
import com.project.tracking_system.entity.OrderReturnRequest;
import com.project.tracking_system.entity.OrderReturnRequestStatus;
import com.project.tracking_system.entity.PostalServiceType;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.TrackStatusEvent;
import com.project.tracking_system.service.order.OrderExchangeService;
import com.project.tracking_system.service.order.OrderReturnRequestService;
import com.project.tracking_system.service.admin.ApplicationSettingsService;
import com.project.tracking_system.service.user.UserService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Сервис чтения сохранённых данных о посылке для модального окна.
 * <p>
 * Реализация не выполняет обращений к внешним почтовым сервисам и работает
 * только с данными, уже сохранёнными в БД, что снижает нагрузку и ускоряет
 * открытие модалки при повторных запросах.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrackViewService {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final TrackParcelService trackParcelService;
    private final TrackStatusEventService trackStatusEventService;
    private final UserService userService;
    private final ApplicationSettingsService applicationSettingsService;
    private final OrderReturnRequestService orderReturnRequestService;
    private final OrderExchangeService orderExchangeService;

    /**
     * Возвращает DTO с данными о посылке для текущего пользователя.
     * <p>
     * Результат кэшируется на несколько секунд, чтобы повторное открытие модалки
     * не инициировало дополнительные запросы в БД (SRP: сервис считает только
     * бизнес-логику, кэш управляется инфраструктурой).
     * </p>
     *
     * @param trackId идентификатор посылки
     * @param userId  идентификатор пользователя
     * @return подготовленный DTO
     */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "track-details", key = "#userId + ':' + #trackId")
    public TrackDetailsDto getTrackDetails(Long trackId, Long userId) {
        TrackParcel parcel = loadParcel(trackId, userId);
        ZoneId userZone = userService.getUserZone(userId);

        List<TrackStatusEventDto> history = buildHistory(parcel, userZone);
        TrackStatusEventDto currentStatus = history.isEmpty() ? null : history.get(0);

        boolean refreshAllowed = isRefreshAllowed(parcel);
        String nextRefreshAt = resolveNextRefreshAt(parcel, refreshAllowed, userZone);
        boolean canEditTrack = canEditTrack(parcel);

        PostalServiceType serviceType = Optional.ofNullable(parcel.getDeliveryHistory())
                .map(DeliveryHistory::getPostalService)
                .orElse(null);

        String systemStatus = Optional.ofNullable(parcel.getStatus())
                .map(GlobalStatus::getDescription)
                .orElse(null);
        String lastUpdateAt = Optional.ofNullable(parcel.getLastUpdate())
                .map(moment -> formatTimestamp(moment, userZone))
                .orElse(null);

        Long episodeNumber = Optional.ofNullable(parcel.getEpisode())
                .map(OrderEpisode::getId)
                .orElse(null);
        List<TrackChainItemDto> chain = buildChain(parcel, userId);

        Optional<OrderReturnRequest> currentRequest = orderReturnRequestService
                .findCurrentForParcel(parcel.getId());
        OrderReturnRequestDto requestDto = currentRequest
                .map(request -> mapReturnRequest(request, userZone))
                .orElse(null);
        boolean requiresAction = currentRequest.map(OrderReturnRequest::requiresAction).orElse(false);
        boolean canRegisterReturn = canRegisterReturn(parcel, currentRequest);
        List<TrackLifecycleStageDto> lifecycle = buildLifecycle(parcel, currentRequest, userZone);

        return new TrackDetailsDto(
                parcel.getId(),
                parcel.getNumber(),
                serviceType != null ? serviceType.getDisplayName() : null,
                systemStatus,
                lastUpdateAt,
                currentStatus,
                history,
                refreshAllowed,
                nextRefreshAt,
                canEditTrack,
                userZone.getId(),
                episodeNumber,
                parcel.isExchange(),
                chain,
                requestDto,
                canRegisterReturn,
                lifecycle,
                requiresAction
        );
    }

    /**
     * Возвращает историю статусов посылки в пользовательском часовом поясе.
     * Метод инкапсулирует загрузку посылки с проверкой прав и переиспользует
     * единые правила форматирования времени для всех клиентов.
     *
     * @param trackId идентификатор посылки
     * @param userId  идентификатор пользователя
     * @return хронологический список событий; если событий нет, возвращается пустой список
     */
    @Transactional(readOnly = true)
    public List<TrackStatusEventDto> getTrackHistory(Long trackId, Long userId) {
        TrackParcel parcel = loadParcel(trackId, userId);
        ZoneId userZone = userService.getUserZone(userId);
        return buildHistory(parcel, userZone);
    }

    /**
     * Возвращает этапы жизненного цикла посылки с учётом активной заявки на возврат/обмен.
     * Метод повторно использует проверку прав доступа и доменную логику построения стадий,
     * гарантируя единый подход для всех клиентов.
     *
     * @param trackId идентификатор посылки
     * @param userId  идентификатор пользователя
     * @return список этапов; при отсутствии заявки может содержать только исходное отправление
     */
    @Transactional(readOnly = true)
    public List<TrackLifecycleStageDto> getTrackLifecycle(Long trackId, Long userId) {
        TrackParcel parcel = loadParcel(trackId, userId);
        ZoneId userZone = userService.getUserZone(userId);
        Optional<OrderReturnRequest> currentRequest = orderReturnRequestService.findCurrentForParcel(parcel.getId());
        return buildLifecycle(parcel, currentRequest, userZone);
    }

    /**
     * Формирует этапы жизненного цикла заказа для модального окна.
     * Метод объединяет данные посылки, заявки и обменной посылки, сохраняя SRP: вычисление
     * состояния этапов изолировано от контроллеров и шаблонов.
     *
     * @param parcel       исходная посылка
     * @param requestOpt   активная заявка на возврат/обмен, если она есть
     * @param userZone     часовой пояс пользователя для форматирования времени
     * @return упорядоченный список этапов цикла; при отсутствии заявки содержит только исходное отправление
     */
    private List<TrackLifecycleStageDto> buildLifecycle(TrackParcel parcel,
                                                        Optional<OrderReturnRequest> requestOpt,
                                                        ZoneId userZone) {
        List<TrackLifecycleStageDto> stages = new ArrayList<>();

        GlobalStatus currentStatus = parcel.getStatus();
        TrackLifecycleStageState outboundState = determineOutboundState(currentStatus);
        String outboundMoment = outboundState == TrackLifecycleStageState.PLANNED
                ? null
                : formatNullableTimestamp(resolveStatusMoment(parcel), userZone);
        String outboundTrackNumber = normalizeTrackNumber(parcel.getNumber());
        TrackLifecycleStageDto outboundStage = new TrackLifecycleStageDto(
                "OUTBOUND",
                "Отправление магазина",
                "Магазин",
                "Магазин оформляет и отправляет исходную посылку покупателю.",
                outboundState,
                outboundMoment,
                outboundTrackNumber,
                "Исходная посылка"
        );
        stages.add(outboundStage);

        requestOpt.ifPresent(request -> appendReturnLifecycleStages(stages, parcel, request, userZone, outboundMoment));
        return stages;
    }

    /**
     * Добавляет в коллекцию этапы, связанные с возвратом и обменом, только при наличии заявки.
     * Метод изолирует расширенную бизнес-логику построения стадий, сохраняя принцип единственной ответственности.
     */
    private void appendReturnLifecycleStages(List<TrackLifecycleStageDto> stages,
                                             TrackParcel parcel,
                                             OrderReturnRequest request,
                                             ZoneId userZone,
                                             String outboundMoment) {
        TrackLifecycleStageState customerReturnState = TrackLifecycleStageState.PLANNED;
        String customerReturnMoment = null;
        boolean reverseStarted = hasReverseShipmentStarted(parcel, request);
        customerReturnState = reverseStarted
                ? TrackLifecycleStageState.COMPLETED
                : TrackLifecycleStageState.IN_PROGRESS;
        customerReturnMoment = formatNullableTimestamp(request.getRequestedAt(), userZone);
        String reverseTrackNumber = normalizeTrackNumber(request.getReverseTrackNumber());
        String reverseTrackContext = "Обратный трек";
        stages.add(new TrackLifecycleStageDto(
                "CUSTOMER_RETURN",
                "Возврат от покупателя",
                "Покупатель",
                "Покупатель оформляет заявку и отправляет посылку обратно магазину.",
                customerReturnState,
                customerReturnMoment,
                reverseTrackNumber,
                reverseTrackContext
        ));

        TrackLifecycleStageState merchantProcessingState = TrackLifecycleStageState.PLANNED;
        String merchantProcessingMoment = null;
        boolean processed = isReturnProcessed(request, parcel);
        if (processed) {
            merchantProcessingState = TrackLifecycleStageState.COMPLETED;
            merchantProcessingMoment = firstNonNull(
                    formatNullableTimestamp(request.getReturnReceiptConfirmedAt(), userZone),
                    formatNullableTimestamp(request.getDecisionAt(), userZone),
                    formatNullableTimestamp(request.getClosedAt(), userZone),
                    outboundMoment
            );
            if (merchantProcessingMoment == null) {
                merchantProcessingMoment = formatNullableTimestamp(resolveStatusMoment(parcel), userZone);
            }
        } else if (hasReverseShipmentStarted(parcel, request)) {
            merchantProcessingState = TrackLifecycleStageState.IN_PROGRESS;
        }
        stages.add(new TrackLifecycleStageDto(
                "MERCHANT_ACCEPT_RETURN",
                "Приём возврата магазином",
                "Магазин",
                "Менеджер проверяет возврат и принимает решение: закрыть заявку или запустить обмен.",
                merchantProcessingState,
                merchantProcessingMoment,
                null,
                null
        ));

        TrackParcel exchangeParcel = orderExchangeService.findLatestExchangeParcel(request).orElse(null);
        if (shouldShowExchangeStages(request, exchangeParcel)) {
            boolean hasExchangeParcel = exchangeParcel != null;
            boolean hasExchangeTrack = hasExchangeParcel
                    && exchangeParcel.getNumber() != null
                    && !exchangeParcel.getNumber().isBlank();
            GlobalStatus exchangeStatus = hasExchangeParcel ? exchangeParcel.getStatus() : null;
            boolean exchangeFinalStatus = exchangeStatus != null && exchangeStatus.isFinal();

            TrackLifecycleStageState exchangeCreationState = TrackLifecycleStageState.PLANNED;
            String exchangeCreationMoment = null;
            if (hasExchangeParcel && (hasExchangeTrack || exchangeFinalStatus)) {
                exchangeCreationState = exchangeFinalStatus
                        ? TrackLifecycleStageState.COMPLETED
                        : TrackLifecycleStageState.IN_PROGRESS;
                exchangeCreationMoment = formatNullableTimestamp(exchangeParcel.getTimestamp(), userZone);
            }

            String exchangeTrackNumber = hasExchangeTrack
                    ? normalizeTrackNumber(exchangeParcel.getNumber())
                    : null;
            stages.add(new TrackLifecycleStageDto(
                    "EXCHANGE_SHIPMENT",
                    "Отправление обмена",
                    "Магазин",
                    "После подтверждения возврата магазин создаёт обменную посылку.",
                    exchangeCreationState,
                    exchangeCreationMoment,
                    exchangeTrackNumber,
                    exchangeTrackNumber != null || exchangeCreationState != TrackLifecycleStageState.PLANNED
                            ? "Обменная посылка"
                            : null
            ));

            TrackLifecycleStageState exchangeDeliveryState = TrackLifecycleStageState.PLANNED;
            String exchangeDeliveryMoment = null;
            if (hasExchangeParcel && (hasExchangeTrack || exchangeFinalStatus)) {
                if (exchangeFinalStatus) {
                    exchangeDeliveryState = TrackLifecycleStageState.COMPLETED;
                    exchangeDeliveryMoment = formatNullableTimestamp(resolveStatusMoment(exchangeParcel), userZone);
                } else {
                    exchangeDeliveryState = TrackLifecycleStageState.IN_PROGRESS;
                    exchangeDeliveryMoment = formatNullableTimestamp(exchangeParcel.getLastUpdate(), userZone);
                }
            }

            stages.add(new TrackLifecycleStageDto(
                    "EXCHANGE_DELIVERY",
                    "Получение обмена",
                    "Покупатель",
                    "Покупатель забирает новую посылку. Цикл завершается до следующей заявки.",
                    exchangeDeliveryState,
                    exchangeDeliveryMoment,
                    exchangeTrackNumber,
                    exchangeTrackNumber != null || exchangeDeliveryState != TrackLifecycleStageState.PLANNED
                            ? "Обменная посылка"
                            : null
            ));
        }
    }

    /**
     * Определяет состояние этапа отправления магазина на основе статуса посылки.
     */
    private TrackLifecycleStageState determineOutboundState(GlobalStatus status) {
        if (status == null || status == GlobalStatus.PRE_REGISTERED) {
            return TrackLifecycleStageState.PLANNED;
        }
        if (status.isFinal() || status == GlobalStatus.RETURN_IN_PROGRESS
                || status == GlobalStatus.RETURN_PENDING_PICKUP || status == GlobalStatus.RETURNED) {
            return TrackLifecycleStageState.COMPLETED;
        }
        return TrackLifecycleStageState.IN_PROGRESS;
    }

    /**
     * Проверяет, отправил ли покупатель обратную посылку.
     */
    private boolean hasReverseShipmentStarted(TrackParcel parcel, OrderReturnRequest request) {
        if (request.getReverseTrackNumber() != null && !request.getReverseTrackNumber().isBlank()) {
            return true;
        }
        GlobalStatus status = parcel.getStatus();
        return status == GlobalStatus.RETURN_IN_PROGRESS
                || status == GlobalStatus.RETURN_PENDING_PICKUP
                || status == GlobalStatus.RETURNED;
    }

    /**
     * Проверяет, обработал ли магазин возврат.
     * <p>
     * Этап считается завершённым только после явного подтверждения менеджером
     * или если исходная посылка получила финальный возвратный статус,
     * что исключает ложные срабатывания при простом закрытии заявки.
     * </p>
     */
    private boolean isReturnProcessed(OrderReturnRequest request, TrackParcel parcel) {
        if (request == null) {
            return false;
        }
        if (request.isReturnReceiptConfirmed()) {
            return true;
        }
        if (parcel == null) {
            return false;
        }
        GlobalStatus status = parcel.getStatus();
        if (status == null) {
            return false;
        }
        if (status == GlobalStatus.RETURNED) {
            return true;
        }
        if (status == GlobalStatus.DELIVERED && wasReturnDeliveredAfterRequest(parcel, request)) {
            return true;
        }
        return false;
    }

    /**
     * Проверяет, достигла ли посылка финального статуса «Вручена» уже после запроса на возврат.
     * <p>
     * Исходная отправка тоже заканчивается статусом «Вручена», поэтому мы сравниваем временные метки
     * и считаем этап завершённым только когда магазин действительно получил обратную посылку.
     * </p>
     */
    private boolean wasReturnDeliveredAfterRequest(TrackParcel parcel, OrderReturnRequest request) {
        ZonedDateTime statusMoment = resolveStatusMoment(parcel);
        if (statusMoment == null) {
            return false;
        }
        ZonedDateTime requestMoment = coalesceReturnRequestMoment(request);
        return requestMoment != null && !statusMoment.isBefore(requestMoment);
    }

    /**
     * Определяет базовый момент начала обработки возврата.
     * <p>
     * Приоритет отдаётся времени пользовательского запроса, а при его отсутствии используем дату регистрации,
     * чтобы корректно работать с переоткрытыми заявками и тестовыми данными.
     * </p>
     */
    private ZonedDateTime coalesceReturnRequestMoment(OrderReturnRequest request) {
        if (request == null) {
            return null;
        }
        return firstNonNullMoment(request.getRequestedAt(), request.getCreatedAt());
    }

    /**
     * Возвращает первую непустую временную метку из переданных аргументов.
     */
    private ZonedDateTime firstNonNullMoment(ZonedDateTime... moments) {
        if (moments == null) {
            return null;
        }
        for (ZonedDateTime moment : moments) {
            if (moment != null) {
                return moment;
            }
        }
        return null;
    }

    /**
     * Решает, нужно ли отображать этапы обмена.
     */
    private boolean shouldShowExchangeStages(OrderReturnRequest request, TrackParcel exchangeParcel) {
        if (request == null) {
            return false;
        }
        if (exchangeParcel != null) {
            return true;
        }
        return request.isExchangeRequested() || request.getStatus() == OrderReturnRequestStatus.EXCHANGE_APPROVED;
    }

    /**
     * Возвращает первый непустой момент времени в виде строки.
     */
    private String firstNonNull(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /**
     * Возвращает трек-номер или {@code null}, если строка пустая.
     * Метод устраняет дублирование проверки пустых строк и тем самым упрощает расширение логики (DRY).
     *
     * @param number исходная строка с трек-номером
     * @return очищенный трек-номер либо {@code null}
     */
    private String normalizeTrackNumber(String number) {
        if (number == null || number.isBlank()) {
            return null;
        }
        return number;
    }

    /**
     * Строит DTO для заявки на возврат/обмен.
     */
    private OrderReturnRequestDto mapReturnRequest(OrderReturnRequest request, ZoneId userZone) {
        boolean canStartExchange = orderReturnRequestService.canStartExchange(request);
        boolean canCloseWithoutExchange = request.getStatus() == OrderReturnRequestStatus.REGISTERED;
        boolean canReopenAsReturn = orderReturnRequestService.canReopenAsReturn(request);
        boolean canCancelExchange = orderReturnRequestService.canCancelExchange(request);
        boolean canConfirmReceipt = orderReturnRequestService.canConfirmReceipt(request);
        boolean canAcceptReverse = orderReturnRequestService.canAcceptReverse(request);
        boolean canCreateExchangeParcel = orderReturnRequestService.canCreateExchangeParcel(request);
        String requestedAt = formatNullableTimestamp(request.getRequestedAt(), userZone);
        // Подставляем дату регистрации, если пользовательское обращение отсутствует, чтобы модалка не показывала дубль.
        if (requestedAt == null) {
            requestedAt = formatNullableTimestamp(request.getCreatedAt(), userZone);
        }

        String cancelExchangeReason = orderReturnRequestService
                .getExchangeCancellationBlockReason(request)
                .orElse(null);

        return new OrderReturnRequestDto(
                request.getId(),
                request.getStatus().getDisplayName(),
                request.getReason(),
                request.getComment(),
                requestedAt,
                formatNullableTimestamp(request.getDecisionAt(), userZone),
                formatNullableTimestamp(request.getClosedAt(), userZone),
                request.getReverseTrackNumber(),
                request.requiresAction(),
                request.isExchangeApproved(),
                request.isExchangeRequested(),
                canStartExchange,
                canCreateExchangeParcel,
                canCloseWithoutExchange,
                canReopenAsReturn,
                canCancelExchange,
                cancelExchangeReason,
                request.isReturnReceiptConfirmed(),
                formatNullableTimestamp(request.getReturnReceiptConfirmedAt(), userZone),
                canConfirmReceipt,
                canAcceptReverse
        );
    }

    /**
     * Проверяет, можно ли зарегистрировать новую заявку на возврат.
     */
    private boolean canRegisterReturn(TrackParcel parcel, Optional<OrderReturnRequest> currentRequest) {
        if (parcel.getStatus() != GlobalStatus.DELIVERED) {
            return false;
        }
        return currentRequest.isEmpty();
    }

    /**
     * Формирует цепочку посылок в рамках эпизода заказа.
     * <p>
     * Метод запрашивает все посылки эпизода у {@link TrackParcelService},
     * фильтрует по принадлежности пользователю и помечает текущую запись,
     * чтобы фронтенд отобразил навигацию по связанным трекам. Если сервис
     * вернул пустой список, метод гарантированно добавляет текущую посылку
     * в цепочку, сохраняя целостность интерфейса.
     * </p>
     *
     * @param parcel исходная посылка
     * @param userId идентификатор пользователя
     * @return список элементов цепочки в порядке их создания
     */
    private List<TrackChainItemDto> buildChain(TrackParcel parcel, Long userId) {
        OrderEpisode episode = parcel.getEpisode();
        if (episode == null || episode.getId() == null) {
            return List.of(new TrackChainItemDto(
                    parcel.getId(),
                    parcel.getNumber(),
                    parcel.isExchange(),
                    isReturnShipment(parcel),
                    true
            ));
        }

        List<TrackParcel> episodeParcels = Optional.ofNullable(
                trackParcelService.findEpisodeParcels(episode.getId(), userId))
                .orElse(List.of());

        if (episodeParcels.isEmpty()) {
            return List.of(new TrackChainItemDto(
                    parcel.getId(),
                    parcel.getNumber(),
                    parcel.isExchange(),
                    isReturnShipment(parcel),
                    true
            ));
        }

        boolean containsCurrent = episodeParcels.stream()
                .anyMatch(item -> item.getId().equals(parcel.getId()));

        List<TrackChainItemDto> mapped = episodeParcels.stream()
                .map(item -> new TrackChainItemDto(
                        item.getId(),
                        item.getNumber(),
                        item.isExchange(),
                        isReturnShipment(item),
                        item.getId().equals(parcel.getId())
                ))
                .toList();

        if (containsCurrent) {
            return mapped;
        }

        ArrayList<TrackChainItemDto> augmented = new ArrayList<>(mapped);
        augmented.add(new TrackChainItemDto(
                parcel.getId(),
                parcel.getNumber(),
                parcel.isExchange(),
                isReturnShipment(parcel),
                true
        ));
        return augmented;
    }

    /**
     * Преобразует посылку в элемент цепочки эпизода.
     *
     * @param parcel        посылка, которую нужно представить в цепочке
     * @param currentParcel идентификатор текущей посылки для вычисления признака выбранности
     * @return DTO элемента цепочки или {@code null}, если посылка отсутствует
     */
    @Transactional(readOnly = true)
    public TrackChainItemDto toChainItem(TrackParcel parcel, Long currentParcel) {
        if (parcel == null) {
            return null;
        }
        Long parcelId = parcel.getId();
        boolean isCurrent = parcelId != null && parcelId.equals(currentParcel);
        return new TrackChainItemDto(
                parcelId,
                parcel.getNumber(),
                parcel.isExchange(),
                isReturnShipment(parcel),
                isCurrent
        );
    }

    /**
     * Определяет, что посылка представляет собой обратную отправку покупателя в магазин.
     * <p>
     * Логика использует доменные статусы и факт возврата в истории доставки, чтобы
     * отметить такие посылки в списке цепочки и показать менеджеру, что посылка движется
     * к магазину, а не к покупателю.
     * </p>
     *
     * @param parcel посылка для проверки
     * @return {@code true}, если посылка едет обратно в магазин
     */
    private boolean isReturnShipment(TrackParcel parcel) {
        if (parcel == null) {
            return false;
        }
        if (parcel.isExchange()) {
            return false;
        }
        GlobalStatus status = parcel.getStatus();
        if (status == GlobalStatus.RETURN_IN_PROGRESS
                || status == GlobalStatus.RETURN_PENDING_PICKUP
                || status == GlobalStatus.RETURNED) {
            return true;
        }
        return Optional.ofNullable(parcel.getDeliveryHistory())
                .map(DeliveryHistory::getReturnedDate)
                .isPresent();
    }

    /**
     * Загружает посылку и проверяет права доступа.
     */
    private TrackParcel loadParcel(Long trackId, Long userId) {
        Optional<TrackParcel> owned = trackParcelService.findOwnedById(trackId, userId);
        if (owned.isPresent()) {
            return owned.get();
        }
        boolean exists = trackParcelService.findById(trackId).isPresent();
        if (exists) {
            throw new AccessDeniedException("Посылка не принадлежит пользователю");
        }
        log.warn("Не найдена посылка id={} для пользователя {}", trackId, userId);
        throw new EntityNotFoundException("Посылка не найдена");
    }

    /**
     * Возвращает сохранённую историю статусов с учётом пользовательской зоны.
     */
    private List<TrackStatusEventDto> buildHistory(TrackParcel parcel, ZoneId userZone) {
        List<TrackStatusEvent> events = trackStatusEventService.findEvents(parcel.getId());
        if (!events.isEmpty()) {
            return events.stream()
                    .map(event -> new TrackStatusEventDto(
                            event.getDescription(),
                            formatTimestamp(event.getEventTime(), userZone)))
                    .toList();
        }
        return buildFallbackHistory(parcel, userZone);
    }

    /**
     * Формирует минимальную историю на основе агрегированного статуса, если
     * детальные события ещё не сохранены.
     */
    private List<TrackStatusEventDto> buildFallbackHistory(TrackParcel parcel, ZoneId userZone) {
        GlobalStatus aggregateStatus = parcel.getStatus();
        ZonedDateTime aggregateMoment = resolveStatusMoment(parcel);
        if (aggregateStatus == null || aggregateMoment == null) {
            return List.of();
        }
        String description = aggregateStatus.getDescription();
        return List.of(new TrackStatusEventDto(
                description,
                formatTimestamp(aggregateMoment, userZone)
        ));
    }

    /**
     * Определяет, можно ли инициировать обновление трека.
     */
    private boolean isRefreshAllowed(TrackParcel parcel) {
        if (parcel.getStatus() != null && parcel.getStatus().isFinal()) {
            return false;
        }
        int interval = applicationSettingsService.getTrackUpdateIntervalHours();
        ZonedDateTime threshold = ZonedDateTime.now(ZoneOffset.UTC).minusHours(interval);
        ZonedDateTime lastUpdate = parcel.getLastUpdate();
        return lastUpdate == null || lastUpdate.isBefore(threshold);
    }

    /**
     * Вычисляет момент следующего допустимого обновления.
     */
    private String resolveNextRefreshAt(TrackParcel parcel, boolean refreshAllowed, ZoneId userZone) {
        if (refreshAllowed || parcel.getStatus() != null && parcel.getStatus().isFinal()) {
            return null;
        }
        int interval = applicationSettingsService.getTrackUpdateIntervalHours();
        ZonedDateTime nextUpdate = parcel.getLastUpdate().plusHours(interval);
        return formatTimestamp(nextUpdate, userZone);
    }

    /**
     * Определяет, разрешено ли редактирование трека.
     */
    private boolean canEditTrack(TrackParcel parcel) {
        GlobalStatus status = parcel.getStatus();
        if (status == null) {
            return true;
        }
        return status == GlobalStatus.PRE_REGISTERED || status == GlobalStatus.ERROR;
    }

    /**
     * Приводит дату к ISO-формату с учётом пользовательского часового пояса.
     */
    private String formatTimestamp(ZonedDateTime moment, ZoneId userZone) {
        return moment.withZoneSameInstant(userZone).format(ISO_FORMATTER);
    }

    private String formatNullableTimestamp(ZonedDateTime moment, ZoneId userZone) {
        if (moment == null) {
            return null;
        }
        return formatTimestamp(moment, userZone);
    }

    /**
     * Определяет момент времени для обобщённого статуса посылки.
     * <p>
     * Если точная дата статуса неизвестна (например, история ещё не загружена
     * из внешнего сервиса), используем отметку последнего обновления трека,
     * чтобы пользователь видел актуальную временную метку.
     * </p>
     *
     * @param parcel посылка, для которой нужно определить момент статуса
     * @return момент статуса или {@code null}, если его невозможно вычислить
     */
    private ZonedDateTime resolveStatusMoment(TrackParcel parcel) {
        ZonedDateTime statusMoment = parcel.getTimestamp();
        if (statusMoment != null) {
            return statusMoment;
        }
        return parcel.getLastUpdate();
    }
}

