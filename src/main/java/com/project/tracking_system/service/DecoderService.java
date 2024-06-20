package com.project.tracking_system.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Service
public class DecoderService {

    @Value("${secret.Password}")
    private String SecretPassword;


    private static byte[] getSHA256Hash(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(input.getBytes(StandardCharsets.UTF_8));
    }

    public String decode(String encryptedText) {
        try {
            // Декодируем Base64 данные
            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedText);

            byte[] keyBytes = getSHA256Hash(SecretPassword);

            // Инициализация шифратора с использованием зашифрованного итерационного ключа
            // IV находится в первых 16 байтах зашифрованного текста, если мы исходили из стандарта
            byte[] ivBytes = new byte[16];
            byte[] cipherBytes = new byte[encryptedBytes.length - 16];
            System.arraycopy(encryptedBytes, 0, ivBytes, 0, 16);
            System.arraycopy(encryptedBytes, 16, cipherBytes, 0, encryptedBytes.length - 16);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, "AES");
            IvParameterSpec ivParameterSpec = new IvParameterSpec(ivBytes);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);

            // Дешифруем данные
            byte[] decryptedBytes = cipher.doFinal(cipherBytes);

            // Возвращаем результат в строку UTF-8
            return new String(decryptedBytes, StandardCharsets.UTF_8);

        } catch (Exception e) {
            throw new RuntimeException("Ошибка декодирования данных", e);
        }
    }
}