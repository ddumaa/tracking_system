package com.project.tracking_system.service.order;

import com.project.tracking_system.dto.AvailableActionsDto;
import com.project.tracking_system.dto.RequestDto;
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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
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
    public RequestDto toDto(OrderReturnRequest request, ZoneId userZone) {
        if (request == null) {
            return null;
        }
        ZoneId zone = Optional.ofNullable(userZone).orElse(ZoneOffset.UTC);

        EnumSet<ReturnRequestAction> activeActions = orderReturnRequestService.resolveAvailableActions(request);
        List<AvailableActionsDto> actions = buildAvailableActions(request, activeActions);

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
        ReturnRequestStage stage = Optional.ofNullable(request.getStage()).orElse(ReturnRequestStage.NEW);

        OrderReturnRequestStatus status = request.getStatus();

        Long storeId = Optional.ofNullable(request.getStore())
                .map(store -> store.getId())
                .orElse(null);
        Long responsibleId = Optional.ofNullable(request.getResponsibleManager())
                .map(manager -> manager.getId())
                .orElse(null);

        boolean manualInboundPick = request.isManualStageOverride() && stage == ReturnRequestStage.INBOUND_PICKED_UP;
        boolean manualReverseTrack = request.isManualTrackOverride();
        boolean exchangeShipmentDispatched = orderReturnRequestService.isExchangeShipmentDispatched(request);

        return new RequestDto(
                request.getId(),
                status != null ? status.name() : null,
                status != null ? status.getDisplayName() : null,
                request.getReason(),
                request.getComment(),
                mode.name(),
                stage.getCode(),
                request.requiresAction(),
                request.getReverseTrackNumber(),
                request.getExchangeTrackNumber(),
                manualInboundPick,
                manualReverseTrack,
                request.isExchangeRequested(),
                request.isExchangeApproved(),
                exchangeShipmentDispatched,
                request.isReturnReceiptConfirmed(),
                actions,
                timestamps,
                storeId,
                responsibleId
        );
    }

    /**
     * Рассчитывает доступные действия по заявке и возвращает их DTO.
     *
     * @param request заявка на возврат/обмен
     * @return список доступных действий
     */
    public List<AvailableActionsDto> toAvailableActions(OrderReturnRequest request) {
        if (request == null) {
            return List.of();
        }
        EnumSet<ReturnRequestAction> actions = orderReturnRequestService.resolveAvailableActions(request);
        return buildAvailableActions(request, actions);
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

    /**
     * Собирает DTO доступных действий с учётом ограничений.
     */
    private List<AvailableActionsDto> buildAvailableActions(OrderReturnRequest request,
                                                            EnumSet<ReturnRequestAction> active) {
        List<AvailableActionsDto> result = new ArrayList<>();
        String cancelReason = orderReturnRequestService
                .getExchangeCancellationBlockReason(request)
                .orElse(null);
        for (ReturnRequestAction action : ReturnRequestAction.values()) {
            boolean enabled = active.contains(action);
            String reason = null;
            if (!enabled && action == ReturnRequestAction.CANCEL_EXCHANGE) {
                reason = cancelReason;
            }
            result.add(new AvailableActionsDto(
                    action.getCode(),
                    action.getDisplayName(),
                    enabled,
                    reason
            ));
        }
        return result;
    }
}
