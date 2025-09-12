package com.project.tracking_system.webdriver;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

/**
 * Реализация {@link WebDriverFactory} для браузера Chrome.
 */
public class ChromeWebDriverFactory implements WebDriverFactory {

    /**
     * Конструктор по умолчанию.
     * <p>
     * Путь к исполняемому файлу ChromeDriver не указывается, поскольку
     * <b>Selenium Manager</b> автоматически подберёт и скачает необходимый
     * драйвер при первом запуске. Это упрощает конфигурацию и делает класс
     * независимым от окружения.
     * </p>
     */
    public ChromeWebDriverFactory() {
        // Конструктор оставлен пустым намеренно
    }

    /**
     * Создаёт {@link ChromeDriver} с набором стандартных опций.
     * <p>
     * Путь к драйверу не задаётся вручную: Selenium Manager сам найдёт или
     * скачает подходящую версию ChromeDriver.
     * </p>
     *
     * @return сконфигурированный экземпляр ChromeDriver
     */
    @Override
    public WebDriver create() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--ignore-certificate-errors");

        return new ChromeDriver(options);
    }

}