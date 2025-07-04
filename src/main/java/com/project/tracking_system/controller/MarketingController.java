package com.project.tracking_system.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Контроллер маркетинговых страниц.
 */
@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("/")
public class MarketingController {

    /**
     * Отображает главную маркетинговую страницу.
     *
     * @return имя шаблона главной страницы
     */
    @GetMapping
    public String index() {
        return "marketing/index";
    }

    /**
     * Отображает страницу с описанием функционала.
     *
     * @return имя шаблона страницы "Возможности"
     */
    @GetMapping("/features")
    public String features() {
        return "marketing/features";
    }

    /**
     * Отображает страницу с тарифами и ценами.
     *
     * @return имя шаблона страницы "Цены"
     */
    @GetMapping("/pricing")
    public String pricing() {
        return "marketing/pricing";
    }

    /**
     * Политика конфиденциальности.
     *
     * @return имя шаблона политики конфиденциальности
     */
    @GetMapping("/privacy-policy")
    public String privacyPolicy() {
        return "marketing/privacy-policy";
    }

    /**
     * Условия использования сервиса.
     *
     * @return имя шаблона страницы "Условия использования"
     */
    @GetMapping("/terms-of-use")
    public String termsOfUse() {
        return "marketing/terms-of-use";
    }
}
