package com.project.tracking_system.configuration;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

/**
 * Конфигурация для управления поведением Selenium Manager.
 * <p>
 * Отвечает за настройку системных свойств, отключающих отправку статистики.
 * </p>
 */
@Configuration
public class SeleniumManagerConfig {

    /**
     * Отключает отправку статистики Selenium Manager для уменьшения шума в логах.
     * <p>
     * Устанавливает системные свойства, запрещающие сбор и передачу аналитики.
     * </p>
     */
    @PostConstruct
    public void disableAnalytics() {
        System.setProperty("SE_MANAGER_ANALYTICS", "false");
        System.setProperty("SELENIUM_MANAGER_ANALYTICS", "false");
    }
}
