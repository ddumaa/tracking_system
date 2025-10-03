package com.project.tracking_system.dto;

import com.project.tracking_system.entity.OrderReturnRequestStatus;

/**
 * Ответ на обновление обратного трека и комментария заявки.
 * <p>
 * Возвращается покупателю после сохранения изменений через Telegram,
 * чтобы бот мог показать актуальные значения и статус.
 * </p>
 *
 * @param requestId          идентификатор обновлённой заявки
 * @param reverseTrackNumber нормализованный трек обратной отправки или {@code null}
 * @param comment            нормализованный комментарий или {@code null}
 * @param status             актуальный статус заявки после сохранения
 */
public record ReturnRequestUpdateResponse(Long requestId,
                                          String reverseTrackNumber,
                                          String comment,
                                          OrderReturnRequestStatus status) {
}
