package com.project.tracking_system.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Контроллер перенаправлений для поддержки старых URL.
 */
@Controller
public class LegacyRedirectController {

    /**
     * Перенаправляет запросы со старого пути "/tariffs" на новый "/app/tariffs".
     *
     * @return строка с редиректом на новую страницу тарифов
     */
    @GetMapping("/tariffs")
    public String redirectTariffs() {
        return "redirect:/app/tariffs";
    }
}
