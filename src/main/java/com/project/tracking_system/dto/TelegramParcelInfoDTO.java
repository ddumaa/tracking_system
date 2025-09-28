package com.project.tracking_system.dto;

import com.project.tracking_system.entity.GlobalStatus;
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
    private final GlobalStatus status;
    /**
     * Создаёт DTO с основными данными о посылке для Telegram.
     *
     * @param trackNumber трек-номер посылки (может быть пустым)
     * @param storeName   название магазина, где оформлен заказ
     */
    public TelegramParcelInfoDTO(String trackNumber,
                                 String storeName) {
        this(trackNumber, storeName, null);
    }

    /**
     * Создаёт DTO с полным набором данных о посылке для Telegram.
     *
     * @param trackNumber трек-номер посылки (может быть пустым)
     * @param storeName   название магазина, где оформлен заказ
     * @param status      актуальный глобальный статус посылки
     */
    public TelegramParcelInfoDTO(String trackNumber,
                                 String storeName,
                                 GlobalStatus status) {
        this.trackNumber = trackNumber;
        this.storeName = storeName;
        this.status = status;
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

    /**
     * @return глобальный статус посылки или {@code null}, если он неизвестен
     */
    public GlobalStatus getStatus() {
        return status;
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
                && Objects.equals(storeName, that.storeName)
                && status == that.status;
    }

    @Override
    public int hashCode() {
        return Objects.hash(trackNumber, storeName, status);
    }
}
