package com.project.tracking_system.dto;

import com.project.tracking_system.entity.OrderReturnRequestStatus;

/**
 * DTO для отображения заявок на возврат/обмен, требующих действий пользователя.
 * <p>
 * Запись содержит сведения о посылке, статусе заявки и агрегированные данные о состоянии,
 * чтобы шаблон "Отправления" мог отрисовать таблицу вкладки «Требуют действия».
 * </p>
 *
 * @param requestId       идентификатор заявки на возврат
 * @param parcelId        идентификатор посылки, к которой относится заявка
 * @param trackNumber     трек-номер посылки или {@code null}, если он отсутствует
 * @param storeName       название магазина, оформившего отправление
 * @param parcelStatus    человеко-читаемый статус посылки
 * @param status          статус самой заявки
 * @param statusLabel     локализованное имя статуса
 * @param reason          причина оформления возврата
 * @param comment         дополнительный комментарий пользователя
 * @param reverseTrackNumber трек обратной отправки, если указан
 * @param state           агрегированное состояние заявки
 * @param availableActions набор доступных действий
 * @param timestamps      временные метки, необходимые интерфейсу
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
                                             String reverseTrackNumber,
                                             ReturnRequestStateDto state,
                                             ReturnRequestAvailableActionsDto availableActions,
                                             ReturnRequestTimestampsDto timestamps) {
}
