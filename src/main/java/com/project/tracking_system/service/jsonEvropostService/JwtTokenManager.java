package com.project.tracking_system.service.jsonEvropostService;

import com.project.tracking_system.entity.User;
import com.project.tracking_system.model.evropost.jsonRequestModel.JsonPacket;
import com.project.tracking_system.repository.UserRepository;
import com.project.tracking_system.utils.EncryptionUtils;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
@Service
public class JwtTokenManager {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenManager.class);

    private final EncryptionUtils encryptionUtils;
    private final GetJwtTokenService getJwtTokenService;
    private final UserRepository userRepository;
    private LocalDateTime systemTokenExpiryTime;
    private String systemToken;

    private static final long TOKEN_LIFETIME_DAYS = 29;

    @Autowired
    public JwtTokenManager(UserRepository userRepository, GetJwtTokenService getJwtTokenService,
                           EncryptionUtils encryptionUtils) {
        this.userRepository = userRepository;
        this.getJwtTokenService = getJwtTokenService;
        this.encryptionUtils = encryptionUtils;
    }

    /**
     * Метод, вызываемый после создания бина, для инициализации токена.
     * Генерирует токен при старте приложения.
     *
     * @throws RuntimeException если не удалось инициализировать токен.
     */
    @PostConstruct
    private void initializeSystemToken() {
        logger.info("Инициализация системного JWT токена при старте приложения.");
        try {
            refreshSystemTokenIfExpired();
            logger.info("Системный JWT токен успешно инициализирован.");
        } catch (Exception e) {
            logger.error("Ошибка при инициализации системного JWT токена: {}", e.getMessage());
        }
    }

    /**
     * Метод для обновления JWT токена.
     * Обновляет токен, если он истёк.
     * Метод синхронизирован для предотвращения одновременных обновлений токена.
     *
     * @throws RuntimeException если не удалось обновить токен.
     */
    private synchronized void refreshSystemTokenIfExpired() {
        if (isSystemTokenExpired()) {
            try {
                String newSystemToken = getJwtTokenService.getSystemTokenFromApi();
                this.systemToken = newSystemToken;
                this.systemTokenExpiryTime = LocalDateTime.now().plusDays(TOKEN_LIFETIME_DAYS);
                logger.info("Системный токен успешно обновлён. Новый срок действия: {}", systemTokenExpiryTime);
            } catch (Exception e) {
                logger.error("Ошибка при обновлении системного токена: {}", e.getMessage());
                throw new RuntimeException("Не удалось обновить системный токен.", e);
            }
        }
    }

    /**
     * Проверяет, истёк ли срок действия токена.
     *
     * @return {@code true}, если токен истёк, иначе {@code false}.
     */
    private boolean isSystemTokenExpired() {
        boolean expired = systemTokenExpiryTime == null || LocalDateTime.now().isAfter(systemTokenExpiryTime);
        if (expired) {
            logger.debug("Системный токен истёк. Требуется обновление.");
        }
        return expired;
    }

    /**
     * Получение системного токена
     */
    public synchronized String getSystemToken() {
        logger.debug("Запрос системного токена.");
        if (isSystemTokenExpired()) {
            refreshSystemTokenIfExpired();
        }
        logger.debug("Системный токен успешно возвращён.");
        return systemToken;
    }

    public synchronized String getUserToken(User user) {
        logger.info("Получение пользовательского токена для пользователя: {}", user.getEmail());
        if (user.getJwtToken() == null || isUserTokenExpired(user)) {
            try {
                logger.debug("JWT токен отсутствует или истёк. Генерация нового токена...");
                String decryptedPassword = encryptionUtils.decrypt(user.getEvropostPassword());
                String decryptedServiceNumber = encryptionUtils.decrypt(user.getServiceNumber());

                String newToken = getJwtTokenService.getUserTokenFromApi(
                        user.getEvropostUsername(),
                        decryptedPassword,
                        decryptedServiceNumber
                );

                user.setJwtToken(newToken);
                user.setTokenCreatedAt(LocalDateTime.now());
                userRepository.save(user);
                logger.info("Новый пользовательский токен успешно создан и сохранён для пользователя: {}", user.getEmail());
            } catch (Exception e) {
                logger.error("Не удалось получить пользовательский JWT токен для пользователя: {}. Причина: {}",
                        user.getEmail(), e.getMessage());
                return null;
            }
        }
        logger.debug("Действующий JWT токен найден для пользователя: {}", user.getEmail());
        return user.getJwtToken();
    }

    private boolean isUserTokenExpired(User user) {
        boolean expired = user.getTokenCreatedAt() == null
                || user.getTokenCreatedAt().plusDays(TOKEN_LIFETIME_DAYS).isBefore(LocalDateTime.now());
        if (expired) {
            logger.debug("Пользовательский токен для {} истёк.", user.getEmail());
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
    @Scheduled(cron = "0 0 0 * * ?") // Обновление токена в полночь
    public void scheduledTokenRefresh() {
        logger.info("Запуск планового обновления токенов в полночь.");
        try {
            refreshSystemTokenIfExpired();

            List<User> users = userRepository.findAll().stream()
                    .filter(User::getUseCustomCredentials) // Только пользователи с пользовательскими кредами
                    .filter(this::isUserTokenExpired)      // Проверяем, истёк ли токен
                    .toList();

            for (User user : users) {
                logger.info("Обновление токена для пользователя: {}", user.getEmail());
                try {
                    String decryptedPassword = encryptionUtils.decrypt(user.getEvropostPassword());
                    String decryptedServiceNumber = encryptionUtils.decrypt(user.getServiceNumber());

                    String newToken = getJwtTokenService.getUserTokenFromApi(
                            user.getEvropostUsername(),
                            decryptedPassword,
                            decryptedServiceNumber
                    );
                    user.setJwtToken(newToken);
                    user.setTokenCreatedAt(LocalDateTime.now());
                    userRepository.save(user);
                    logger.info("Токен для пользователя {} успешно обновлён.", user.getEmail());
                } catch (Exception e) {
                    logger.error("Не удалось обновить токен для пользователя: {}", user.getEmail());
                }
            }
            logger.info("Плановое обновление токенов завершено.");
        } catch (Exception e) {
            logger.error("Ошибка при плановом обновлении токенов: {}", e.getMessage());
        }
    }

}