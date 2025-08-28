package com.project.tracking_system.utils;

/**
 * Утилиты для работы с ФИО.
 * <p>
 * Предоставляет методы для маскирования персональных данных.
 * </p>
 */
public final class NameUtils {

    private NameUtils() {
    }

    /**
     * Маскирует ФИО, оставляя только первую букву каждого слова.
     * Остальные символы заменяются на символ '*'.
     *
     * @param name исходное ФИО
     * @return маскированное ФИО либо {@code null}, если входные данные пусты
     */
    public static String maskName(String name) {
        if (name == null || name.isBlank()) {
            return name;
        }
        String[] parts = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            sb.append(part.charAt(0));
            if (part.length() > 1) {
                sb.append("*".repeat(part.length() - 1));
            }
            if (i < parts.length - 1) {
                sb.append(' ');
            }
        }
        return sb.toString();
    }

    /**
     * Сокращает ФИО, оставляя фамилию полностью и инициалы имени и отчества.
     * <p>
     * Если строка содержит менее двух частей, она возвращается без изменений.
     * </p>
     *
     * @param fullName исходное полное имя
     * @return сокращённая запись либо исходная строка при недостатке данных
     */
    public static String shortenName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return fullName;
        }
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length < 2) {
            return fullName;
        }
        StringBuilder result = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            result.append(' ').append(part.charAt(0)).append('.');
        }
        return result.toString();
    }
}
