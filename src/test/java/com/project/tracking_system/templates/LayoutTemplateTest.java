package com.project.tracking_system.templates;

import com.project.tracking_system.entity.Role;
import com.project.tracking_system.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Набор тестов для проверки корректной обработки шаблона layout.html.
 */
@SpringBootTest
class LayoutTemplateTest {

    @Autowired
    private TemplateEngine templateEngine;

    /**
     * Очищает контекст безопасности после каждого теста.
     */
    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Проверяет, что шаблон успешно рендерится при присутствии авторизованного пользователя.
     */
    @Test
    void rendersForAuthorizedUser() {
        User user = new User();
        user.setId(1L);
        user.setRole(Role.ROLE_USER);
        Authentication auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        templateEngine.process("layout/layout", new Context());
    }

    /**
     * Проверяет, что шаблон успешно рендерится при отсутствии информации об аутентификации.
     */
    @Test
    void rendersForAnonymousUser() {
        templateEngine.process("layout/layout", new Context());
    }
}

