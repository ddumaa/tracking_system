package com.project.tracking_system.utils;

import lombok.extern.slf4j.Slf4j;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;

/**
 * Утилитный класс для парсинга дат в различных форматах.
 * <p>
 * Поддерживает форматы "dd.MM.yyyy, HH:mm" (Белпочта) и
 * "dd.MM.yyyy HH:mm:ss" (Европочта). Возвращает дату в UTC
 * с учётом часового пояса пользователя.
 * </p>
 */
@Slf4j
public final class DateParserUtils {

    private static final DateTimeFormatter BELPOST_FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("dd.MM.yyyy, ")
            .appendValue(ChronoField.HOUR_OF_DAY)
            .appendLiteral(':')
            .appendPattern("mm")
            .toFormatter();

    private static final DateTimeFormatter EVROPOST_FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("dd.MM.yyyy ")
            .appendValue(ChronoField.HOUR_OF_DAY)
            .appendLiteral(':')
            .appendPattern("mm:ss")
            .toFormatter();

    private DateParserUtils() {
    }

    /**
     * Парсит строковую дату в {@link ZonedDateTime} в зоне UTC.
     *
     * @param rawDate  исходная строка даты
     * @param userZone часовой пояс пользователя
     * @return распарсенная дата в UTC
     * @throws DateTimeParseException если дата не соответствует ожидаемым форматам
     */
    public static ZonedDateTime parse(String rawDate, ZoneId userZone) {
        if (rawDate == null || userZone == null) {
            throw new IllegalArgumentException("Дата и часовой пояс не могут быть null");
        }

        LocalDateTime ldt;
        try {
            ldt = LocalDateTime.parse(rawDate, BELPOST_FORMATTER);
        } catch (DateTimeParseException ex) {
            try {
                ldt = LocalDateTime.parse(rawDate, EVROPOST_FORMATTER);
            } catch (DateTimeParseException e) {
                log.error("Ошибка парсинга даты: {}", rawDate, e);
                throw e;
            }
        }
        return ldt.atZone(userZone).withZoneSameInstant(ZoneOffset.UTC);
    }
}
