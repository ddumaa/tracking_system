package com.project.tracking_system.utils;

/**
 * Утилиты для нормализации телефонных номеров.
 */
public final class PhoneUtils {

    private PhoneUtils() {
    }

    /**
     * Нормализует белорусский номер телефона до формата 375XXXXXXXXX.
     *
     * @param input исходный номер телефона
     * @return нормализованный номер без знаков и пробелов
     * @throws IllegalArgumentException если номер имеет некорректный формат
     */
    public static String normalizePhone(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Номер телефона не может быть пустым");
        }

        // Удаляем все символы кроме цифр и плюса
        String digits = input.replaceAll("[^\\d+]", "");

        // Убираем ведущий плюс, если он есть
        if (digits.startsWith("+")) {
            digits = digits.substring(1);
        }

        // Обработка различных местных форматов
        if (digits.matches("375\\d{9}")) {
            return digits;
        }
        if (digits.matches("80\\d{9}")) {
            return "375" + digits.substring(2);
        }
        if (digits.matches("0\\d{9}")) {
            return "375" + digits.substring(1);
        }
        if (digits.matches("\\d{9}")) {
            return "375" + digits;
        }

        throw new IllegalArgumentException("Некорректный формат номера телефона");
    }

    /**
     * Маскирует номер телефона, скрывая последние четыре цифры.
     * <p>
     * Например, из {@code 375291234567} получится {@code 37529123***}.
     * </p>
     *
     * @param phone номер телефона в произвольном формате
     * @return маскированный номер или исходная строка, если номер слишком короткий
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() <= 4) {
            return phone;
        }
        return phone.substring(0, phone.length() - 4) + "***";
    }
}
