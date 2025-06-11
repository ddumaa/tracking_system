package com.project.tracking_system.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestTemplate;

/**
 * Конфигурационный класс для приложения.
 * <p>
 * Этот класс содержит бин-конфигурации, которые необходимы для работы приложения,
 * включая создание экземпляров {@link RestTemplate} и {@link ObjectMapper}.
 * </p>
 *
 * @author Dmitriy Anisimov
 * @date 07.01.2025
 */
@ComponentScan(basePackages = "com.project.tracking_system")
@Configuration
public class AppConfiguration {

    /**
     * Создает бин {@link RestTemplate} для выполнения HTTP-запросов.
     * <p>
     * {@link RestTemplate} используется для отправки запросов и получения ответов от внешних сервисов.
     * </p>
     *
     * @return Экземпляр {@link RestTemplate}.
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /**
     * Создает бин {@link ObjectMapper} для работы с JSON данными.
     * <p>
     * {@link ObjectMapper} используется для сериализации и десериализации JSON данных в Java объекты.
     * </p>
     *
     * @return Экземпляр {@link ObjectMapper}.
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }


    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
}