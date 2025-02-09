package com.project.tracking_system.service.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

/**
 * Сервис для генерации HTML-шаблонов для отправки email.
 * <p>
 * Этот сервис предоставляет методы для создания HTML-шаблонов сообщений, таких как письмо с кодом подтверждения.
 * </p>
 *
 * @author Dmitriy Anisimov
 * @date Добавленно 07.01.2025
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HtmlEmailTemplateService {

    private final TemplateEngine templateEngine;

    /**
     * Генерирует HTML-формат email из шаблона Thymeleaf
     *
     * @param templateName имя шаблона (без .html)
     * @param variables    переменные для вставки в письмо
     * @return сгенерированное письмо в формате HTML
     */
    public String generateEmail(String templateName, Map<String, Object> variables) {
        try {
            Context context = new Context();
            context.setVariables(variables);
            return templateEngine.process("email/" + templateName, context);
        } catch (Exception e) {
            log.error("Ошибка генерации шаблона {}: {}", templateName, e.getMessage(), e);
            return "<p>Ошибка генерации письма</p>";
        }
    }
}