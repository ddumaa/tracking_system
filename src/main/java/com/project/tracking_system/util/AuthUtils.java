package com.project.tracking_system.util;

import com.project.tracking_system.entity.User;
import com.project.tracking_system.exception.UserNotAuthenticatedException;
import lombok.experimental.UtilityClass;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * Вспомогательные методы для работы с аутентификацией.
 */
@UtilityClass
public class AuthUtils {

    /**
     * Возвращает текущего аутентифицированного пользователя.
     *
     * @param authentication объект аутентификации
     * @return текущий пользователь
     * @throws UserNotAuthenticatedException если пользователь отсутствует
     */
    public static User getCurrentUser(Authentication authentication) {
        if (authentication instanceof UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof User user) {
            return user;
        }
        throw new UserNotAuthenticatedException("Пользователь не аутентифицирован");
    }
}
