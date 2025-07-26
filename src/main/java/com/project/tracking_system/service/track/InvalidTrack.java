package com.project.tracking_system.service.track;

/**
 * Информация о некорректной строке из файла с треками.
 * <p>
 * Хранит номер и причину, по которой строка признана некорректной.
 * </p>
 *
 * @param number исходный номер трека (может быть {@code null})
 * @param reason текстовое описание причины
 */
public record InvalidTrack(String number, String reason) {
}
