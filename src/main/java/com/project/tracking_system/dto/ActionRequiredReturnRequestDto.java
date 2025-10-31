package com.project.tracking_system.dto;

import com.project.tracking_system.entity.OrderReturnRequestStatus;
import com.project.tracking_system.entity.ReturnRequestAction;

import java.util.List;
import java.util.Objects;

/**
 * DTO для отображения заявок на возврат/обмен, требующих действий пользователя.
 * <p>
 * Запись содержит сведения о посылке, статусе заявки и доступных действиях, чтобы
 * веб-интерфейс и Telegram могли отрисовать кнопки без дополнительной бизнес-логики.
 * </p>
 *
 * @param requestId    идентификатор заявки на возврат
 * @param parcelId     идентификатор посылки
 * @param trackNumber  трек-номер посылки
 * @param storeName    название магазина
 * @param parcelStatus человеко-читаемый статус посылки
 * @param status       статус заявки
 * @param statusLabel  локализованное название статуса
 * @param reason       причина оформления возврата
 * @param comment      комментарий покупателя
 * @param reverseTrack обратный трек
 * @param state        агрегированное состояние заявки
 * @param actions      доступные действия
 * @param timestamps   временные метки жизненного цикла
 */
public record ActionRequiredReturnRequestDto(Long requestId,
                                             Long parcelId,
                                             String trackNumber,
                                             String storeName,
                                             String parcelStatus,
                                             OrderReturnRequestStatus status,
                                             String statusLabel,
                                             String reason,
                                             String comment,
                                             String reverseTrack,
                                             ReturnRequestStateDto state,
                                             AvailableActionsDto actions,
                                             ReturnRequestTimestampsDto timestamps) {

    public ActionRequiredReturnRequestDto {
        actions = actions == null ? new AvailableActionsDto(List.of()) : actions;
        timestamps = Objects.requireNonNullElseGet(timestamps, () -> new ReturnRequestTimestampsDto(null, null, null, null, null, null, null, null));
    }

    /**
     * Проверяет наличие доступного действия по его коду.
     */
    public boolean hasAction(ReturnRequestAction action) {
        if (action == null) {
            return false;
        }
        List<String> codes = actions != null ? actions.getActions() : List.of();
        return codes.contains(action.getCode());
    }
}
