package com.project.tracking_system.service.user;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;

/**
 * Сервис для генерации случайных строк.
 * <p>
 * Этот сервис генерирует случайные строки заданной длины, состоящие из букв латинского алфавита (в обоих регистрах) и цифр.
 * Используется для создания кодов подтверждения, токенов и других случайных значений.
 * </p>
 *
 * @author Dmitriy Anisimov
 * @date Добавленно 07.01.2025
 */
@Service
public class RandomlyGeneratedString {

    /** Доступные символы для генерации случайной строки */
    private static final String CHARACTERS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    /** Длина случайной строки */
    private static final int LENGTH = 10;

    /**
     * Генерирует код подтверждения.
     * <p>
     * Строка состоит из случайных символов (буквы и цифры) длиной {@link #LENGTH}.
     * </p>
     *
     * @return случайно сгенерированная строка
     */
    public String generateConfirmationCode() {
        SecureRandom secureRandom = new SecureRandom();
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            int index = secureRandom.nextInt(CHARACTERS.length());
            sb.append(CHARACTERS.charAt(index));
        }
        return sb.toString();
    }
}
