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

    String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    int length = 10;

    /**
     * Генерирует случайную строку для подтверждения регистрации.
     * <p>
     * Строка состоит из случайных символов (буквы и цифры) длиной {@link #length}.
     * </p>
     *
     * @return случайно сгенерированная строка
     */
    public String generateConfirmCodRegistration() {
        SecureRandom secureRandom = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = secureRandom.nextInt(characters.length());
            sb.append(characters.charAt(index));
        }
        return sb.toString();
    }
}
