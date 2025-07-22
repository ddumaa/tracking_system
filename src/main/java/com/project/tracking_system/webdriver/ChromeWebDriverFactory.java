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

        //
        // Configure ChromeDriver for headless execution on a server.
        // These options allow running inside environments without a display
        // manager (e.g. Docker containers) while maintaining stable behavior.
        //
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