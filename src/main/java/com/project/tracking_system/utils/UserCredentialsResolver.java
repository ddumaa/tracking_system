package com.project.tracking_system.utils;

import com.project.tracking_system.dto.ResolvedCredentialsDTO;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.model.evropost.jsonRequestModel.JsonPacket;
import com.project.tracking_system.service.jsonEvropostService.JwtTokenManager;
import com.project.tracking_system.utils.EmailUtils;
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

    /**
     * Определяет подходящие учётные данные для обращения к API Evropost.
     *
     * @param user пользователь, для которого выбираются данные
     * @return разрешённые учётные данные
     */
    public ResolvedCredentialsDTO resolveCredentials(User user) {
        log.info("Начинается проверка использования данных для api evropost для пользователя: {}",
                EmailUtils.maskEmail(user.getEmail()));

        if (canUseCustomCredentials(user)) {
            try {
                // Если можно использовать пользовательские данные
                log.debug("Пользовательские данные для {} могут быть использованы.", EmailUtils.maskEmail(user.getEmail()));
                String jwt = jwtTokenManager.getUserToken(user);
                String serviceNumber = encryptionUtils.decrypt(user.getEvropostServiceCredential().getServiceNumber());
                log.debug("Успешно получены пользовательские данные для {}: JWT и ServiceNumber.", EmailUtils.maskEmail(user.getEmail()));
                return new ResolvedCredentialsDTO(jwt, serviceNumber);
            } catch (Exception e) {
                // Если произошла ошибка, переключаемся на системные данные
                log.error("Ошибка при использовании пользовательских данных для пользователя {}. Переключаемся на системные данные.", EmailUtils.maskEmail(user.getEmail()), e);
            }
        }

        // Если пользовательские данные нельзя использовать, возвращаем системные данные
        log.info("Используем системные данные для пользователя {}", EmailUtils.maskEmail(user.getEmail()));
        return new ResolvedCredentialsDTO(jwtTokenManager.getSystemToken(), jsonPacket.getServiceNumber());

    }

    /**
     * Проверяет наличие и корректность пользовательских данных для API.
     *
     * @param user пользователь, чьи данные проверяются
     * @return {@code true}, если можно использовать собственные учётные данные
     */
    private boolean canUseCustomCredentials(User user) {
        log.debug("Проверяем возможность использования пользовательских данных для пользователя: {}", EmailUtils.maskEmail(user.getEmail()));

        if (!Boolean.TRUE.equals(user.getEvropostServiceCredential().getUseCustomCredentials())) {
            log.debug("Пользователь {} не настроил использование пользовательских данных.", EmailUtils.maskEmail(user.getEmail()));
            return false;
        }

        try {
            boolean result = user.getEvropostServiceCredential().getUsername() != null && !user.getEvropostServiceCredential().getUsername().isBlank()
                    && user.getEvropostServiceCredential().getPassword() != null && !encryptionUtils.decrypt(user.getEvropostServiceCredential().getPassword()).isBlank()
                    && user.getEvropostServiceCredential().getServiceNumber() != null && !encryptionUtils.decrypt(user.getEvropostServiceCredential().getServiceNumber()).isBlank();
            log.debug("Результат проверки пользовательских данных для {}: {}", EmailUtils.maskEmail(user.getEmail()), result);
            return result;
        } catch (Exception e) {
            log.error("Ошибка при проверке пользовательских данных для пользователя: {}", EmailUtils.maskEmail(user.getEmail()), e);
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