package com.project.tracking_system.service;

import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class DecoderService {

    public String decode(String encryptedText, String password) {
        try {
            // Декодируем Base64 данные
            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedText);

            // Инициализируем шифратор с использованием зашифрованного итерационного ключа
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec secretKeySpec = new SecretKeySpec(password.getBytes(StandardCharsets.UTF_8), "AES");
            IvParameterSpec ivParameterSpec = new IvParameterSpec(password.getBytes(StandardCharsets.UTF_8));
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);

            // Дешифруем данные
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);

            // Возвращаем результат в строку UTF-8
            return new String(decryptedBytes, StandardCharsets.UTF_8);

        } catch (Exception e) {
            throw new RuntimeException("Ошибка декодирования данных", e);
        }
    }

}

