package com.project.tracking_system.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Набор модульных тестов для {@link PaginationUtils}, проверяющих корректность нормализации индекса страницы
 * и формирования окна пагинации при различных входных данных.
 */
class PaginationUtilsTest {

    @Test
    void shouldClampRequestedPageAboveRange() {
        PaginationWindow window = PaginationUtils.calculateWindow(10, 6, 6);

        assertEquals(5, window.currentPage(), "Индекс страницы должен быть ограничен последней доступной страницей");
        assertEquals(0, window.startPage(), "Окно должно начинаться с первой страницы блока");
        assertEquals(5, window.endPage(), "Окно должно заканчиваться последней страницей блока");
        assertEquals(List.of(0, 1, 2, 3, 4, 5), window.pageIndexes(), "Список индексов должен содержать последовательность страниц окна");
    }

    @Test
    void shouldNormalizeNegativeRequestedPage() {
        PaginationWindow window = PaginationUtils.calculateWindow(-3, 5, 6);

        assertEquals(0, window.currentPage(), "Отрицательные индексы переводятся на первую страницу");
        assertEquals(0, window.startPage(), "Окно должно начинаться с нулевой страницы");
        assertEquals(4, window.endPage(), "Окно должно охватывать все доступные страницы");
        assertEquals(List.of(0, 1, 2, 3, 4), window.pageIndexes(), "Должны выводиться все доступные страницы");
    }

    @Test
    void shouldReturnEmptyWindowWhenNoPagesAvailable() {
        PaginationWindow window = PaginationUtils.calculateWindow(3, 0, 6);

        assertEquals(0, window.currentPage(), "Текущая страница фиксируется на нуле при отсутствии данных");
        assertEquals(0, window.startPage(), "Начало окна должно быть нулевым");
        assertEquals(-1, window.endPage(), "Конец окна равен -1, что сигнализирует об отсутствии страниц");
        assertTrue(window.pageIndexes().isEmpty(), "При отсутствии данных список индексов должен быть пустым");
    }

    @Test
    void shouldProduceWindowForLastBlock() {
        PaginationWindow window = PaginationUtils.calculateWindow(11, 12, 6);

        assertEquals(11, window.currentPage(), "Должен использоваться последний индекс страницы");
        assertEquals(6, window.startPage(), "Окно должно начинаться с начала последнего блока");
        assertEquals(11, window.endPage(), "Окно должно заканчиваться последней доступной страницей");
        assertEquals(List.of(6, 7, 8, 9, 10, 11), window.pageIndexes(), "Список индексов должен соответствовать последнему окну");
    }
}
