package com.project.tracking_system.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Конфигурационный класс для приложения.
 * <p>
 * Этот класс содержит бин-конфигурации, которые необходимы для работы приложения,
 * включая создание экземпляров {@link RestTemplate}, {@link ObjectMapper} и {@link ModelMapper}.
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

    /**
     * Создает бин {@link ModelMapper} для преобразования объектов между различными слоями приложения.
     * <p>
     * {@link ModelMapper} используется для маппинга данных между объектами, например, между моделями и DTO.
     * </p>
     *
     * @return Экземпляр {@link ModelMapper}.
     */
    @Bean
    public ModelMapper getMapper() {
        return new ModelMapper();
    }
}