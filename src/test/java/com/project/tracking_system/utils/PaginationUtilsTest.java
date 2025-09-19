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
    void shouldCenterWindowAndShowEllipsis() {
        PaginationWindow window = PaginationUtils.calculateWindow(5, 32, 5);

        assertEquals(5, window.currentPage(), "Текущая страница должна сохраняться после нормализации");
        assertEquals(3, window.startPage(), "Окно должно начинаться на две позиции левее текущей страницы");
        assertEquals(7, window.endPage(), "Окно должно охватывать пять последовательных страниц");
        assertEquals(List.of(
                        PaginationItem.page(0),
                        PaginationItem.ellipsisItem(),
                        PaginationItem.page(3),
                        PaginationItem.page(4),
                        PaginationItem.page(5),
                        PaginationItem.page(6),
                        PaginationItem.page(7),
                        PaginationItem.ellipsisItem(),
                        PaginationItem.page(31)
                ),
                window.paginationItems(),
                "Должны отображаться первая и последняя страницы с разделителями");
    }

    @Test
    void shouldReturnAllPagesWhenTotalLessThanWindow() {
        PaginationWindow window = PaginationUtils.calculateWindow(2, 3, 5);

        assertEquals(2, window.currentPage(), "Текущая страница должна остаться без изменений");
        assertEquals(0, window.startPage(), "Окно должно начинаться с нулевой страницы");
        assertEquals(2, window.endPage(), "Окно должно охватывать все доступные страницы");
        assertEquals(List.of(
                        PaginationItem.page(0),
                        PaginationItem.page(1),
                        PaginationItem.page(2)
                ),
                window.paginationItems(),
                "При малом количестве страниц разделители не нужны");
    }

    @Test
    void shouldClampRequestedPageAboveRange() {
        PaginationWindow window = PaginationUtils.calculateWindow(50, 10, 5);

        assertEquals(9, window.currentPage(), "Номер страницы должен быть ограничен последним доступным значением");
        assertEquals(5, window.startPage(), "Окно должно быть смещено к концу диапазона");
        assertEquals(9, window.endPage(), "Последняя страница должна быть включена в окно");
        assertEquals(List.of(
                        PaginationItem.page(0),
                        PaginationItem.ellipsisItem(),
                        PaginationItem.page(5),
                        PaginationItem.page(6),
                        PaginationItem.page(7),
                        PaginationItem.page(8),
                        PaginationItem.page(9)
                ),
                window.paginationItems(),
                "Список должен содержать первую страницу, разделитель и финальный блок страниц");
    }

    @Test
    void shouldReturnEmptyWindowWhenNoPagesAvailable() {
        PaginationWindow window = PaginationUtils.calculateWindow(3, 0, 5);

        assertEquals(0, window.currentPage(), "Текущая страница фиксируется на нуле при отсутствии данных");
        assertEquals(0, window.startPage(), "Начало окна должно быть нулевым");
        assertEquals(-1, window.endPage(), "Конец окна равен -1, что сигнализирует об отсутствии страниц");
        assertTrue(window.paginationItems().isEmpty(), "При отсутствии данных список элементов должен быть пустым");
    }
}
