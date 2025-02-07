package com.project.tracking_system.configuration;

import com.project.tracking_system.service.user.LoginAttemptService;
import com.project.tracking_system.utils.CspNonceFilter;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextPersistenceFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.savedrequest.DefaultSavedRequest;

import static org.springframework.security.config.Customizer.withDefaults;

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

    private final static Logger logger = LoggerFactory.getLogger(SecurityConfiguration.class);

    private final UserDetailsService userDetailsService;
    private final LoginAttemptService loginAttemptService;

    @Autowired
    public SecurityConfiguration(LoginAttemptService loginAttemptService,
                                 UserDetailsService userDetailsService) {
        this.loginAttemptService = loginAttemptService;
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CspNonceFilter cspNonceFilter) throws Exception {
        http
                .addFilterBefore(cspNonceFilter, SecurityContextPersistenceFilter.class)
                .headers(h -> h
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
                        .httpStrictTransportSecurity(hsts -> hsts
                                .maxAgeInSeconds(31536000)
                                .includeSubDomains(true)
                                .preload(true)
                        )
                        .defaultsDisabled()
                        .contentTypeOptions(withDefaults())
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                )
                .authorizeHttpRequests(authorizeRequests -> authorizeRequests
                        .requestMatchers("/", "/login", "/logout", "/registration", "/forgot-password", "/reset-password",
                                "/privacy-policy", "/terms-of-use", "/css/**", "/js/**", "/images/**").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .formLogin(formLogin -> formLogin
                        .loginPage("/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler((request, response, authentication) -> {
                            String email = request.getParameter("email");
                            String ip = request.getRemoteAddr();

                            loginAttemptService.loginSucceeded(email, ip);

                            HttpSession session = request.getSession(false);
                            if (session != null) {
                                Object savedRequestObj = session.getAttribute("SPRING_SECURITY_SAVED_REQUEST");
                                if (savedRequestObj instanceof DefaultSavedRequest savedRequest) {
                                    response.sendRedirect(savedRequest.getRedirectUrl());
                                    return;
                                }
                            }
                            response.sendRedirect("/");
                        })
                        .failureHandler((request, response, exception) -> {
                            String email = request.getParameter("email");
                            String ip = request.getRemoteAddr();

                            request.getSession().setAttribute("email", email);

                            if (loginAttemptService.checkAndRedirect(request, response, email, ip)) return;

                            loginAttemptService.loginFailed(email, ip);
                            logger.info("Неудачная попытка входа: email={}, IP={}", email, ip);
                            response.sendRedirect("/login?error=true");
                        })
                )
                .rememberMe(rememberMe -> rememberMe
                        .key(System.getenv("REMEMBER_ME_KEY"))
                        .tokenValiditySeconds(14 * 24 * 60 * 60)
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID", "XSRF-TOKEN")
                )
                .sessionManagement(sessionManagement -> sessionManagement
                        .sessionCreationPolicy(SessionCreationPolicy.ALWAYS)
                        .maximumSessions(1)
                )
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())

                );
        http.userDetailsService(userDetailsService);

        return http.build();
    }


}