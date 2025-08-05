package com.project.tracking_system.service.track;

import com.project.tracking_system.entity.PostalServiceType;

/**
 * Метаданные трек-номера, используемые при пакетной обработке.
 * <p>
 * Хранят номер посылки, идентификатор магазина, телефон клиента,
 * признак возможности сохранения и тип почтовой службы. Тип службы
 * может быть передан напрямую из базы данных, что позволяет
 * пропустить детектирование.
 * </p>
 *
 * @param number            номер трека
 * @param storeId           идентификатор магазина (может быть {@code null})
 * @param phone             телефон получателя (может быть {@code null})
 * @param canSave           разрешено ли сохранять трек
 * @param postalServiceType тип почтовой службы; может быть {@code null}
 */
public record TrackMeta(String number,
                        Long storeId,
                        String phone,
                        boolean canSave,
                        PostalServiceType postalServiceType) {

    /**
     * Удобный конструктор, если тип почтовой службы не известен.
     */
    public TrackMeta(String number, Long storeId, String phone, boolean canSave) {
        this(number, storeId, phone, canSave, null);
    }

}