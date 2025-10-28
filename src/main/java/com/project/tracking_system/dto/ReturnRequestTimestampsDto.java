package com.project.tracking_system.dto;

/**
 * Временные метки жизненного цикла заявки.
 *
 * @param requestedAt             дата обращения пользователя
 * @param createdAt               дата регистрации заявки
 * @param decisionAt              дата принятия решения об обмене
 * @param closedAt                дата закрытия без обмена
 * @param stageStartedAt          дата начала текущего этапа
 * @param stageUpdatedAt          дата последнего обновления этапа
 * @param exchangeTrackAssignedAt дата назначения трека обмена
 * @param returnReceiptConfirmedAt дата подтверждения возврата магазином
 */
public record ReturnRequestTimestampsDto(String requestedAt,
                                         String createdAt,
                                         String decisionAt,
                                         String closedAt,
                                         String stageStartedAt,
                                         String stageUpdatedAt,
                                         String exchangeTrackAssignedAt,
                                         String returnReceiptConfirmedAt) {
}
