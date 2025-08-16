package com.project.tracking_system.utils;

import com.project.tracking_system.service.ratelimit.Bucket4jRateLimiter;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Фильтр ограничения частоты запросов для API покупателей.
 * <p>
 * Проверяет POST-запросы к {@code /api/customers/**} по идентификаторам
 * магазина или пользователя. Для Telegram-хука применяется отдельный,
 * более мягкий лимит.
 * </p>
 */
@Component
@RequiredArgsConstructor
public class ApiRateLimitFilter extends OncePerRequestFilter {

    private static final String CUSTOMERS_PATH = "/api/customers";
    private static final String TELEGRAM_HOOK_PATH = "/telegram/hook";

    private final Bucket4jRateLimiter rateLimiter;

    /**
     * Определяет, нужно ли применять фильтр к текущему запросу.
     * <p>
     * Фильтр активен только для POST-запросов к путям {@code /api/customers/**}
     * и {@code /telegram/hook}.
     * </p>
     *
     * @param request обрабатываемый запрос
     * @return {@code true}, если фильтр можно пропустить
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        return !(uri.startsWith(CUSTOMERS_PATH) || uri.equals(TELEGRAM_HOOK_PATH));
    }

    /**
     * Выполняет проверку лимита и либо продолжает цепочку, либо возвращает 429.
     *
     * @param request     обрабатываемый запрос
     * @param response    ответ, в который при необходимости пишется ошибка
     * @param filterChain дальнейшая цепочка фильтров
     * @throws ServletException если произошла ошибка фильтрации
     * @throws IOException      при ошибках ввода-вывода
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        Bucket bucket;
        if (uri.equals(TELEGRAM_HOOK_PATH)) {
            bucket = rateLimiter.resolveTelegramBucket("telegram");
        } else {
            String key = Optional.ofNullable(request.getParameter("storeId"))
                    .orElse(request.getParameter("userId"));
            if (key == null) {
                key = request.getRemoteAddr();
            }
            bucket = rateLimiter.resolveCustomerBucket(key);
        }

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_TOO_MANY_REQUESTS);
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write("Превышен лимит запросов.");
    }
}
