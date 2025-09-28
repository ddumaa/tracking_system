package com.project.tracking_system.dto;

import java.util.Objects;

/**
 * Информация о посылке для отображения покупателю в Telegram.
 * <p>
 * DTO содержит минимальные данные, необходимые для формирования
 * текста в разделе «Мои посылки».
 * </p>
 */
public class TelegramParcelInfoDTO {

    private final String trackNumber;
    private final String storeName;

    /**
     * Создаёт DTO с основными данными о посылке.
     *
     * @param trackNumber трек-номер посылки (может быть пустым)
     * @param storeName   название магазина, где оформлен заказ
     */
    public TelegramParcelInfoDTO(String trackNumber, String storeName) {
        this.trackNumber = trackNumber;
        this.storeName = storeName;
    }

    /**
     * @return трек-номер посылки в удобном для отображения виде
     */
    public String getTrackNumber() {
        return trackNumber;
    }

    /**
     * @return название магазина, где оформлен заказ
     */
    public String getStoreName() {
        return storeName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TelegramParcelInfoDTO that)) {
            return false;
        }
        return Objects.equals(trackNumber, that.trackNumber)
                && Objects.equals(storeName, that.storeName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(trackNumber, storeName);
    }
}
