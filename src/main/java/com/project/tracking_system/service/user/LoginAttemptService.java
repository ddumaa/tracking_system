package com.project.tracking_system.service.user;

import com.project.tracking_system.entity.LoginAttempt;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.repository.LoginAttemptRepository;
import com.project.tracking_system.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Сервис для управления попытками входа в систему.
 * <p>
 * Этот сервис управляет количеством неудачных попыток входа пользователя в систему, блокирует аккаунт при
 * превышении максимально допустимого числа попыток и позволяет сбросить счётчик попыток при успешном входе.
 * </p>
 *
 * @author Дмитрий Анисимов
 * @date Добавленно 07.01.2025
 */
@Service
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 4;  // Максимальное количество попыток входа
    private static final long LOCK_TIME_DURATION = 1;  // Время блокировки аккаунта в часах

    private final UserRepository userRepository;  // Репозиторий для работы с пользователями
    private final LoginAttemptRepository loginAttemptRepository;  // Репозиторий для работы с попытками входа

    /**
     * Конструктор класса {@link LoginAttemptService}.
     *
     * @param userRepository репозиторий для работы с пользователями
     * @param loginAttemptRepository репозиторий для работы с попытками входа
     */
    @Autowired
    public LoginAttemptService(UserRepository userRepository, LoginAttemptRepository loginAttemptRepository) {
        this.userRepository = userRepository;
        this.loginAttemptRepository = loginAttemptRepository;
    }

    /**
     * Сбрасывает счётчик неудачных попыток входа при успешном входе.
     * <p>
     * Если пользователь с указанным email существует, его счётчик попыток сбрасывается в 0.
     * </p>
     *
     * @param email адрес электронной почты пользователя
     */
    public void loginSucceeded(String email) {
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            LoginAttempt loginAttempt = user.getLoginAttempt();
            if (loginAttempt != null) {
                loginAttempt.setAttempts(0);
                loginAttemptRepository.save(loginAttempt);
            }
        }
    }

    /**
     * Проверяет, заблокирован ли пользователь из-за слишком большого количества неудачных попыток.
     * <p>
     * Если количество неудачных попыток превышает максимальное, возвращается {@code true}, если блокировка
     * ещё активна. В противном случае счётчик попыток сбрасывается.
     * </p>
     *
     * @param email адрес электронной почты пользователя
     * @return {@code true}, если пользователь заблокирован, иначе {@code false}
     */
    public boolean isBlocked(String email) {
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            LoginAttempt loginAttempt = user.getLoginAttempt();
            if (loginAttempt != null && loginAttempt.getAttempts() >= MAX_ATTEMPTS) {
                ZonedDateTime zonedDateTime = loginAttempt.getLastModified().plusHours(LOCK_TIME_DURATION);
                if (zonedDateTime.isAfter(ZonedDateTime.now(ZoneOffset.UTC))) {
                    return true;
                } else {
                    loginAttempt.setAttempts(0);
                    loginAttemptRepository.save(loginAttempt);
                }
            }
        }
        return false;
    }

    /**
     * Обрабатывает неудачную попытку входа.
     * <p>
     * Если пользователь существует, увеличивается счётчик неудачных попыток, и сохраняется время последней
     * попытки.
     * </p>
     *
     * @param email адрес электронной почты пользователя
     */
    public void loginFailed(String email) {
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            LoginAttempt loginAttempt = user.getLoginAttempt();
            if (loginAttempt == null) {
                loginAttempt = new LoginAttempt();
                loginAttempt.setUser(user);
                user.setLoginAttempt(loginAttempt);
            }
            loginAttempt.setAttempts(loginAttempt.getAttempts() + 1);
            loginAttempt.setLastModified(ZonedDateTime.now(ZoneOffset.UTC));
            loginAttemptRepository.save(loginAttempt);
        }
    }

    /**
     * Получает количество оставшихся попыток для входа.
     * <p>
     * Возвращает количество оставшихся попыток до блокировки аккаунта.
     * </p>
     *
     * @param email адрес электронной почты пользователя
     * @return количество оставшихся попыток входа
     */
    public int getRemainingAttempts(String email) {
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            LoginAttempt loginAttempt = user.getLoginAttempt();
            if (loginAttempt != null) {
                return MAX_ATTEMPTS - loginAttempt.getAttempts();
            }
        }
        return MAX_ATTEMPTS;
    }

    /**
     * Получает время, когда аккаунт будет разблокирован после достижения максимального числа попыток.
     * <p>
     * Если количество неудачных попыток превышает максимально допустимое, возвращается время разблокировки.
     * </p>
     *
     * @param email адрес электронной почты пользователя
     * @return время разблокировки аккаунта, если он заблокирован, иначе {@code null}
     */
    public ZonedDateTime getUnlockTime(String email) {
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            LoginAttempt loginAttempt = user.getLoginAttempt();
            if (loginAttempt != null && loginAttempt.getAttempts() >= MAX_ATTEMPTS) {
                return loginAttempt.getLastModified().plusHours(LOCK_TIME_DURATION);
            }
        }
        return null;
    }
}