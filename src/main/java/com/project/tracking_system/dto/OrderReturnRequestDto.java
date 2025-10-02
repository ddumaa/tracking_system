package com.project.tracking_system.dto;

/**
 * DTO заявки на возврат/обмен для модального окна трека.
 *
 * @param id                     идентификатор заявки
 * @param status                 человеко-читаемый статус
 * @param createdAt              дата регистрации
 * @param decisionAt             дата принятия решения об обмене
 * @param closedAt               дата закрытия без обмена
 * @param requiresAction         признак, что заявка ожидает действий
 * @param exchangeApproved       признак, что обмен уже запущен
 * @param canStartExchange       доступность кнопки запуска обмена
 * @param canCloseWithoutExchange доступность закрытия без обмена
 */
public record OrderReturnRequestDto(Long id,
                                    String status,
                                    String createdAt,
                                    String decisionAt,
                                    String closedAt,
                                    boolean requiresAction,
                                    boolean exchangeApproved,
                                    boolean canStartExchange,
                                    boolean canCloseWithoutExchange) {
}

