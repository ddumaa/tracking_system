package com.project.tracking_system.service.track;

/**
 * Метаданные трек-номера, полученные при парсинге файла.
 * <p>
 * Содержат номер посылки, магазин, телефон получателя и признак,
 * можно ли сохранять этот трек для пользователя.
 * </p>
 *
 * @param number  номер трека
 * @param storeId идентификатор магазина (может быть {@code null})
 * @param phone   телефон получателя (может быть {@code null})
 * @param canSave разрешено ли сохранять трек
 */
import com.project.tracking_system.entity.PostalServiceType;

/**
 * Metadata for a track number used during batch processing.
 * <p>
 * Contains tracking number, store identifier, customer phone and flag whether
 * the track may be saved. Postal service type may be provided directly from the
 * database to skip additional detection.
 * </p>
 *
 * @param number            track number
 * @param storeId           store identifier (may be {@code null})
 * @param phone             customer phone number (may be {@code null})
 * @param canSave           whether the track can be persisted
 * @param postalServiceType type of postal service; may be {@code null} if unknown
 */
public record TrackMeta(String number,
                        Long storeId,
                        String phone,
                        boolean canSave,
                        PostalServiceType postalServiceType) {

    /**
     * Convenience constructor when postal service is not specified.
     */
    public TrackMeta(String number, Long storeId, String phone, boolean canSave) {
        this(number, storeId, phone, canSave, null);
    }
}
