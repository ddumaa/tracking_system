package com.project.tracking_system.webdriver;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Async;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Пул для повторного использования экземпляров {@link WebDriver}.
 * <p>
 * Инициализируется фиксированным количеством драйверов, созданных через
 * {@link WebDriverFactory}. Драйверы помещаются в потокобезопасную очередь
 * и выдаются по запросу. При завершении работы приложения все драйверы
 * корректно закрываются.
 * </p>
 */
@Slf4j
@Component
public class WebDriverPool {

    /** Очередь свободных драйверов. */
    private final BlockingQueue<WebDriver> pool;

    /** Фабрика для создания экземпляров WebDriver. */
    private final WebDriverFactory webDriverFactory;

    /** Размер пула драйверов. */
    private final int poolSize;

    /**
     * Создаёт пул указанного размера и заполняет его драйверами.
     *
     * @param webDriverFactory фабрика для создания драйверов
     * @param poolSize         максимальное количество драйверов в пуле
     */
    public WebDriverPool(WebDriverFactory webDriverFactory,
                         @Value("${webdriver.pool.size:2}") int poolSize) {
        this.webDriverFactory = webDriverFactory;
        this.poolSize = poolSize;
        this.pool = new LinkedBlockingQueue<>(poolSize);
    }

    /**
     * Асинхронно заполняет пул созданными драйверами после инициализации бина.
     * <p>
     * Метод помечен {@link Async}, поэтому создание драйверов не блокирует запуск
     * приложения. Каждая ошибка при создании драйвера логируется, не прерывая
     * заполнение пула.
     * </p>
     */
    @PostConstruct
    @Async("webDriverExecutor")
    public void populatePool() {
        for (int i = 0; i < poolSize; i++) {
            try {
                pool.add(webDriverFactory.create());
            } catch (Exception e) {
                log.warn("Не удалось создать WebDriver: {}", e.getMessage(), e);
            }
        }
        log.info("Создан пул WebDriver на {} экземпляров", poolSize);
    }

    /**
     * Берёт драйвер из пула, блокируясь при отсутствии свободных.
     *
     * @return свободный {@link WebDriver}
     * @throws InterruptedException если поток был прерван во время ожидания
     */
    public WebDriver borrowDriver() throws InterruptedException {
        return pool.take();
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
     * Завершает работу пула, закрывая все драйверы.
     */
    @PreDestroy
    public void shutdown() {
        while (!pool.isEmpty()) {
            WebDriver driver = pool.poll();
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception e) {
                    log.warn("Ошибка закрытия WebDriver: {}", e.getMessage(), e);
                }
            }
        }
        log.info("Пул WebDriver успешно остановлен");
    }
}
