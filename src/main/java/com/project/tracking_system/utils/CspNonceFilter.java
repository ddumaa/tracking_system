package com.project.tracking_system.utils;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
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

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Генерируем случайный nonce (16 байт = 128 бит)
        byte[] nonceBytes = new byte[16];
        new SecureRandom().nextBytes(nonceBytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);

        request.setAttribute("cspNonce", nonce);

        // Формируем заголовок CSP
        String cspPolicy = "default-src 'self'; " +
                "script-src 'self' 'nonce-" + nonce + "' https://code.jquery.com https://cdn.jsdelivr.net; " +
                "style-src 'self' 'unsafe-inline' https://cdnjs.cloudflare.com https://cdn.jsdelivr.net; " +
                "font-src 'self' https://cdnjs.cloudflare.com https://cdn.jsdelivr.net; " +
                "img-src 'self' data:; " +
                "connect-src 'self' wss://belivery.by; " +
                "object-src 'none'; " +
                "frame-ancestors 'none'; " +
                "base-uri 'self'; " +
                "form-action 'self';";

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

        // Продолжаем цепочку фильтров
        filterChain.doFilter(request, response);
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("XSRF-TOKEN".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return "";
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

}