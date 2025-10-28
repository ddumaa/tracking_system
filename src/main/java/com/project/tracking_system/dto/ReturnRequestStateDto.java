package com.project.tracking_system.dto;

import com.project.tracking_system.entity.ReturnRequestMode;
import com.project.tracking_system.entity.ReturnRequestStage;

/**
 * Текущее состояние заявки на возврат/обмен.
 *
 * @param mode                    режим обработки заявки
 * @param stage                   актуальный этап обработки
 * @param manualStageOverride     признак ручного выбора этапа
 * @param manualTrackOverride     признак ручного выбора трека обмена
 * @param exchangeRequested       заявка оформлена как обмен
 * @param exchangeApproved        обмен запущен
 * @param exchangeShipmentDispatched обменная посылка уже отправлена
 * @param exchangeTrackNumber     трек обменной посылки
 * @param returnReceiptConfirmed  магазин подтвердил получение возврата
 */
public record ReturnRequestStateDto(ReturnRequestMode mode,
                                    ReturnRequestStage stage,
                                    boolean manualStageOverride,
                                    boolean manualTrackOverride,
                                    boolean exchangeRequested,
                                    boolean exchangeApproved,
                                    boolean exchangeShipmentDispatched,
                                    String exchangeTrackNumber,
                                    boolean returnReceiptConfirmed) {
}
