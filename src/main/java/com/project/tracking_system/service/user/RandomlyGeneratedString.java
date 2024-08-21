package com.project.tracking_system.service.user;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
public class RandomlyGeneratedString {

    String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    int length = 10;

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