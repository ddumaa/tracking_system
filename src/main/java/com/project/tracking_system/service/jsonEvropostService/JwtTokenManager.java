package com.project.tracking_system.service.jsonEvropostService;

import com.project.tracking_system.model.evropost.jsonRequestModel.JsonPacket;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Сервис для управления JWT токенами в системе.
 * Этот класс генерирует, обновляет и управляет сроком действия JWT токена.
 * Также поддерживает обновление токена по расписанию.
 * <p>
 * Основные функции:
 * - Генерация токена при старте приложения.
 * - Обновление токена в случае его истечения.
 * - Планирование обновления токена по расписанию (в полночь).
 * </p>
 * Взаимодействует с сервисами:
 * - {@link GetJwtTokenService} для получения нового JWT токена.
 * - {@link JsonPacket} для хранения токена.
 *
 * @author Dmitriy Anisimov
 * @date 08.01.2025
 */
@Service
public class JwtTokenManager {

    private final GetJwtTokenService jwtTokenService;
    private final JsonPacket jsonPacket;
    private LocalDateTime tokenExpiryTime;

    @Autowired
    public JwtTokenManager(GetJwtTokenService jwtTokenService, JsonPacket jsonPacket) {
        this.jwtTokenService = jwtTokenService;
        this.jsonPacket = jsonPacket;
    }

    /**
     * Метод, вызываемый после создания бина, для инициализации токена.
     * Генерирует токен при старте приложения.
     *
     * @throws RuntimeException если не удалось инициализировать токен.
     */
    @PostConstruct
    private void initializeJwtToken() {
        try {
            refreshToken();
        } catch (Exception e) {
            throw new RuntimeException("Не удалось инициализировать JWT токен при старте приложения", e);
        }
    }

    /**
     * Метод для обновления JWT токена.
     * Обновляет токен, если он истёк.
     * Метод синхронизирован для предотвращения одновременных обновлений токена.
     *
     * @throws RuntimeException если не удалось обновить токен.
     */
    private synchronized void refreshToken() {
        if (isTokenExpired()) {
            try {
                jsonPacket.setJwt(jwtTokenService.getJwtToken());
                tokenExpiryTime = LocalDateTime.now().plusDays(30);
            } catch (Exception e) {
                throw new RuntimeException("Не удалось обновить JWT токен", e);
            }
        }
    }

    /**
     * Проверяет, истёк ли срок действия токена.
     *
     * @return {@code true}, если токен истёк, иначе {@code false}.
     */
    private boolean isTokenExpired() {
        return tokenExpiryTime == null || LocalDateTime.now().isAfter(tokenExpiryTime);
    }

    /**
     * Метод для планового обновления токена.
     * Этот метод вызывается автоматически каждый день в полночь.
     *
     * @see Scheduled
     */
    @Scheduled(cron = "0 0 0 * * ?") // Обновление токена в полночь
    public void scheduledTokenRefresh() {
        refreshToken();
    }
}