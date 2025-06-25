package com.project.tracking_system.configuration;

import com.project.tracking_system.dto.UserSettingsDTO;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Глобальная обработка добавления пользовательских настроек в модель.
 * <p>
 * Класс использует {@link ControllerAdvice}, чтобы автоматически помещать в
 * модель объект {@link UserSettingsDTO} со значением флага отображения кнопки
 * массового обновления. Если пользователь не аутентифицирован, значение флага
 * будет {@code false}.
 * </p>
 */
@ControllerAdvice
@RequiredArgsConstructor
public class UserSettingsAdvice {

    private final UserService userService;

    /**
     * Добавляет в модель настройки текущего пользователя.
     * <p>
     * Метод извлекает идентификатор аутентифицированного пользователя и
     * запрашивает у {@link UserService} значение флага отображения кнопки
     * массового обновления. Для неаутентифицированных пользователей
     * возвращается значение {@code false}.
     * </p>
     *
     * @return DTO с настройками пользователя
     */
    @ModelAttribute("userSettings")
    public UserSettingsDTO addUserSettings() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getPrincipal() instanceof User user) {
            return new UserSettingsDTO(userService.isShowBulkUpdateButton(user.getId()));
        }
        return new UserSettingsDTO(false);
    }
}
