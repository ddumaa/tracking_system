package com.project.tracking_system.templates;

import com.project.tracking_system.entity.Role;
import com.project.tracking_system.entity.User;
import nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.extras.springsecurity6.dialect.SpringSecurityDialect;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.nio.charset.StandardCharsets;

/**
 * Набор тестов для проверки корректной обработки шаблона layout.html.
 */
class LayoutTemplateTest {

    private TemplateEngine templateEngine;

    /**
     * Создаёт движок шаблонов с необходимыми диалектами перед каждым тестом.
     */
    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        engine.addDialect(new SpringSecurityDialect());
        engine.addDialect(new LayoutDialect());
        this.templateEngine = engine;
    }

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