package com.project.tracking_system.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

/**
 * Запрос на запуск обменной посылки, позволяющий выбрать предрегистрацию.
 *
 * @param exchangeTrackNumber трек обменной отправки, если посылка создаётся с номером
 * @param preRegistered       признак создания обменной посылки в статусе предрегистрации
 */
public record ExchangeApprovalRequest(
        @Size(max = 64, message = "Трек обменной отправки не должен превышать 64 символа") String exchangeTrackNumber,
        boolean preRegistered
) {

    /**
     * Проверяет, что трек-номер указан, если предрегистрация не выбрана.
     * В противном случае посылка может быть создана без трека.
     *
     * @return {@code true}, если соблюдены условия запроса
     */
    @AssertTrue(message = "Укажите трек обменной отправки или включите предрегистрацию")
    public boolean isTrackProvidedWhenRequired() {
        return preRegistered || (exchangeTrackNumber != null && !exchangeTrackNumber.isBlank());
    }
}
