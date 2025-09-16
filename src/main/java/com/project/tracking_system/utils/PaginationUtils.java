package com.project.tracking_system.utils;

/**
 * Утилитный класс для расчёта безопасного окна пагинации.
 */
public final class PaginationUtils {

    private PaginationUtils() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Рассчитывает параметры окна пагинации, гарантируя что индексы страниц останутся в допустимых пределах.
     * <p>
     * Метод фиксирует некорректные значения текущей страницы (например, отрицательные или выходящие за количество страниц),
     * чтобы исключить исключения при генерации диапазона ссылок.
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
        int safeRequestedPage = Math.max(requestedPage, 0);

        int maxPageIndex = normalizedTotalPages > 0 ? normalizedTotalPages - 1 : 0;
        int currentPage = Math.min(safeRequestedPage, maxPageIndex);

        int startPage = normalizedTotalPages > 0 ? (currentPage / windowSize) * windowSize : 0;
        int endPage = normalizedTotalPages > 0 ? Math.min(startPage + windowSize - 1, maxPageIndex) : 0;

        return new PaginationWindow(currentPage, startPage, endPage);
    }
}
