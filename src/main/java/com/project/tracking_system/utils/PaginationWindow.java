package com.project.tracking_system.utils;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Представляет параметры окна пагинации для вывода ссылок на страницы.
 *
 * @param currentPage     фактически используемый индекс текущей страницы (с учётом ограничений)
 * @param startPage       начальный индекс окна пагинации
 * @param endPage         конечный индекс окна пагинации
 * @param totalPages      общее количество доступных страниц
 * @param paginationItems список элементов пагинации с учётом крайних страниц и разделителей
 */
public record PaginationWindow(int currentPage,
                               int startPage,
                               int endPage,
                               int totalPages,
                               List<PaginationItem> paginationItems) {

    /**
     * Канонический конструктор, делающий список элементов неизменяемым и проверяющий корректность входных данных.
     *
     * @param currentPage     фактически используемый индекс текущей страницы
     * @param startPage       начальный индекс окна
     * @param endPage         конечный индекс окна
     * @param totalPages      общее количество доступных страниц
     * @param paginationItems элементы для отрисовки пагинации
     */
    public PaginationWindow {
        if (totalPages < 0) {
            throw new IllegalArgumentException("Количество страниц не может быть отрицательным");
        }
        paginationItems = paginationItems == null ? List.of() : List.copyOf(paginationItems);
    }

    /**
     * Формирует неизменяемый список индексов страниц внутри текущего окна (без учёта разделителей).
     *
     * @return список индексов страниц в порядке возрастания
     */
    public List<Integer> windowIndexes() {
        if (endPage < startPage) {
            return List.of();
        }
        return IntStream.rangeClosed(startPage, endPage)
                .boxed()
                .toList();
    }
}
