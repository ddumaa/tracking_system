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

    private final Long parcelId;
    private final String trackNumber;
    private final String storeName;
    private final GlobalStatus status;
    private final boolean activeReturnRequest;
    /**
     * Создаёт DTO с основными данными о посылке для Telegram.
     *
     * @param trackNumber трек-номер посылки (может быть пустым)
     * @param storeName   название магазина, где оформлен заказ
     */
    public TelegramParcelInfoDTO(String trackNumber,
                                 String storeName) {
        this(null, trackNumber, storeName, null, false);
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
        this(null, trackNumber, storeName, status, false);
    }

    /**
     * Создаёт DTO с идентификатором посылки и признаком активной заявки на возврат.
     *
     * @param parcelId             идентификатор посылки
     * @param trackNumber          трек-номер посылки
     * @param storeName            название магазина
     * @param status               актуальный глобальный статус
     * @param activeReturnRequest  признак наличия активной заявки на возврат/обмен
     */
    public TelegramParcelInfoDTO(Long parcelId,
                                 String trackNumber,
                                 String storeName,
                                 GlobalStatus status,
                                 boolean activeReturnRequest) {
        this.parcelId = parcelId;
        this.trackNumber = trackNumber;
        this.storeName = storeName;
        this.status = status;
        this.activeReturnRequest = activeReturnRequest;
    }

    /**
     * @return идентификатор посылки в базе данных
     */
    public Long getParcelId() {
        return parcelId;
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

    /**
     * @return {@code true}, если по посылке уже зарегистрирована активная заявка
     */
    public boolean hasActiveReturnRequest() {
        return activeReturnRequest;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TelegramParcelInfoDTO that)) {
            return false;
        }
        return Objects.equals(parcelId, that.parcelId)
                && Objects.equals(trackNumber, that.trackNumber)
                && Objects.equals(storeName, that.storeName)
                && status == that.status
                && activeReturnRequest == that.activeReturnRequest;
    }

    @Override
    public int hashCode() {
        return Objects.hash(parcelId, trackNumber, storeName, status, activeReturnRequest);
    }
}
