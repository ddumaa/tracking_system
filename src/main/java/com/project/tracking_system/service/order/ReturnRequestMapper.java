package com.project.tracking_system.service.order;

import com.project.tracking_system.dto.ReturnRequestAvailableActionsDto;
import com.project.tracking_system.dto.ReturnRequestDto;
import com.project.tracking_system.dto.ReturnRequestStateDto;
import com.project.tracking_system.dto.ReturnRequestTimestampsDto;
import com.project.tracking_system.entity.OrderReturnRequest;
import com.project.tracking_system.entity.OrderReturnRequestStatus;
import com.project.tracking_system.entity.ReturnRequestAction;
import com.project.tracking_system.entity.ReturnRequestMode;
import com.project.tracking_system.entity.ReturnRequestStage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Optional;

/**
 * Маппер доменной заявки на возврат в DTO для API.
 * <p>
 * Компонент инкапсулирует расчёт доступных действий и форматирование дат,
 * чтобы контроллер и сервисы отображения оставались тонкими и следовали SRP.
 * </p>
 */
@Component
@RequiredArgsConstructor
public class ReturnRequestMapper {

    /** Формат ISO для сериализации временных меток. */
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final OrderReturnRequestService orderReturnRequestService;

    /**
     * Преобразует заявку в DTO карточки возврата.
     *
     * @param request исходная заявка
     * @param userZone предпочитаемый часовой пояс пользователя
     * @return заполненный DTO или {@code null}, если заявка отсутствует
     */
    public ReturnRequestDto toDto(OrderReturnRequest request, ZoneId userZone) {
        if (request == null) {
            return null;
        }
        ZoneId zone = Optional.ofNullable(userZone).orElse(ZoneOffset.UTC);

        ReturnRequestAvailableActionsDto availableActions = toAvailableActions(request);

        String requestedAt = formatNullable(request.getRequestedAt(), zone);
        if (requestedAt == null) {
            requestedAt = formatNullable(request.getCreatedAt(), zone);
        }

        ReturnRequestTimestampsDto timestamps = new ReturnRequestTimestampsDto(
                requestedAt,
                formatNullable(request.getCreatedAt(), zone),
                formatNullable(request.getDecisionAt(), zone),
                formatNullable(request.getClosedAt(), zone),
                formatNullable(request.getStageStartedAt(), zone),
                formatNullable(request.getStageUpdatedAt(), zone),
                formatNullable(request.getExchangeTrackAssignedAt(), zone),
                formatNullable(request.getReturnReceiptConfirmedAt(), zone)
        );

        ReturnRequestMode mode = Optional.ofNullable(request.getMode()).orElse(ReturnRequestMode.RETURN);
        ReturnRequestStage stage = Optional.ofNullable(request.getStage()).orElse(ReturnRequestStage.CUSTOMER_RETURN);

        ReturnRequestStateDto state = new ReturnRequestStateDto(
                mode,
                stage,
                request.isManualStageOverride(),
                request.isManualTrackOverride(),
                request.isExchangeRequested(),
                request.isExchangeApproved(),
                orderReturnRequestService.isExchangeShipmentDispatched(request),
                request.getExchangeTrackNumber(),
                request.isReturnReceiptConfirmed()
        );

        OrderReturnRequestStatus status = request.getStatus();

        Long storeId = Optional.ofNullable(request.getStore())
                .map(store -> store.getId())
                .orElse(null);
        Long responsibleId = Optional.ofNullable(request.getResponsibleManager())
                .map(manager -> manager.getId())
                .orElse(null);

        return new ReturnRequestDto(
                request.getId(),
                status != null ? status.getDisplayName() : null,
                request.getReason(),
                request.getComment(),
                request.getReverseTrackNumber(),
                request.requiresAction(),
                state,
                availableActions,
                timestamps,
                storeId,
                responsibleId
        );
    }

    /**
     * Рассчитывает доступные действия по заявке и возвращает их DTO.
     *
     * @param request заявка на возврат/обмен
     * @return DTO доступных действий или {@code null}, если заявка отсутствует
     */
    public ReturnRequestAvailableActionsDto toAvailableActions(OrderReturnRequest request) {
        if (request == null) {
            return null;
        }
        EnumSet<ReturnRequestAction> actions = orderReturnRequestService.resolveAvailableActions(request);
        boolean canStartExchange = orderReturnRequestService.canStartExchange(request);
        boolean canCreateExchangeParcel = orderReturnRequestService.canCreateExchangeParcel(request);
        boolean canConfirmReceipt = orderReturnRequestService.canConfirmReceipt(request);
        String cancelExchangeReason = orderReturnRequestService
                .getExchangeCancellationBlockReason(request)
                .orElse(null);

        return new ReturnRequestAvailableActionsDto(
                canStartExchange,
                canCreateExchangeParcel,
                actions.contains(ReturnRequestAction.CANCEL_RETURN),
                actions.contains(ReturnRequestAction.CONVERT_TO_RETURN),
                actions.contains(ReturnRequestAction.CANCEL_EXCHANGE),
                canConfirmReceipt,
                cancelExchangeReason
        );
    }

    /**
     * Форматирует дату с учётом временной зоны пользователя.
     */
    private String formatNullable(ZonedDateTime moment, ZoneId zone) {
        if (moment == null) {
            return null;
        }
        return ISO_FORMATTER.format(moment.withZoneSameInstant(zone));
    }
}
