package com.project.tracking_system.dto;

/**
 * Доступные действия с заявкой на возврат/обмен.
 *
 * @param startExchange                   признак доступности перевода в обмен
 * @param createExchangeParcel            возможность сформировать обменную посылку
 * @param closeWithoutExchange            доступность закрытия без обмена
 * @param reopenAsReturn                  доступность перевода обмена обратно в возврат
 * @param cancelExchange                  возможность отменить обмен
 * @param confirmReceipt                  доступность подтверждения возврата магазином
 * @param cancelExchangeUnavailableReason причина недоступности отмены обмена
 */
public record ReturnRequestAvailableActionsDto(boolean startExchange,
                                               boolean createExchangeParcel,
                                               boolean closeWithoutExchange,
                                               boolean reopenAsReturn,
                                               boolean cancelExchange,
                                               boolean confirmReceipt,
                                               String cancelExchangeUnavailableReason) {
}
