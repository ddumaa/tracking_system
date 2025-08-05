package com.project.tracking_system.service.track;

/**
 * Информация о некорректной строке из файла с треками.
 * <p>
 * Хранит исходный номер и причину, по которой строка
 * не была допущена к дальнейшей обработке. Используется для
 * отображения пользователю списка ошибок.
 * </p>
 *
 * @param number исходный номер трека (может быть {@code null})
 * @param reason код причины некорректности
 */
public record InvalidTrack(String number, InvalidTrackReason reason) {
}