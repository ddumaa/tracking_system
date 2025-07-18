package com.project.tracking_system.webdriver;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Component;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Менеджер повторного использования экземпляров {@link WebDriver}.
 * <p>
 * Хранит созданные драйверы в очереди и отдаёт их по запросу, создавая новые
 * при отсутствии свободных. При завершении работы приложения все сохранённые
 * драйверы корректно завершаются.
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class WebDriverPool {

    /** Фабрика для непосредственного создания драйверов. */
    private final WebDriverFactory webDriverFactory;

    /** Очередь свободных драйверов. */
    private final Queue<WebDriver> pool = new ConcurrentLinkedQueue<>();

    /**
     * Предоставляет драйвер для использования.
     * <p>
     * Если в пуле нет свободных экземпляров, создаётся новый через фабрику.
     * </p>
     *
     * @return экземпляр {@link WebDriver}
     */
    public WebDriver borrowDriver() {
        WebDriver driver = pool.poll();
        if (driver == null) {
            driver = webDriverFactory.create();
        }
        return driver;
    }

    /**
     * Возвращает драйвер обратно в пул.
     *
     * @param driver использованный драйвер
     */
    public void returnDriver(WebDriver driver) {
        if (driver != null) {
            pool.offer(driver);
        }
    }

    /**
     * Завершает работу всех драйверов при остановке приложения.
     */
    @PreDestroy
    public void shutdown() {
        for (WebDriver driver : pool) {
            try {
                driver.quit();
            } catch (Exception e) {
                log.warn("Не удалось корректно завершить драйвер: {}", e.getMessage(), e);
            }
        }
        pool.clear();
    }
}
