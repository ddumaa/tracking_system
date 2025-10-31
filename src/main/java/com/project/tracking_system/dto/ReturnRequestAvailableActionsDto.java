package com.project.tracking_system.dto;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

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
 * @param actionCodes                     список доступных действий в виде кодов
 */
public record ReturnRequestAvailableActionsDto(boolean startExchange,
                                               boolean createExchangeParcel,
                                               boolean closeWithoutExchange,
                                               boolean reopenAsReturn,
                                               boolean cancelExchange,
                                               boolean confirmReceipt,
                                               String cancelExchangeUnavailableReason,
                                               Set<String> actionCodes) {

    public ReturnRequestAvailableActionsDto {
        if (actionCodes == null || actionCodes.isEmpty()) {
            actionCodes = Set.of();
        } else {
            actionCodes = Collections.unmodifiableSet(new LinkedHashSet<>(actionCodes));
        }
    }
}
