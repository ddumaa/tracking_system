package com.project.tracking_system.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Глобальная обработка для добавления информации о текущем аутентифицированном пользователе в модель.
 * <p>
 * Класс использует {@link ControllerAdvice}, чтобы глобально добавлять в модель
 * атрибут с именем аутентифицированного пользователя. Этот атрибут доступен во
 * всех контроллерах и может использоваться в представлениях для проверки факта
 * авторизации. Если пользователь аутентифицирован, его имя добавляется в модель,
 * иначе возвращается {@code null}.
 * </p>
 *
 * @author Dmitriy Anisimov
 * @date 07.01.2025
 */
@ControllerAdvice
public class GlobalControllerAdvice {

    /**
     * Ссылка на Telegram-бота, получаемая из конфигурации приложения.
     * Используется для отображения единого URL в шаблонах.
     */
    @Value("${telegram.bot.link}")
    private String telegramBotLink;

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

    /**
     * Добавляет в модель ссылку на нашего Telegram-бота.
     * <p>
     * Благодаря этому атрибуту шаблоны могут обращаться к одинаковой ссылке,
     * не хардкодя URL в каждом месте.
     * </p>
     *
     * @return ссылка на бота
     */
    @ModelAttribute("telegramBotLink")
    public String getTelegramBotLink() {
        return telegramBotLink;
    }
}