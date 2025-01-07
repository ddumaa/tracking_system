package com.project.tracking_system.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

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
}