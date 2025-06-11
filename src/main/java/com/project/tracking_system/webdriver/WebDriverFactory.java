package com.project.tracking_system.webdriver;

import org.openqa.selenium.WebDriver;

/**
 * Фабрика для создания экземпляров {@link WebDriver}.
 *
 * Предоставляет возможность использовать различные браузеры или заглушки
 * при тестировании.
 */
public interface WebDriverFactory {

    /**
     * Создаёт новый экземпляр {@link WebDriver}.
     *
     * @return драйвер для работы с Selenium
     */
    WebDriver create();
}
