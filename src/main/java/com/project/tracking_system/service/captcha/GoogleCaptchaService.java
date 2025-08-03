package com.project.tracking_system.service.captcha;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * Реализация {@link CaptchaService}, использующая Google reCAPTCHA.
 * <p>
 * Сервис отправляет запрос к удалённому API для проверки полученного токена,
 * тем самым инкапсулируя логику взаимодействия с внешним сервисом.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleCaptchaService implements CaptchaService {

    /** HTTP-клиент для запросов к API Google. */
    private final RestTemplate restTemplate;

    /** Секретный ключ приложения. */
    @Value("${recaptcha.secret-key}")
    private String secretKey;

    /** Публичный ключ сайта для отображения виджета. */
    @Value("${recaptcha.site-key}")
    private String siteKey;

    /** URL проверки токена reCAPTCHA. */
    @Value("${recaptcha.verify-url:https://www.google.com/recaptcha/api/siteverify}")
    private String verifyUrl;

    @Override
    public boolean verifyToken(String token, String ip) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        try {
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("secret", secretKey);
            body.add("response", token);
            if (StringUtils.hasText(ip)) {
                body.add("remoteip", ip);
            }
            CaptchaResponse response = restTemplate.postForObject(verifyUrl, body, CaptchaResponse.class);
            return response != null && Boolean.TRUE.equals(response.success);
        } catch (Exception ex) {
            log.warn("Не удалось проверить капчу", ex);
            return false;
        }
    }

    @Override
    public String getSiteKey() {
        return siteKey;
    }

    /**
     * Модель ответа от сервиса Google reCAPTCHA.
     */
    private record CaptchaResponse(Boolean success) {
    }
}
