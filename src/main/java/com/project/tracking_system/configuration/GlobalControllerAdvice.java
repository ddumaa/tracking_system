package com.project.tracking_system.configuration;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Глобальная обработка для добавления информации о текущем аутентифицированном пользователе в модель.
 * <p>
 * Класс использует {@link ControllerAdvice} для того, чтобы глобально добавлять в модель атрибут с именем аутентифицированного
 * пользователя, который может быть использован в представлениях. Если пользователь аутентифицирован, его имя добавляется в модель,
 * иначе возвращается {@code null}.
 * </p>
 *
 * @author Dmitriy Anisimov
 * @date 07.01.2025
 */
@ControllerAdvice
public class GlobalControllerAdvice {

    /**
     * Добавляет имя аутентифицированного пользователя в модель.
     * <p>
     * Если пользователь аутентифицирован, его имя извлекается из {@link Authentication} и добавляется в модель как атрибут
     * "authenticatedUser". В случае анонимного пользователя возвращается {@code null}.
     * </p>
     *
     * @return Имя аутентифицированного пользователя или {@code null}, если пользователь анонимный.
     */
    @ModelAttribute("authenticatedUser")
    public String getAuthenticatedUser(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if(authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken)){
            return authentication.getName();
        }
        return null;
    }
}