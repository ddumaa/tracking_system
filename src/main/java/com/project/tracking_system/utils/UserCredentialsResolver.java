package com.project.tracking_system.utils;

import com.project.tracking_system.dto.ResolvedCredentialsDTO;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.model.evropost.jsonRequestModel.JsonPacket;
import com.project.tracking_system.service.jsonEvropostService.JwtTokenManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Dmitriy Anisimov
 * @date 29.01.2025
 */
@Component
public class UserCredentialsResolver {

    private static final Logger logger = LoggerFactory.getLogger(UserCredentialsResolver.class);

    private final JwtTokenManager jwtTokenManager;
    private final EncryptionUtils encryptionUtils;
    private final JsonPacket jsonPacket;

    @Autowired
    public UserCredentialsResolver(JwtTokenManager jwtTokenManager, EncryptionUtils encryptionUtils,
                                   JsonPacket jsonPacket){
        this.jwtTokenManager = jwtTokenManager;
        this.encryptionUtils = encryptionUtils;
        this.jsonPacket = jsonPacket;
    }

    public ResolvedCredentialsDTO resolveCredentials(User user) {
        logger.info("Начинается проверка использования данных для api evropost для пользователя: {}", user.getEmail());

        if (canUseCustomCredentials(user)) {
            try {
                // Если можно использовать пользовательские данные
                logger.debug("Пользовательские данные для {} могут быть использованы.", user.getEmail());
                String jwt = jwtTokenManager.getUserToken(user);
                String serviceNumber = encryptionUtils.decrypt(user.getServiceNumber());
                logger.debug("Успешно получены пользовательские данные для {}: JWT и ServiceNumber.", user.getEmail());
                return new ResolvedCredentialsDTO(jwt, serviceNumber);
            } catch (Exception e) {
                // Если произошла ошибка, переключаемся на системные данные
                logger.error("Ошибка при использовании пользовательских данных для пользователя {}. Переключаемся на системные данные.", user.getEmail(), e);            }
        }

        // Если пользовательские данные нельзя использовать, возвращаем системные данные
        logger.info("Используем системные данные для пользователя {}", user.getEmail());
        return new ResolvedCredentialsDTO(jwtTokenManager.getSystemToken(), jsonPacket.getServiceNumber());

    }

    private boolean canUseCustomCredentials(User user) {
        logger.debug("Проверяем возможность использования пользовательских данных для пользователя: {}", user.getEmail());

        if (!Boolean.TRUE.equals(user.getUseCustomCredentials())) {
            logger.debug("Пользователь {} не настроил использование пользовательских данных.", user.getEmail());
            return false;
        }

        try {
            boolean result = user.getEvropostUsername() != null && !user.getEvropostUsername().isBlank()
                    && user.getEvropostPassword() != null && !encryptionUtils.decrypt(user.getEvropostPassword()).isBlank()
                    && user.getServiceNumber() != null && !encryptionUtils.decrypt(user.getServiceNumber()).isBlank();
            logger.debug("Результат проверки пользовательских данных для {}: {}", user.getEmail(), result);
            return result;
        } catch (Exception e) {
            logger.error("Ошибка при проверке пользовательских данных для пользователя: {}", user.getEmail(), e);
            return false;
        }
    }

}