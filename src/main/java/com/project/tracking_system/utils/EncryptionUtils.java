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

    public EncryptionUtils(@Value("${encryption.key}") String key) {
        if (key == null || key.length() !=16) {
            throw new IllegalArgumentException("Ключ шифрования должен быть длиной 16 символов.");
        }
        this.secretKeySpec = new SecretKeySpec(key.getBytes(), ALGORITHM);
    }

    /**
     * Шифрует переданные данные алгоритмом AES.
     *
     * @param data исходная строка
     * @return зашифрованная строка в формате Base64
     * @throws Exception если произошла ошибка при шифровании
     */
    public String encrypt(String data) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
        byte[] encryptedData = cipher.doFinal(data.getBytes());
        return Base64.getEncoder().encodeToString(encryptedData);
    }

    /**
     * Расшифровывает строку, полученную после {@link #encrypt(String)}.
     *
     * @param encryptedData зашифрованная строка в формате Base64
     * @return исходная строка или пустая строка, если входные данные пусты
     * @throws Exception если произошла ошибка при расшифровке
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