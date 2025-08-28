package com.project.tracking_system.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты для {@link NameUtils#shortenName(String)}.
 */
class NameUtilsTest {

    /**
     * Проверяет сокращение полного ФИО из трёх частей.
     */
    @Test
    void shouldShortenThreePartRussianName() {
        String result = NameUtils.shortenName("Иванов Иван Иванович");
        assertEquals("Иванов И. И.", result);
    }

    /**
     * Проверяет сокращение ФИО из двух частей.
     */
    @Test
    void shouldShortenTwoPartRussianName() {
        String result = NameUtils.shortenName("Петров Пётр");
        assertEquals("Петров П.", result);
    }

    /**
     * Проверяет, что при одной части строка возвращается без изменений.
     */
    @Test
    void shouldReturnOriginalWhenSinglePartProvided() {
        String result = NameUtils.shortenName("Сидоров");
        assertEquals("Сидоров", result);
    }
}
