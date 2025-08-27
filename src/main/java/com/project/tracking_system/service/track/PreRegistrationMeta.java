package com.project.tracking_system.service.track;

/**
 * Данные для предрегистрации из Excel-файла.
 * <p>
 * Содержит нормализованный (при наличии) номер и идентификатор магазина,
 * по которым {@link com.project.tracking_system.service.registration.PreRegistrationService}
 * создаёт запись с флагом предрегистрации.
 * </p>
 *
 * @param number  нормализованный номер трека, может быть {@code null}
 * @param storeId идентификатор магазина
 */
public record PreRegistrationMeta(String number, Long storeId) {
}
