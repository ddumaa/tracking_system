package com.project.tracking_system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Основной класс приложения отслеживания.
 * <p>
 * Отвечает за инициализацию и запуск Spring Boot.
 */
@SpringBootApplication
@EnableScheduling
public class TrackingSystemApplication {

    /**
     * Точка входа в приложение.
     *
     * @param args аргументы командной строки
     */
    public static void main(String[] args) {
        SpringApplication.run(TrackingSystemApplication.class, args);
    }

}
