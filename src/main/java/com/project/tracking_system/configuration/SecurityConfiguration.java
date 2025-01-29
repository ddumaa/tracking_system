package com.project.tracking_system.configuration;

import com.project.tracking_system.service.user.LoginAttemptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * Конфигурация безопасности приложения с настройками для аутентификации, авторизации, защиты от атак и управления сессиями.
 * <p>
 * Этот класс отвечает за настройку всех аспектов безопасности приложения, включая обработку входа в систему, защиту от атак CSRF,
 * управление сессиями и другие аспекты, связанные с безопасностью. Конфигурация использует настройки для:
 * - Разрешения доступа к публичным страницам (например, /login, /registration)
 * - Настройки формы входа и обработку успешного и неуспешного входа
 * - Использования механизма "remember-me" для запоминания пользователя
 * - Настройки выхода из системы (logout)
 * - Установки для сессий (например, максимальное количество сессий)
 * - Защиты от атак CSRF с использованием cookie
 * </p>
 *
 * @author Dmitriy Anisimov
 * @date 07.01.2025
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    private final LoginAttemptService loginAttemptService;

    @Autowired
    public SecurityConfiguration(LoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    /**
     * Бин для создания {@link PasswordEncoder}, используемого для хеширования паролей.
     *
     * @return BCryptPasswordEncoder для хеширования паролей.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Бин для настройки {@link SecurityFilterChain}, который управляет правилами безопасности HTTP-запросов.
     *
     * @param http объект конфигурации {@link HttpSecurity}.
     * @return {@link SecurityFilterChain} с настроенными параметрами безопасности.
     * @throws Exception если возникают ошибки при настройке безопасности.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .headers(h -> h.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))
                .authorizeHttpRequests(authorizeRequests -> authorizeRequests
                        .requestMatchers("/", "/login", "/logout", "/registration", "/forgot-password", "/reset-password",
                                "/css/**", "/js/**", "/images/**").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(formLogin -> formLogin
                        .loginPage("/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler((request, response, authentication) -> {
                            String email = request.getParameter("email");
                            loginAttemptService.loginSucceeded(email);
                            response.sendRedirect("/");
                        })
                        .failureHandler((request, response, exception) -> {
                            String email = request.getParameter("email");
                            request.getSession().setAttribute("email", email); // Store email in session
                            if (loginAttemptService.isBlocked(email)) {
                                response.sendRedirect("/login?blocked=true");
                            } else {
                                loginAttemptService.loginFailed(email);
                                response.sendRedirect("/login?error=true");
                            }
                        })
                )
                .rememberMe(rememberMe -> rememberMe.key("remember-me"))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                )
                .sessionManagement(sessionManagement -> sessionManagement
                        .sessionCreationPolicy(SessionCreationPolicy.ALWAYS)
                        .maximumSessions(1)
                )
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers("/login", "/logout", "/history", "/history/delete-selected")
                );

        return http.build();
    }
}