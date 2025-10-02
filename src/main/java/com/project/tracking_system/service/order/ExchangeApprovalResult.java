package com.project.tracking_system.service.order;

import com.project.tracking_system.entity.OrderReturnRequest;
import com.project.tracking_system.entity.TrackParcel;

/**
 * Результат одобрения обмена.
 *
 * @param request        обновлённая заявка на возврат/обмен
 * @param exchangeParcel созданная обменная посылка
 */
public record ExchangeApprovalResult(OrderReturnRequest request,
                                     TrackParcel exchangeParcel) {
}
