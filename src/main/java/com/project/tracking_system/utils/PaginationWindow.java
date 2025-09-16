package com.project.tracking_system.utils;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Представляет параметры окна пагинации для вывода ссылок на страницы.
 *
 * @param currentPage фактически используемый индекс текущей страницы (с учётом ограничений)
 * @param startPage   начальный индекс окна пагинации
 * @param endPage     конечный индекс окна пагинации
 */
public record PaginationWindow(int currentPage, int startPage, int endPage) {

    /**
     * Формирует неизменяемый список индексов страниц внутри текущего окна.
     * <p>
     * Если окно некорректно (например, при отсутствии страниц), возвращается пустой список,
     * чтобы слой представления не пытался отрисовать несуществующие номера.
     * </p>
     *
     * @return список индексов страниц в порядке возрастания
     */
    public List<Integer> pageIndexes() {
        if (endPage < startPage) {
            return List.of();
        }
        return IntStream.rangeClosed(startPage, endPage)
                .boxed()
                .toList();
    }
}
