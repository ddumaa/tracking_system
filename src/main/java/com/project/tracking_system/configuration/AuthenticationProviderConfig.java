package com.project.tracking_system.configuration;

import com.project.tracking_system.service.user.LoginAttemptService;
import com.project.tracking_system.service.user.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Конфигурация провайдера аутентификации для Spring Security.
 * <p>
 * Этот класс настраивает кастомизированный {@link AuthenticationProvider}, который проверяет, заблокирован ли аккаунт пользователя,
 * прежде чем выполнять аутентификацию. Используется {@link DaoAuthenticationProvider} с дополнительной логикой для блокировки аккаунтов
 * на основе количества неудачных попыток входа, а также {@link BCryptPasswordEncoder} для кодирования пароля.
 * </p>
 *
 * @author Dmitriy Anisimov
 * @date 07.01.2025
 */
@Configuration
@RequiredArgsConstructor
public class AuthenticationProviderConfig {

    private final LoginAttemptService loginAttemptService;
    private final UserDetailsServiceImpl userDetailsService;
    private final PasswordEncoder passwordEncoder;

    /**
     * Создает и настраивает {@link AuthenticationProvider} для аутентификации пользователей.
     * <p>
     * Провайдер аутентификации проверяет, заблокирован ли аккаунт пользователя, и если да, то выбрасывает исключение {@link LockedException}.
     * Используется {@link BCryptPasswordEncoder} для кодирования пароля и {@link DaoAuthenticationProvider} для стандартной аутентификации.
     * </p>
     *
     * @return Настроенный {@link AuthenticationProvider}.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider daoAuthenticationProvider = new DaoAuthenticationProvider(){
            @Override
            public Authentication authenticate(Authentication authentication) throws AuthenticationException {
                String email = authentication.getName();
                if (loginAttemptService.isEmailBlocked(email)){
                    throw new LockedException("Аккант временно заблокирован");
                }
                return super.authenticate(authentication);
            }
        };
        daoAuthenticationProvider.setUserDetailsService(userDetailsService);
        daoAuthenticationProvider.setPasswordEncoder(passwordEncoder);
        return daoAuthenticationProvider;
    }

    /**
     * Создает {@link AuthenticationManager} для аутентификации пользователей.
     * <p>
     * {@link AuthenticationManager} используется для обработки аутентификаций в Spring Security.
     * </p>
     *
     * @param authenticationConfiguration Конфигурация аутентификации.
     * @return Настроенный {@link AuthenticationManager}.
     * @throws Exception если не удается создать {@link AuthenticationManager}.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}