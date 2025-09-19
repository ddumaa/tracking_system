package com.project.tracking_system.utils;

/**
 * Элемент постраничной навигации, описывающий одну ссылку или разделитель «…».
 * <p>
 * Используется для построения человеко-понятного списка страниц, где часть номеров
 * может быть скрыта с помощью разделителя.
 * </p>
 *
 * @param pageIndex индекс страницы (начинается с нуля), если элемент представляет страницу
 * @param ellipsis  флаг, сигнализирующий, что элемент является разделителем
 */
public record PaginationItem(Integer pageIndex, boolean ellipsis) {

    /**
     * Создаёт элемент, представляющий конкретную страницу.
     * Метод проверяет корректность индекса и гарантирует отсутствие отрицательных значений.
     *
     * @param pageIndex индекс страницы, на которую ведёт ссылка
     * @return настроенный элемент пагинации
     */
    public static PaginationItem page(int pageIndex) {
        if (pageIndex < 0) {
            throw new IllegalArgumentException("Индекс страницы не может быть отрицательным");
        }
        return new PaginationItem(pageIndex, false);
    }

    /**
     * Создаёт элемент разделителя «…», используемый при скрытии диапазона страниц.
     *
     * @return элемент, обозначающий разрыв между страницами
     */
    public static PaginationItem ellipsis() {
        return new PaginationItem(null, true);
    }

    /**
     * Позволяет быстро определить, является ли элемент ссылкой на страницу.
     *
     * @return {@code true}, если элемент представляет страницу, иначе {@code false}
     */
    public boolean isPage() {
        return !ellipsis;
    }

    /**
     * Канонический конструктор, обеспечивающий корректность комбинации параметров записи.
     *
     * @param pageIndex индекс страницы, если элемент отображает ссылку
     * @param ellipsis  флаг, указывающий на использование разделителя
     */
    public PaginationItem {
        if (ellipsis && pageIndex != null) {
            throw new IllegalArgumentException("Разделитель не может содержать индекс страницы");
        }
        if (!ellipsis && pageIndex == null) {
            throw new IllegalArgumentException("Для ссылки на страницу индекс обязателен");
        }
    }
}
