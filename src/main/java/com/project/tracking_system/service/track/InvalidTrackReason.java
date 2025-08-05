package com.project.tracking_system.service.track;

/**
 * Причины некорректности строки с треком.
 * <p>
 * Используются для передачи кода ошибки от сервиса валидации
 * на клиентскую сторону.
 * </p>
 */
public enum InvalidTrackReason {
    /** В исходной строке отсутствует номер трека. */
    EMPTY_NUMBER("Пустой номер"),
    /** Номер не соответствует требуемому формату. */
    WRONG_FORMAT("Некорректный формат"),
    /** Такой номер уже встречался в загружаемом файле. */
    DUPLICATE("Дубликат");

    /** Человекочитаемое сообщение. */
    private final String text;

    InvalidTrackReason(String text) {
        this.text = text;
    }

    /**
     * Возвращает текстовое описание причины.
     *
     * @return локализованная строка
     */
    public String getText() {
        return text;
    }

}