package com.project.tracking_system.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * Перехватчик для логирования входящих HTTP-запросов.
 * <p>
 * Генерирует correlation ID для каждого запроса и сохраняет его в MDC,
 * а также логирует метод и URI при получении и завершении обработки запроса.
 * </p>
 */
@Slf4j
@Component
public class LoggingInterceptor implements HandlerInterceptor {

    private static final String START_TIME = "startTime";
    private static final String CORRELATION_ID = "correlationId";

    /**
     * Выполняется перед обработкой контроллера.
     *
     * @param request  текущий HTTP-запрос
     * @param response текущий HTTP-ответ
     * @param handler  выбранный обработчик
     * @return {@code true}, чтобы продолжить обработку запроса
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String correlationId = UUID.randomUUID().toString();
        MDC.put(CORRELATION_ID, correlationId); // сохраняем correlation ID
        request.setAttribute(START_TIME, System.currentTimeMillis());

        log.info("Начало обработки: {} {}", request.getMethod(), request.getRequestURI());
        return true;
    }

    /**
     * Вызывается после завершения обработки запроса.
     *
     * @param request  текущий HTTP-запрос
     * @param response текущий HTTP-ответ
     * @param handler  выбранный обработчик
     * @param ex       возможное исключение
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        Object startAttr = request.getAttribute(START_TIME);
        if (startAttr instanceof Long start) {
            long duration = System.currentTimeMillis() - start;
            log.info("Завершение обработки: {} {} ({} мс)", request.getMethod(), request.getRequestURI(), duration);
        } else {
            log.info("Завершение обработки: {} {}", request.getMethod(), request.getRequestURI());
        }
        MDC.remove(CORRELATION_ID); // очищаем MDC
    }
}
