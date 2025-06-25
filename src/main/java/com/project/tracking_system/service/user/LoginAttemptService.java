package com.project.tracking_system.service.user;

import com.project.tracking_system.entity.LoginAttempt;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.repository.LoginAttemptRepository;
import com.project.tracking_system.repository.UserRepository;
import com.project.tracking_system.utils.EmailUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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
@RequiredArgsConstructor
@Slf4j
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 4;  // Максимальное количество попыток входа
    private static final long LOCK_TIME_DURATION = 1;  // Время блокировки аккаунта в часах
    private static final long IP_BLOCK_TIME_MINUTES = 10; // Блокировка IP на 10 минут

    private final UserRepository userRepository;  // Репозиторий для работы с пользователями
    private final LoginAttemptRepository loginAttemptRepository;  // Репозиторий для работы с попытками входа

    private final Map<String, Integer> ipAttempts = new ConcurrentHashMap<>();
    private final Map<String, ZonedDateTime> blockedIPs = new ConcurrentHashMap<>();

    /**
     * Сбрасывает счётчик неудачных попыток входа при успешном входе.
     * <p>
     * Если пользователь с указанным email существует, его счётчик попыток сбрасывается в 0.
     * </p>
     *
     * @param email адрес электронной почты пользователя
     */
    public void loginSucceeded(String email, String ip) {
        Optional<User> userOptional = userRepository.findByEmail(email);

        if (userOptional.isPresent()) {
            User user = userOptional.get();
            LoginAttempt loginAttempt = user.getLoginAttempt();

            if (loginAttempt != null) {
                loginAttempt.setAttempts(0);
                loginAttemptRepository.save(loginAttempt);
            }
        }
        ipAttempts.remove(ip);
        blockedIPs.remove(ip);

        log.info("Пользователь {} успешно вошел. IP {} разблокирован.", EmailUtils.maskEmail(email), ip);
    }

    /**
     * Проверяет, заблокирован ли IP-адрес из-за превышения числа попыток входа.
     *
     * @param ip IP-адрес клиента
     * @return {@code true}, если IP в чёрном списке, иначе {@code false}
     */
    public boolean isIPBlocked(String ip) {
        if (blockedIPs.containsKey(ip)) {

            if (ZonedDateTime.now(ZoneOffset.UTC).isAfter(blockedIPs.get(ip))) {
                blockedIPs.remove(ip);
                ipAttempts.remove(ip);
                return false;
            }
            return true;
        }
        return false;
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
    public boolean isEmailBlocked(String email) {
        Optional<User> userOptional = userRepository.findByEmail(email);

        if (userOptional.isPresent()) {
            User user = userOptional.get();
            LoginAttempt loginAttempt = user.getLoginAttempt();

            if (loginAttempt != null && loginAttempt.getAttempts() >= MAX_ATTEMPTS) {
                ZonedDateTime lastModified = loginAttempt.getLastModified();

                if (lastModified == null) { // Защита от NPE
                    return false; // Считаем, что блокировки нет
                }

                ZonedDateTime zonedDateTime = lastModified.plusHours(LOCK_TIME_DURATION);
                if (zonedDateTime.isAfter(ZonedDateTime.now(ZoneOffset.UTC))) {
                    return true;
                } else {
                    loginAttempt.setAttempts(0);
                    loginAttempt.setLastModified(ZonedDateTime.now(ZoneOffset.UTC));
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
    public void loginFailed(String email, String ip) {
        Optional<User> userOptional = userRepository.findByEmail(email);

        if (userOptional.isPresent()) {
            User user = userOptional.get();
            LoginAttempt loginAttempt = user.getLoginAttempt();

            if (loginAttempt == null) {
                loginAttempt = new LoginAttempt();
                loginAttempt.setUser(user);
                loginAttempt.setLastModified(ZonedDateTime.now(ZoneOffset.UTC)); // 🛠 Добавляем дату при создании
                user.setLoginAttempt(loginAttempt);
            }

            loginAttempt.setAttempts(loginAttempt.getAttempts() + 1);
            loginAttempt.setLastModified(ZonedDateTime.now(ZoneOffset.UTC)); // 🛠 Обновляем дату при каждом провале
            loginAttemptRepository.save(loginAttempt);
        }

        int attempts = ipAttempts.getOrDefault(ip, 0) + 1;
        ipAttempts.put(ip, attempts);

        if (attempts >= MAX_ATTEMPTS) {
            blockedIPs.put(ip, ZonedDateTime.now(ZoneOffset.UTC).plusMinutes(IP_BLOCK_TIME_MINUTES));
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
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
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

    /**
     * Проверяет блокировки по IP и email, перенаправляя пользователя при необходимости.
     *
     * @param request  HTTP-запрос
     * @param response HTTP-ответ
     * @param email    email пользователя (может быть {@code null})
     * @param ip       IP-адрес клиента
     * @return {@code true}, если было выполнено перенаправление
     */
    public boolean checkAndRedirect(HttpServletRequest request, HttpServletResponse response,
                                    String email, String ip) throws IOException {
        if (isIPBlocked(ip)) {
            log.warn("Блокировка по IP: {} (Попытка входа заблокирована)", ip);
            response.sendRedirect("/login?blockedIP=true");
            return true;
        }

        if (email != null && isEmailBlocked(email)) {
            log.warn("Блокировка по email: {} (Попытка входа заблокирована)", EmailUtils.maskEmail(email));
            response.sendRedirect("/login?blocked=true");
            return true;
        }

        return false;
    }

}