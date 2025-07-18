package com.project.tracking_system.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.Executor;

/**
 * Конфигурация асинхронного выполнения задач.
 * <p>
 * Этот класс настраивает пулы потоков для асинхронных задач с использованием
 * {@link ThreadPoolTaskExecutor}. В конфигурации определены несколько
 * исполнителей, один из которых ({@code Post}) используется сервисом отправки
 * почтовых сообщений.
 * </p>
 *
 * @author Dmitriy Anisimov
 * @date 07.01.2025
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /** Пул потоков для обработки XLS-файлов. */
    @Value("${xls.executor.core-pool-size:5}")
    private int xlsCorePoolSize;

    /** Максимальное число потоков для пула XLS. */
    @Value("${xls.executor.max-pool-size:10}")
    private int xlsMaxPoolSize;

    /** Размер очереди задач для пула XLS. */
    @Value("${xls.executor.queue-capacity:100}")
    private int xlsQueueCapacity;

    /** Размер пула потоков для создания WebDriver. */
    @Value("${webdriver.executor.pool-size:1}")
    private int webDriverExecutorPoolSize;

    /**
     * Создает и настраивает {@link Executor} для асинхронных задач.
     * <p>
     * Пул {@link ThreadPoolTaskExecutor} используется для операций, требующих
     * асинхронности. В частности, этот executor применяется сервисом отправки
     * электронной почты, что позволяет ограничить количество потоков, участвующих
     * в рассылке.
     * </p>
     *
     * @return настроенный {@link Executor}
     */
    @Bean(name="Post")
    public Executor asyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5); // минимальное количество потоков
        executor.setMaxPoolSize(10); // максимальное количество потоков
        executor.setQueueCapacity(100); // размер очереди задач
        executor.setThreadNamePrefix("Post"); // префикс для имен потоков
        executor.initialize();
        return executor;
    }

    /**
     * Создаёт пул потоков для {@link com.project.tracking_system.service.track.TrackUpdateService}.
     * <p>
     * Используется только для обновления треков, чтобы разграничить нагрузку от других
     * асинхронных задач.
     * </p>
     *
     * @return настроенный {@link TaskExecutor} для сервиса обновления треков
     */
    @Bean(name = "trackExecutor")
    public TaskExecutor trackExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5); // минимальное количество потоков
        executor.setMaxPoolSize(10); // максимальное количество потоков
        executor.setQueueCapacity(100); // размер очереди задач
        executor.setThreadNamePrefix("TrackUpdate-"); // префикс для имен потоков
        executor.initialize();
        return executor;
    }

    /**
     * Создаёт пул потоков для асинхронного заполнения {@link com.project.tracking_system.webdriver.WebDriverPool}.
     * <p>
     * Размер пула регулируется свойством {@code webdriver.executor.pool-size}, что обеспечивает
     * гибкую настройку в зависимости от ресурсов приложения.
     * </p>
     *
     * @return настроенный {@link TaskExecutor} для создания WebDriver
     */
    @Bean(name = "webDriverExecutor")
    public TaskExecutor webDriverExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(webDriverExecutorPoolSize);
        executor.setMaxPoolSize(webDriverExecutorPoolSize);
        executor.setQueueCapacity(webDriverExecutorPoolSize);
        executor.setThreadNamePrefix("WebDriver-");
        executor.initialize();
        return executor;
    }

    /**
     * Создаёт пул потоков для {@link com.project.tracking_system.service.track.TrackingNumberServiceXLS}.
     * <p>
     * Размер пула и очередь задач задаются через свойства приложения, что обеспечивает гибкость конфигурации.
     * </p>
     *
     * @return настроенный {@link TaskExecutor} для чтения XLS-файлов
     */
    @Bean(name = "xlsExecutor")
    public TaskExecutor xlsExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(xlsCorePoolSize);
        executor.setMaxPoolSize(xlsMaxPoolSize);
        executor.setQueueCapacity(xlsQueueCapacity);
        executor.setThreadNamePrefix("XlsProcessing-");
        executor.initialize();
        return executor;
    }
}
