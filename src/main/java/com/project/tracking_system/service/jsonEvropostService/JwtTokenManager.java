package com.project.tracking_system.service.jsonEvropostService;

import com.project.tracking_system.entity.User;
import com.project.tracking_system.model.evropost.jsonRequestModel.JsonPacket;
import com.project.tracking_system.repository.UserRepository;
import com.project.tracking_system.utils.EncryptionUtils;
import com.project.tracking_system.utils.EmailUtils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

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
@Slf4j
@Service
public class JwtTokenManager {

    private final EncryptionUtils encryptionUtils;
    private final GetJwtTokenService getJwtTokenService;
    private final UserRepository userRepository;
    private ZonedDateTime systemTokenExpiryTime;
    private String systemToken;

    private static final long TOKEN_LIFETIME_DAYS = 29;

    public JwtTokenManager(EncryptionUtils encryptionUtils,
                           GetJwtTokenService getJwtTokenService,
                           UserRepository userRepository) {
        this.encryptionUtils = encryptionUtils;
        this.getJwtTokenService = getJwtTokenService;
        this.userRepository = userRepository;
    }


    /**
     * Метод, вызываемый после создания бина, для инициализации токена.
     * Генерирует токен при старте приложения.
     *
     */
    @PostConstruct
    private void initializeSystemToken() {
        log.info("Инициализация системного JWT токена при старте приложения.");
        try {
            refreshSystemTokenIfExpired();
            log.info("Системный JWT токен успешно инициализирован.");
        } catch (Exception e) {
            log.error("Ошибка при инициализации системного JWT токена: {}", e.getMessage());
        }
    }

    /**
     * Метод для обновления JWT токена.
     * Обновляет токен, если он истёк.
     * Метод синхронизирован для предотвращения одновременных обновлений токена.
     *
     * @throws IllegalStateException если не удалось обновить токен.
     */
    private synchronized void refreshSystemTokenIfExpired() {
        if (isSystemTokenExpired()) {
            try {
                String newSystemToken = getJwtTokenService.getSystemTokenFromApi();
                this.systemToken = newSystemToken;
                this.systemTokenExpiryTime = ZonedDateTime.now(ZoneOffset.UTC).plusDays(TOKEN_LIFETIME_DAYS);
                log.info("Системный токен успешно обновлён. Новый срок действия: {}", systemTokenExpiryTime);
            } catch (Exception e) {
                log.error("Ошибка при обновлении системного токена: {}", e.getMessage());
                throw new IllegalStateException("Не удалось обновить системный токен.", e);
            }
        }
    }

    /**
     * Проверяет, истёк ли срок действия токена.
     *
     * @return {@code true}, если токен истёк, иначе {@code false}.
     */
    private boolean isSystemTokenExpired() {
        boolean expired = systemTokenExpiryTime == null || ZonedDateTime.now(ZoneOffset.UTC).isAfter(systemTokenExpiryTime);
        if (expired) {
            log.debug("Системный токен истёк. Требуется обновление.");
        }
        return expired;
    }

    /**
     * Получение системного токена
     */
    public synchronized String getSystemToken() {
        log.debug("Запрос системного токена.");
        if (isSystemTokenExpired()) {
            refreshSystemTokenIfExpired();
        }
        log.debug("Системный токен успешно возвращён.");
        return systemToken;
    }

    /**
     * Возвращает JWT токен пользователя, при необходимости обновляя его.
     *
     * @param user пользователь, для которого запрашивается токен
     * @return актуальный JWT токен
     */
    public synchronized String getUserToken(User user) {
        log.info("Получение пользовательского токена для пользователя: {}",
                EmailUtils.maskEmail(user.getEmail()));
        if (user.getEvropostServiceCredential().getJwtToken() == null || isUserTokenExpired(user)) {
            try {
                log.debug("JWT токен отсутствует или истёк. Генерация нового токена...");
                String decryptedPassword = encryptionUtils.decrypt(user.getEvropostServiceCredential().getPassword());
                String decryptedServiceNumber = encryptionUtils.decrypt(user.getEvropostServiceCredential().getServiceNumber());

                String newToken = getJwtTokenService.getUserTokenFromApi(
                        user.getEvropostServiceCredential().getUsername(),
                        decryptedPassword,
                        decryptedServiceNumber
                );

                user.getEvropostServiceCredential().setJwtToken(newToken);
                user.getEvropostServiceCredential().setTokenCreatedAt(ZonedDateTime.now(ZoneOffset.UTC));
                userRepository.save(user);
                log.info("Новый пользовательский токен успешно создан и сохранён для пользователя: {}",
                        EmailUtils.maskEmail(user.getEmail()));
            } catch (Exception e) {
                log.error("Не удалось получить пользовательский JWT токен для пользователя: {}. Причина: {}",
                        EmailUtils.maskEmail(user.getEmail()), e.getMessage());
                return null;
            }
        }
        log.debug("Действующий JWT токен найден для пользователя: {}",
                EmailUtils.maskEmail(user.getEmail()));
        return user.getEvropostServiceCredential().getJwtToken();
    }

    /**
     * Проверяет срок действия пользовательского токена.
     *
     * @param user пользователь, чей токен проверяется
     * @return {@code true}, если токен истёк
     */
    private boolean isUserTokenExpired(User user) {
        ZonedDateTime createdAt = user.getEvropostServiceCredential().getTokenCreatedAt();
        boolean expired = createdAt == null ||
                createdAt.plusDays(TOKEN_LIFETIME_DAYS).isBefore(ZonedDateTime.now(ZoneOffset.UTC));
        if (expired) {
            log.debug("Пользовательский токен для {} истёк.",
                    EmailUtils.maskEmail(user.getEmail()));
        }
        return expired;
    }

    /**
     * Метод для планового обновления токена.
     * Этот метод вызывается автоматически каждый день в полночь.
     *
     * @see Scheduled
     */
    @Transactional
    public void scheduledTokenRefresh() {
        log.info("Запуск планового обновления токенов в полночь.");
        try {
            refreshSystemTokenIfExpired();

            java.time.ZonedDateTime threshold = java.time.ZonedDateTime
                    .now(java.time.ZoneOffset.UTC)
                    .minusDays(TOKEN_LIFETIME_DAYS);
            List<User> users = userRepository.findUsersForTokenRefresh(threshold);

            for (User user : users) {
                log.info("Обновление токена для пользователя: {}",
                        EmailUtils.maskEmail(user.getEmail()));
                try {
                    String decryptedPassword = encryptionUtils.decrypt(user.getEvropostServiceCredential().getPassword());
                    String decryptedServiceNumber = encryptionUtils.decrypt(user.getEvropostServiceCredential().getServiceNumber());

                    String newToken = getJwtTokenService.getUserTokenFromApi(
                            user.getEvropostServiceCredential().getUsername(),
                            decryptedPassword,
                            decryptedServiceNumber
                    );
                    user.getEvropostServiceCredential().setJwtToken(newToken);
                    user.getEvropostServiceCredential().setTokenCreatedAt(ZonedDateTime.now(ZoneOffset.UTC));
                    userRepository.save(user);
                    log.info("Токен для пользователя {} успешно обновлён.",
                            EmailUtils.maskEmail(user.getEmail()));
                } catch (Exception e) {
                    log.error("Не удалось обновить токен для пользователя: {}",
                            EmailUtils.maskEmail(user.getEmail()));
                }
            }
            log.info("Плановое обновление токенов завершено.");
        } catch (Exception e) {
            log.error("Ошибка при плановом обновлении токенов: {}", e.getMessage());
        }
    }

}