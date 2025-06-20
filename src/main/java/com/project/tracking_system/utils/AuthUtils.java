package com.project.tracking_system.utils;

import com.project.tracking_system.entity.User;
import org.springframework.security.core.Authentication;

/**
 * Утилиты для работы с данными аутентификации.
 */
public final class AuthUtils {

    private AuthUtils() {
    }

    /**
     * Возвращает текущего пользователя из объекта аутентификации.
     *
     * @param authentication объект аутентификации
     * @return текущий пользователь
     * @throws SecurityException если пользователь отсутствует или не аутентифицирован
     */
    public static User getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof User user)) {
            throw new SecurityException("Необходима аутентификация пользователя");
        }
        return user;
    }
}
