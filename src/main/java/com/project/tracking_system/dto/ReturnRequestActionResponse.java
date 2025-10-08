package com.project.tracking_system.dto;

/**
 * Ответ на действия с заявками возврата/обмена в модальном окне.
 *
 * @param details       актуальные данные по посылке для модалки
 * @param actionRequired обновлённые сведения для таблицы «Требуют действия»
 */
public record ReturnRequestActionResponse(TrackDetailsDto details,
                                          ActionRequiredReturnRequestDto actionRequired) {
}
