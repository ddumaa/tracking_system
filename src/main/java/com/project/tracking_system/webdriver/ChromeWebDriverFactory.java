package com.project.tracking_system.webdriver;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.beans.factory.annotation.Value;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Реализация {@link WebDriverFactory} для браузера Chrome.
 */
public class ChromeWebDriverFactory implements WebDriverFactory {

    /**
     * Путь к исполняемому файлу ChromeDriver.
     * <p>
     * Если значение не задано или файл отсутствует, Selenium Manager сам
     * скачает подходящий драйвер.
     * </p>
     */
    @Value("${webdriver.chrome.driver:}")
    private String driverPath;

    /**
     * Конструктор по умолчанию.
     * <p>
     * Путь к ChromeDriver оставляется пустым, чтобы конфигурация не зависела
     * от окружения и соответствовала принципу <b>Single Responsibility</b>.
     * </p>
     */
    public ChromeWebDriverFactory() {
        // Конструктор оставлен пустым намеренно
    }

    /**
     * Настраивает системные свойства при использовании локального ChromeDriver.
     * <p>
     * При наличии корректного пути Selenium Manager отключается, а драйвер
     * берётся из указанного файла.
     * </p>
     */
    private void configureDriverPath() {
        if (driverPath != null && !driverPath.isBlank() && Files.isExecutable(Path.of(driverPath))) {
            System.setProperty("webdriver.chrome.driver", driverPath);
            System.setProperty("SE_MANAGER_DISABLE", "true");
        }
    }

    /**
     * Создаёт {@link ChromeDriver} с набором стандартных опций.
     * <p>
     * Если путь к драйверу задан и файл существует, используется локальный
     * ChromeDriver, иначе Selenium Manager скачает его автоматически.
     * </p>
     *
     * @return сконфигурированный экземпляр ChromeDriver
     */
    @Override
    public WebDriver create() {
        configureDriverPath();

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
