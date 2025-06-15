package com.project.tracking_system.service.user;

import com.project.tracking_system.dto.UserRegistrationDTO;
import com.project.tracking_system.exception.UserAlreadyExistsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;

/**
 * Сервис для обработки регистрации пользователя.
 * <p>
 * Выполняет начальную отправку кода подтверждения и финальное подтверждение регистрации.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationService {

    private final UserService userService;

    /**
     * Определяет, является ли текущий запрос первым шагом регистрации.
     *
     * @param userDTO данные пользователя
     * @return {@code true}, если код подтверждения ещё не введён
     */
    public boolean isInitialStep(UserRegistrationDTO userDTO) {
        boolean initial = userDTO.getConfirmCodRegistration() == null
                || userDTO.getConfirmCodRegistration().isEmpty();
        log.debug("Проверка первого шага регистрации для {}: {}", userDTO.getEmail(), initial);
        return initial;
    }

    /**
     * Обрабатывает начальный шаг регистрации и отправляет код подтверждения.
     *
     * @param userDTO данные пользователя
     * @param result  результат валидации формы
     * @return {@code true}, если код успешно отправлен
     * @throws UserAlreadyExistsException если пользователь уже существует
     */
    public boolean handleInitialStep(UserRegistrationDTO userDTO, BindingResult result)
            throws UserAlreadyExistsException {
        log.info("Старт первичной регистрации пользователя: {}", userDTO.getEmail());

        if (result.hasFieldErrors("email") || result.hasFieldErrors("password")
                || result.hasFieldErrors("confirmPassword") || result.hasFieldErrors("agreeToTerms")) {
            log.debug("Ошибки валидации формы регистрации для {}", userDTO.getEmail());
            return false;
        }
        if (!userDTO.getPassword().equals(userDTO.getConfirmPassword())) {
            log.warn("Пароли не совпадают для {}", userDTO.getEmail());
            result.rejectValue("confirmPassword", "password.mismatch", "Пароли не совпадают");
            return false;
        }

        userService.sendConfirmationCode(userDTO);
        log.info("Код подтверждения отправлен пользователю {}", userDTO.getEmail());
        return true;
    }

    /**
     * Подтверждает регистрацию пользователя по коду.
     *
     * @param userDTO данные пользователя с кодом подтверждения
     */
    public void confirm(UserRegistrationDTO userDTO) {
        log.info("Подтверждение регистрации для {}", userDTO.getEmail());
        userService.confirmRegistration(userDTO);
    }
}

