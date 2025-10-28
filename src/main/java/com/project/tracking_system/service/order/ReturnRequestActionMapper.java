package com.project.tracking_system.service.order;

import com.project.tracking_system.dto.ActionRequiredReturnRequestDto;
import com.project.tracking_system.dto.ReturnRequestAvailableActionsDto;
import com.project.tracking_system.dto.ReturnRequestStateDto;
import com.project.tracking_system.dto.ReturnRequestTimestampsDto;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.OrderReturnRequest;
import com.project.tracking_system.entity.OrderReturnRequestStatus;
import com.project.tracking_system.entity.ReturnRequestMode;
import com.project.tracking_system.entity.ReturnRequestStage;
import com.project.tracking_system.entity.TrackParcel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Преобразует заявки на возврат в DTO для вкладки «Требуют действия».
 * <p>
 * Компонент инкапсулирует форматирование дат и расчёт флагов действий,
 * чтобы контроллеры могли переиспользовать готовый результат и соблюдать SRP.
 * </p>
 */
@Component
@RequiredArgsConstructor
public class ReturnRequestActionMapper {

    /** Формат дат, используемый в таблице заявок. */
    private static final DateTimeFormatter REQUEST_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final OrderReturnRequestService orderReturnRequestService;

    /**
     * Подготавливает DTO заявки для веб-интерфейса.
     *
     * @param request исходная заявка
     * @param userZone часовой пояс пользователя для отображения дат
     * @return заполненный DTO или {@code null}, если заявка отсутствует
     */
    public ActionRequiredReturnRequestDto toDto(OrderReturnRequest request, ZoneId userZone) {
        if (request == null) {
            return null;
        }
        TrackParcel parcel = request.getParcel();
        Long parcelId = parcel != null ? parcel.getId() : null;
        String trackNumber = parcel != null ? parcel.getNumber() : null;
        String storeName = parcel != null && parcel.getStore() != null ? parcel.getStore().getName() : null;
        GlobalStatus parcelStatus = parcel != null ? parcel.getStatus() : null;
        OrderReturnRequestStatus status = request.getStatus();

        boolean canStartExchange = orderReturnRequestService.canStartExchange(request);
        boolean canCloseWithoutExchange = status == OrderReturnRequestStatus.REGISTERED;
        String cancelExchangeReason = orderReturnRequestService
                .getExchangeCancellationBlockReason(request)
                .orElse(null);
        boolean exchangeShipmentDispatched = orderReturnRequestService.isExchangeShipmentDispatched(request);
        boolean canReopenAsReturn = orderReturnRequestService.canReopenAsReturn(request);
        boolean canCancelExchange = orderReturnRequestService.canCancelExchange(request);
        boolean canConfirmReceipt = orderReturnRequestService.canConfirmReceipt(request);
        ReturnRequestMode mode = request.getMode() != null ? request.getMode() : ReturnRequestMode.RETURN;
        ReturnRequestStage stage = request.getStage() != null ? request.getStage() : ReturnRequestStage.CUSTOMER_RETURN;

        ReturnRequestStateDto state = new ReturnRequestStateDto(
                mode,
                stage,
                request.isManualStageOverride(),
                request.isManualTrackOverride(),
                request.isExchangeRequested(),
                request.isExchangeApproved(),
                exchangeShipmentDispatched,
                request.getExchangeTrackNumber(),
                request.isReturnReceiptConfirmed()
        );

        ReturnRequestAvailableActionsDto availableActions = new ReturnRequestAvailableActionsDto(
                canStartExchange,
                orderReturnRequestService.canCreateExchangeParcel(request),
                canCloseWithoutExchange,
                canReopenAsReturn,
                canCancelExchange,
                canConfirmReceipt,
                cancelExchangeReason
        );

        ReturnRequestTimestampsDto timestamps = new ReturnRequestTimestampsDto(
                formatRequestMoment(request.getRequestedAt(), userZone),
                formatRequestMoment(request.getCreatedAt(), userZone),
                formatRequestMoment(request.getDecisionAt(), userZone),
                formatRequestMoment(request.getClosedAt(), userZone),
                formatRequestMoment(request.getStageStartedAt(), userZone),
                formatRequestMoment(request.getStageUpdatedAt(), userZone),
                formatRequestMoment(request.getExchangeTrackAssignedAt(), userZone),
                formatRequestMoment(request.getReturnReceiptConfirmedAt(), userZone)
        );

        return new ActionRequiredReturnRequestDto(
                request.getId(),
                parcelId,
                trackNumber,
                storeName,
                parcelStatus != null ? parcelStatus.getDescription() : null,
                status,
                status != null ? status.getDisplayName() : null,
                request.getReason(),
                request.getComment(),
                request.getReverseTrackNumber(),
                state,
                availableActions,
                timestamps
        );
    }

    /**
     * Форматирует дату обращения в выбранной временной зоне.
     *
     * @param moment исходная дата в UTC
     * @param userZone часовой пояс пользователя
     * @return отформатированная строка или {@code null}, если дата отсутствует
     */
    private String formatRequestMoment(ZonedDateTime moment, ZoneId userZone) {
        if (moment == null || userZone == null) {
            return null;
        }
        return REQUEST_DATE_FORMATTER.format(moment.withZoneSameInstant(userZone));
    }
}
