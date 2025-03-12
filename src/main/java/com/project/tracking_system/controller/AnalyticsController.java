package com.project.tracking_system.controller;

import com.project.tracking_system.service.analytics.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * @author Dmitriy Anisimov
 * @date 13.03.2025
 */
@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("/analytics")
public class AnalyticsController {

    // Пример: сервис, если нужно загружать данные для аналитики.
    private final AnalyticsService analyticsService;

    /**
     * Главная страница аналитики ("/analytics").
     * Отдаёт базовый шаблон, где может быть общее меню/дашборд.
     */
    @GetMapping
    public String showAnalyticsDashboard() {
        log.info("Loading analytics dashboard");
        // Ищет файл resources/templates/analytics/dashboard.html
        return "analytics/dashboard";
    }

    /**
     * Страница со статистикой по магазинам ("/analytics/stores").
     * Пример метода, который можно расширить, чтобы отображать реальную статистику.
     */
    @GetMapping("/stores")
    public String showStoreStatistics(Model model) {
        // Если бы нам нужны были данные, берём их из сервиса:
        // List<StoreStatistics> stats = storeStatisticsService.findAll();
        // model.addAttribute("stats", stats);

        log.info("Loading store statistics analytics");
        // Ищет файл resources/templates/analytics/store_statistics.html
        return "analytics/store_statistics";
    }

}