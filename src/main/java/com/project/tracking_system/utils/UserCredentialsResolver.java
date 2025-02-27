package com.project.tracking_system.utils;

import com.project.tracking_system.dto.ResolvedCredentialsDTO;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.model.evropost.jsonRequestModel.JsonPacket;
import com.project.tracking_system.service.jsonEvropostService.JwtTokenManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author Dmitriy Anisimov
 * @date 29.01.2025
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class UserCredentialsResolver {

    private final JwtTokenManager jwtTokenManager;
    private final EncryptionUtils encryptionUtils;
    private final JsonPacket jsonPacket;

    public ResolvedCredentialsDTO resolveCredentials(User user) {
        log.info("Начинается проверка использования данных для api evropost для пользователя: {}", user.getEmail());

        if (canUseCustomCredentials(user)) {
            try {
                // Если можно использовать пользовательские данные
                log.debug("Пользовательские данные для {} могут быть использованы.", user.getEmail());
                String jwt = jwtTokenManager.getUserToken(user);
                String serviceNumber = encryptionUtils.decrypt(user.getServiceNumber());
                log.debug("Успешно получены пользовательские данные для {}: JWT и ServiceNumber.", user.getEmail());
                return new ResolvedCredentialsDTO(jwt, serviceNumber);
            } catch (Exception e) {
                // Если произошла ошибка, переключаемся на системные данные
                log.error("Ошибка при использовании пользовательских данных для пользователя {}. Переключаемся на системные данные.", user.getEmail(), e);            }
        }

        // Если пользовательские данные нельзя использовать, возвращаем системные данные
        log.info("Используем системные данные для пользователя {}", user.getEmail());
        return new ResolvedCredentialsDTO(jwtTokenManager.getSystemToken(), jsonPacket.getServiceNumber());

    }

    private boolean canUseCustomCredentials(User user) {
        log.debug("Проверяем возможность использования пользовательских данных для пользователя: {}", user.getEmail());

        if (!Boolean.TRUE.equals(user.getUseCustomCredentials())) {
            log.debug("Пользователь {} не настроил использование пользовательских данных.", user.getEmail());
            return false;
        }

        try {
            boolean result = user.getEvropostUsername() != null && !user.getEvropostUsername().isBlank()
                    && user.getEvropostPassword() != null && !encryptionUtils.decrypt(user.getEvropostPassword()).isBlank()
                    && user.getServiceNumber() != null && !encryptionUtils.decrypt(user.getServiceNumber()).isBlank();
            log.debug("Результат проверки пользовательских данных для {}: {}", user.getEmail(), result);
            return result;
        } catch (Exception e) {
            log.error("Ошибка при проверке пользовательских данных для пользователя: {}", user.getEmail(), e);
            return false;
        }
    }

    public ResolvedCredentialsDTO getSystemCredentials() {
        return new ResolvedCredentialsDTO(
                jwtTokenManager.getSystemToken(),
                jsonPacket.getServiceNumber()
        );
    }

}