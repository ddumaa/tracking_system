package com.project.tracking_system.utils;

import java.time.Duration;

/**
 * Утилитные методы для работы с {@link Duration} и её форматирования.
 */
public final class DurationUtils {

    private DurationUtils() {
    }

    /**
     * Преобразует длительность в строку формата <code>MM:SS</code>.
     *
     * @param duration продолжительность
     * @return строка формата MM:SS, либо {@code "0:00"}, если значение {@code null}
     */
    public static String formatMinutesSeconds(Duration duration) {
        if (duration == null) {
            return "0:00";
        }
        long minutes = duration.toMinutes();
        int seconds = duration.toSecondsPart();
        return String.format("%d:%02d", minutes, seconds);
    }

}