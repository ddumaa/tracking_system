package com.project.tracking_system.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/**
 * Запрос на регистрацию заявки возврата/обмена.
 *
 * @param idempotencyKey      идемпотентный ключ для защиты от повторов
 * @param reason              причина оформления возврата
 * @param requestedAt         момент запроса возврата пользователем
 * @param comment             дополнительный комментарий
 * @param reverseTrackNumber  трек-номер обратной отправки, если известен
 * @param isExchange          признак того, что пользователь сразу запрашивает обмен;
 *                             если значение не передано, оно считается равным {@code false}
 */
public record ReturnRegistrationRequest(
        @NotBlank(message = "Идемпотентный ключ обязателен") String idempotencyKey,
        @NotBlank(message = "Причина возврата обязательна") @Size(max = 255, message = "Причина не должна превышать 255 символов") String reason,
        @NotNull(message = "Дата запроса обязательна") @PastOrPresent(message = "Дата запроса не может быть из будущего") OffsetDateTime requestedAt,
        @Size(max = 2000, message = "Комментарий не должен превышать 2000 символов") String comment,
        @Size(max = 64, message = "Трек обратной отправки не должен превышать 64 символа") String reverseTrackNumber,
        @JsonProperty("isExchange") boolean isExchange
) {
}

