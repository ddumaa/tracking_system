package com.project.tracking_system.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * @author Dmitriy Anisimov
 * @date 26.01.2025
 */
@Component
public class EncryptionUtils {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";
    private final SecretKeySpec secretKeySpec;

    /**
     * Создает экземпляр утилиты шифрования с указанным ключом.
     *
     * @param key ключ длиной 16 символов для инициализации алгоритма AES
     * @throws IllegalArgumentException если длина ключа не равна 16 символам
     */
    public EncryptionUtils(@Value("${encryption.key}") String key) {
        if (key == null || key.length() !=16) {
            throw new IllegalArgumentException("Ключ шифрования должен быть длиной 16 символов.");
        }
        this.secretKeySpec = new SecretKeySpec(key.getBytes(), ALGORITHM);
    }

    /**
     * Шифрует переданную строку.
     *
     * @param data исходные данные для шифрования
     * @return зашифрованная строка в формате Base64
     * @throws Exception если возникает ошибка при шифровании
     */
    public String encrypt(String data) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
        byte[] encryptedData = cipher.doFinal(data.getBytes());
        return Base64.getEncoder().encodeToString(encryptedData);
    }

    /**
     * Расшифровывает указанную строку.
     *
     * @param encryptedData строка, зашифрованная методом {@link #encrypt(String)}
     * @return исходная строка или пустая строка, если {@code encryptedData} пустая
     * @throws Exception если возникает ошибка при расшифровке
     */
    public String decrypt(String encryptedData) throws Exception {
        if (encryptedData == null || encryptedData.isBlank()) {
            return "";
        }
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
        byte[] decodedData = Base64.getDecoder().decode(encryptedData);
        return new String(cipher.doFinal(decodedData));
    }

}