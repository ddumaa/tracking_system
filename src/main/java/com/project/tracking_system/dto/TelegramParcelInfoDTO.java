package com.project.tracking_system.dto;

import com.project.tracking_system.entity.GlobalStatus;
import java.time.ZonedDateTime;
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
    private final ZonedDateTime lastUpdate;

    /**
     * Создаёт DTO с основными данными о посылке для Telegram.
     *
     * @param trackNumber трек-номер посылки (может быть пустым)
     * @param storeName   название магазина, где оформлен заказ
     * @param status      глобальный статус посылки, используемый для описания
     * @param lastUpdate  дата последнего обновления статуса
     */
    public TelegramParcelInfoDTO(String trackNumber,
                                 String storeName,
                                 GlobalStatus status,
                                 ZonedDateTime lastUpdate) {
        this.trackNumber = trackNumber;
        this.storeName = storeName;
        this.status = status;
        this.lastUpdate = lastUpdate;
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
     * @return глобальный статус посылки для формирования текстового описания
     */
    public GlobalStatus getStatus() {
        return status;
    }

    /**
     * @return отметка времени последнего обновления статуса посылки
     */
    public ZonedDateTime getLastUpdate() {
        return lastUpdate;
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
                && status == that.status
                && Objects.equals(lastUpdate, that.lastUpdate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(trackNumber, storeName, status, lastUpdate);
    }
}
