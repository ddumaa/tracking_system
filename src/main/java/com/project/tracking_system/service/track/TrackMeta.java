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
public record TrackMeta(String number, Long storeId, String phone, boolean canSave) {
}
