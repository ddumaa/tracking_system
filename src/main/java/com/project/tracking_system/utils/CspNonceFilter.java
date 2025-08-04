package com.project.tracking_system.utils;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * @author Dmitriy Anisimov
 * @date 04.02.2025
 */
@Component
public class CspNonceFilter extends OncePerRequestFilter {

    /**
     * Разрешённые адреса для директивы connect-src.
     */
    private final String[] allowedConnectOrigins;

    /**
     * Разрешённые адреса для директивы form-action.
     */
    private final String[] allowedFormActionOrigins;

    /**
     * Создаёт фильтр с разрешёнными источниками connect-src и form-action.
     *
     * @param allowedConnectOrigins     список разрешённых адресов для connect-src
     * @param allowedFormActionOrigins  список разрешённых адресов для form-action
     */
    public CspNonceFilter(@Value("${csp.allowed-connect-origins}") String[] allowedConnectOrigins,
                          @Value("${csp.allowed-form-action-origins:}") String[] allowedFormActionOrigins) {
        this.allowedConnectOrigins = allowedConnectOrigins;
        this.allowedFormActionOrigins = allowedFormActionOrigins;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Генерируем случайный nonce (16 байт = 128 бит)
        byte[] nonceBytes = new byte[16];
        new SecureRandom().nextBytes(nonceBytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);

        request.setAttribute("cspNonce", nonce);
        request.setAttribute("nonce", nonce);

        // Формируем заголовок CSP
        String connectSrc = String.join(" ", allowedConnectOrigins);
        String formAction = String.join(" ", allowedFormActionOrigins);

        String cspPolicy = "default-src 'self'; " +
                // Разрешаем скрипты собственного происхождения, CDN и Google reCAPTCHA
                "script-src 'self' 'nonce-" + nonce + "' https://code.jquery.com https://cdn.jsdelivr.net https://www.google.com/recaptcha/ https://www.gstatic.com/recaptcha/; " +
                // Разрешаем подключать стили с CDN Google Fonts и reCAPTCHA
                "style-src 'self' 'unsafe-inline' https://cdnjs.cloudflare.com https://cdn.jsdelivr.net https://fonts.googleapis.com https://www.gstatic.com/recaptcha/; " +
                // Разрешаем подгрузку шрифтов с CDN Google Fonts
                "font-src 'self' https://cdnjs.cloudflare.com https://cdn.jsdelivr.net https://fonts.gstatic.com; " +
                "img-src 'self' data:; " +
                "connect-src 'self'" + (connectSrc.isBlank() ? "" : " " + connectSrc) + "; " +
                "object-src 'none'; " +
                "frame-ancestors 'none'; " +
                // Разрешаем загрузку iframe с Google reCAPTCHA
                "frame-src 'self' https://www.google.com/recaptcha/; " +
                "base-uri 'self'; " +
                "form-action 'self'" + (formAction.isBlank() ? "" : " " + formAction) + ";";

        // Устанавливаем заголовки безопасности
        response.setHeader("Content-Security-Policy", cspPolicy);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload");

        // Устанавливаем XSRF-TOKEN и JSESSIONID с SameSite=None и Secure
        HttpSession session = request.getSession(false);
        if (session != null) {
            response.addHeader("Set-Cookie", "JSESSIONID=" + session.getId() + "; Path=/; HttpOnly; SameSite=None; Secure");
        }

        String xsrfToken = getTokenFromRequest(request);
        if (xsrfToken != null) {
            response.addHeader("Set-Cookie", "XSRF-TOKEN=" + xsrfToken + "; Path=/; HttpOnly; SameSite=None; Secure");
        }

        // Проверяем, принял ли пользователь куки
        String cookieConsent = getCookieValue(request, "cookie_consent");
        if (cookieConsent == null) {
            // Пользователь не принял куки → показываем окно
            response.addHeader("Set-Cookie", "cookie_consent=not_set; Path=/; SameSite=None; Secure");
        }

        if ("declined".equals(cookieConsent)) {
            // Если пользователь отказался — очищаем сторонние куки, например аналитику
            clearCookie(response, "analytics");
        }

        // Продолжаем цепочку фильтров
        filterChain.doFilter(request, response);
    }

    /**
     * Извлекает значение куки {@code XSRF-TOKEN} из запроса.
     *
     * @param request HTTP-запрос
     * @return значение токена или {@code null}, если кука отсутствует
     */
    private String getTokenFromRequest(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("XSRF-TOKEN".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private String getCookieValue(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookieName.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    /**
     * Очищает указанную куку у клиента.
     *
     * @param response   HTTP-ответ
     * @param cookieName имя куки
     */
    private void clearCookie(HttpServletResponse response, String cookieName) {
        Cookie cookie = new Cookie(cookieName, null);
        cookie.setMaxAge(0);
        cookie.setPath("/");
        cookie.setSecure(true);
        cookie.setHttpOnly(true);
        cookie.setAttribute("SameSite", "None");
        response.addCookie(cookie);
    }

}