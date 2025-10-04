package com.project.tracking_system.dto;

import com.project.tracking_system.entity.OrderReturnRequestStatus;

/**
 * DTO для отображения заявок на возврат/обмен, требующих действий пользователя.
 * <p>
 * Запись содержит сведения о посылке, статусе заявки и доступных действиях,
 * чтобы шаблон "Отправления" мог отрисовать таблицу вкладки «Требуют действия».
 * </p>
 *
 * @param requestId                 идентификатор заявки на возврат
 * @param parcelId                  идентификатор посылки, к которой относится заявка
 * @param trackNumber               трек-номер посылки или {@code null}, если он отсутствует
 * @param storeName                 название магазина, оформившего отправление
 * @param parcelStatus              человеко-читаемый статус посылки
 * @param status                    статус самой заявки
 * @param statusLabel               локализованное имя статуса
 * @param requestedAt               дата обращения пользователя в локальной зоне
 * @param createdAt                 дата регистрации заявки в системе
 * @param reason                    причина оформления возврата
 * @param comment                   дополнительный комментарий пользователя
 * @param reverseTrackNumber        трек обратной отправки, если указан
 * @param canStartExchange          признак доступности запуска обмена
 * @param canCloseWithoutExchange   признак доступности закрытия без обмена
 * @param cancelExchangeUnavailableReason сообщение о недоступности отмены обмена
 */
public record ActionRequiredReturnRequestDto(Long requestId,
                                             Long parcelId,
                                             String trackNumber,
                                             String storeName,
                                             String parcelStatus,
                                             OrderReturnRequestStatus status,
                                             String statusLabel,
                                             String requestedAt,
                                             String createdAt,
                                             String reason,
                                             String comment,
                                             String reverseTrackNumber,
                                             boolean canStartExchange,
                                             boolean canCloseWithoutExchange,
                                             String cancelExchangeUnavailableReason) {
}
