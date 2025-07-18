package com.project.tracking_system.webdriver;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

/**
 * Реализация {@link WebDriverFactory} для браузера Chrome.
 */
public class ChromeWebDriverFactory implements WebDriverFactory {

    /** Путь к исполняемому файлу chromedriver. */
    private final String driverPath;

    /**
     * Создаёт фабрику с указанным путём к драйверу.
     *
     * @param driverPath путь к исполняемому файлу chromedriver. Передача
     *                   значения через конструктор позволяет конфигурировать
     *                   запуск и избегать проблем с правами доступа.
     */
    public ChromeWebDriverFactory(String driverPath) {
        this.driverPath = driverPath;
    }

    /**
     * Создаёт {@link ChromeDriver} с набором стандартных опций.
     * <p>
     * Перед созданием драйвера устанавливается системное свойство
     * {@code webdriver.chrome.driver}, что позволяет избежать проблем с
     * доступом к бинарному файлу ChromeDriver.
     * </p>
     *
     * @return сконфигурированный экземпляр ChromeDriver
     */
    @Override
    public WebDriver create() {
        System.setProperty("webdriver.chrome.driver", driverPath);

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=chrome");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");
        //options.addArguments("--ignore-certificate-errors");
        //options.addArguments("--no-sandbox");
        //options.addArguments("--disable-dev-shm-usage");
        //options.addArguments("--remote-debugging-port=9222");
        return new ChromeDriver(options);
    }

}