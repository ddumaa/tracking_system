package com.project.tracking_system.dto;

import jakarta.validation.constraints.Size;

/**
 * Запрос обновления трека обратной отправки и комментария по заявке возврата.
 * <p>
 * Клиент передаёт только изменённые поля, а контроллер нормализует ввод и
 * передаёт сервису значения для дальнейшей проверки бизнес-правил.
 * </p>
 *
 * @param reverseTrackNumber новый трек обратной отправки либо пустая строка для очистки
 * @param comment            новый комментарий либо {@code null}, если он не меняется
 */
public record ReturnRequestReverseTrackUpdateRequest(
        @Size(max = 64, message = "Трек обратной отправки не должен превышать 64 символа") String reverseTrackNumber,
        @Size(max = 2000, message = "Комментарий не должен превышать 2000 символов") String comment
) {
}
