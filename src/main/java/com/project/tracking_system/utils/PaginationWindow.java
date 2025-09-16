package com.project.tracking_system.utils;

/**
 * Представляет параметры окна пагинации для вывода ссылок на страницы.
 *
 * @param currentPage фактически используемый индекс текущей страницы (с учётом ограничений)
 * @param startPage   начальный индекс окна пагинации
 * @param endPage     конечный индекс окна пагинации
 */
public record PaginationWindow(int currentPage, int startPage, int endPage) {
}
