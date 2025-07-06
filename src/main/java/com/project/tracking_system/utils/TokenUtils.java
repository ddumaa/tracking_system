package com.project.tracking_system.utils;

/**
 * Утилиты для работы с токенами.
 */
public final class TokenUtils {

    private TokenUtils() {
    }

    /**
     * Маскирует токен, оставляя первые четыре символа.
     *
     * @param token исходный токен
     * @return маскированный токен или исходная строка, если она короче четырех символов
     */
    public static String maskToken(String token) {
        if (token == null) {
            return null;
        }
        if (token.length() <= 4) {
            return token;
        }
        return token.substring(0, 4) + "***";
    }
}
