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
 * @param exchangeRequested         признак, что покупатель запросил обмен при регистрации
 * @param canStartExchange          признак доступности запуска обмена
 * @param canCloseWithoutExchange   признак доступности закрытия без обмена
 * @param canReopenAsReturn         признак возможности вернуть заявку из обмена в возврат
 * @param canCancelExchange         признак доступности отмены обмена
 * @param exchangeShipmentDispatched признак, что обменная посылка уже отправлена
 * @param cancelExchangeUnavailableReason сообщение о недоступности отмены обмена
 * @param returnReceiptConfirmed признак, что магазин подтвердил получение возврата
 * @param returnReceiptConfirmedAt дата подтверждения возврата
 * @param canConfirmReceipt доступность ручного подтверждения приёма
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
                                             boolean exchangeRequested,
                                             boolean canStartExchange,
                                             boolean canCloseWithoutExchange,
                                             boolean canReopenAsReturn,
                                             boolean canCancelExchange,
                                             boolean exchangeShipmentDispatched,
                                             String cancelExchangeUnavailableReason,
                                             boolean returnReceiptConfirmed,
                                             String returnReceiptConfirmedAt,
                                             boolean canConfirmReceipt) {
}
