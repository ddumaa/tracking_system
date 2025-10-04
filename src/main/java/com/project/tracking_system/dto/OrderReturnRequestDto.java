package com.project.tracking_system.dto;

/**
 * DTO заявки на возврат/обмен для модального окна трека.
 *
 * @param id                       идентификатор заявки
 * @param status                   человеко-читаемый статус
 * @param reason                   причина оформления возврата
 * @param comment                  дополнительный комментарий пользователя
 * @param requestedAt              дата, указанная пользователем при обращении (или дата регистрации при её отсутствии)
 * @param decisionAt               дата принятия решения об обмене
 * @param closedAt                 дата закрытия без обмена
 * @param reverseTrackNumber       трек обратной отправки, если указан
 * @param requiresAction           признак, что заявка ожидает действий
 * @param exchangeApproved         признак, что обмен уже запущен
 * @param canStartExchange         доступность кнопки запуска обмена
 * @param canCloseWithoutExchange  доступность закрытия без обмена
 * @param cancelExchangeUnavailableReason сообщение для пользователя, если отмена обмена недоступна
 */
public record OrderReturnRequestDto(Long id,
                                    String status,
                                    String reason,
                                    String comment,
                                    String requestedAt,
                                    String decisionAt,
                                    String closedAt,
                                    String reverseTrackNumber,
                                    boolean requiresAction,
                                    boolean exchangeApproved,
                                    boolean canStartExchange,
                                    boolean canCloseWithoutExchange,
                                    String cancelExchangeUnavailableReason) {
}

