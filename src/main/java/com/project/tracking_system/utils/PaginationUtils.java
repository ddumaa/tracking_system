package com.project.tracking_system.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Утилитный класс для расчёта безопасного окна пагинации.
 */
public final class PaginationUtils {

    private PaginationUtils() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Рассчитывает параметры окна пагинации и формирует готовый набор элементов для отображения.
     * <p>
     * Метод нормализует номер страницы, стремится расположить её по центру окна и дополняет список
     * крайними страницами и разделителями при необходимости.
     * </p>
     *
     * @param requestedPage исходно запрошенный индекс страницы
     * @param totalPages    количество доступных страниц
     * @param windowSize    размер окна пагинации
     * @return объект {@link PaginationWindow} с безопасными индексами
     */
    public static PaginationWindow calculateWindow(int requestedPage, int totalPages, int windowSize) {
        if (windowSize <= 0) {
            throw new IllegalArgumentException("Размер окна пагинации должен быть положительным");
        }

        int normalizedTotalPages = Math.max(totalPages, 0);

        if (normalizedTotalPages == 0) {
            // Нет данных — возвращаем пустое окно и фиксируем страницу на нуле
            return new PaginationWindow(0, 0, -1, 0, List.of());
        }

        int safeRequestedPage = Math.max(requestedPage, 0);
        int maxPageIndex = normalizedTotalPages - 1;
        int currentPage = Math.min(safeRequestedPage, maxPageIndex);

        int effectiveWindow = Math.min(windowSize, normalizedTotalPages);
        int leftSlots = effectiveWindow / 2;
        int rightSlots = effectiveWindow - leftSlots - 1;

        int startPage = currentPage - leftSlots;
        int endPage = currentPage + rightSlots;

        if (startPage < 0) {
            endPage = Math.min(endPage - startPage, maxPageIndex);
            startPage = 0;
        }

        if (endPage > maxPageIndex) {
            int shift = endPage - maxPageIndex;
            startPage = Math.max(startPage - shift, 0);
            endPage = maxPageIndex;
        }

        List<PaginationItem> paginationItems = buildPaginationItems(startPage, endPage, normalizedTotalPages);

        return new PaginationWindow(currentPage, startPage, endPage, normalizedTotalPages, paginationItems);
    }

    /**
     * Формирует элементы пагинации с учётом необходимости показывать крайние страницы и разделители.
     *
     * @param startPage  начальный индекс окна
     * @param endPage    конечный индекс окна
     * @param totalPages общее количество страниц
     * @return неизменяемый список элементов пагинации
     */
    private static List<PaginationItem> buildPaginationItems(int startPage, int endPage, int totalPages) {
        if (totalPages == 0 || endPage < startPage) {
            return List.of();
        }

        List<PaginationItem> items = new ArrayList<>();
        int lastIndex = totalPages - 1;

        if (startPage > 0) {
            items.add(PaginationItem.page(0));
            if (startPage > 1) {
                items.add(PaginationItem.ellipsis());
            }
        }

        for (int page = startPage; page <= endPage; page++) {
            items.add(PaginationItem.page(page));
        }

        if (endPage < lastIndex) {
            if (endPage < lastIndex - 1) {
                items.add(PaginationItem.ellipsis());
            }
            items.add(PaginationItem.page(lastIndex));
        }

        return List.copyOf(items);
    }
}
