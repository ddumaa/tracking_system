package com.project.tracking_system.service.track;

/**
 * Сырые данные строки XLS-файла.
 * <p>
 * Используется для хранения значений ячеек без предварительной обработки.
 * </p>
 *
 * @param number номер трека
 * @param store  значение ячейки магазина
 * @param phone  значение ячейки телефона
 */
public record TrackExcelRow(String number, String store, String phone) {
}
