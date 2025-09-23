package com.project.tracking_system.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Конфигурация асинхронного выполнения задач.
 * <p>
 * Этот класс настраивает пул потоков для асинхронных задач с использованием {@link ThreadPoolTaskExecutor}.
 * Пул потоков настраивается с начальными параметрами для максимального количества потоков, размера очереди и префикса имени потоков.
 * </p>
 *
 * @author Dmitriy Anisimov
 * @date 07.01.2025
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Создает и настраивает {@link Executor} для асинхронных задач.
     * <p>
     * Пул потоков {@link ThreadPoolTaskExecutor} используется для асинхронного выполнения задач,
     * что позволяет улучшить производительность за счет параллельного выполнения.
     * </p>
     *
     * @return Настроенный {@link Executor}.
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

    @Bean(name = "BelPost")
    public Executor postExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("Bel-");
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
     * Отдельный пул потоков для пакетной загрузки треков.
     * <p>
     * Используется при {@link com.project.tracking_system.service.track.EvropostTrackUpdateProcessor}
     * для выполнения запросов Европочты параллельно. Ограничения по потокам
     * задают верхнюю границу нагрузки от одновременных загрузок.
     * </p>
     *
     * @return executor для задач пакетной загрузки
     */
    @Bean(name = "batchUploadExecutor")
    public TaskExecutor batchUploadExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4); // минимально 4 потока для повышения пропускной способности
        executor.setMaxPoolSize(12); // увеличиваем верхнюю границу параллелизма
        executor.setQueueCapacity(500); // расширенная очередь для пиковых нагрузок
        executor.setThreadNamePrefix("BatchUpload-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy()); // не отклоняем задачи при перегрузке
        executor.initialize();
        return executor;
    }
}