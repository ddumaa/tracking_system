package com.project.tracking_system.utils;

/**
 * Утилиты для работы с email.
 */
public final class EmailUtils {

    private EmailUtils() {
    }

    /**
     * Маскирует email, оставляя первые четыре символа и скрывая остальную часть локальной части.
     *
     * @param email исходный email
     * @return маскированный email, либо исходную строку, если email некорректен
     */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }

        String[] parts = email.split("@", 2);
        String local = parts[0];
        String domain = parts[1];
        String visible = local.length() <= 4 ? local : local.substring(0, 4);
        return visible + "***@" + domain;
    }
}
