package com.project.tracking_system.webdriver;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

/**
 * Реализация {@link WebDriverFactory} для браузера Chrome.
 */
public class ChromeWebDriverFactory implements WebDriverFactory {

    /**
     * Создаёт {@link ChromeDriver} с набором стандартных опций.
     *
     * @return сконфигурированный экземпляр ChromeDriver
     */
    @Override
    public WebDriver create() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--ignore-certificate-errors");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--remote-debugging-port=9222");
        return new ChromeDriver(options);
    }
}
