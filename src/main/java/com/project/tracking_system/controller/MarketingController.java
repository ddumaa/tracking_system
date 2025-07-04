package com.project.tracking_system.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Контроллер маркетинговых страниц сайта.
 */
@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("/")
public class MarketingController {

    /**
     * Отображает публичную домашнюю страницу.
     *
     * @return имя представления маркетинговой домашней страницы
     */
    @GetMapping
    public String home() {
        return "marketing/home";
    }

    /**
     * Отображает страницу с возможностями сервиса.
     *
     * @return имя представления страницы с описанием функций
     */
    @GetMapping("features")
    public String features() {
        return "marketing/features";
    }

    /**
     * Отображает страницу с тарифами для гостей.
     *
     * @return имя представления маркетинговой страницы тарифов
     */
    @GetMapping("pricing")
    public String pricing() {
        return "marketing/pricing";
    }

    /**
     * Отображает страницу с пользовательским соглашением.
     *
     * @return имя представления с условиями использования
     */
    @GetMapping("terms")
    public String terms() {
        return "marketing/terms";
    }

    /**
     * Отображает страницу политики конфиденциальности.
     *
     * @return имя представления политики конфиденциальности
     */
    @GetMapping("privacy")
    public String privacy() {
        return "marketing/privacy";
    }
}
