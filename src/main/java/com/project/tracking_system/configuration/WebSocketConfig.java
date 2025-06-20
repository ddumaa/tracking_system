package com.project.tracking_system.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

/**
 * @author Dmitriy Anisimov
 * @date 19.02.2025
 */
@Slf4j
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Список разрешённых источников для WebSocket.
     */
    private final String[] allowedOrigins;

    public WebSocketConfig(@Value("${websocket.allowed-origins:*}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    /**
     * Регистрирует конечную точку STOMP для WebSocket.
     *
     * @param registry реестр конечных точек
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins);
        log.debug("✅ WebSocket endpoint /wss зарегистрирован!");
    }

    /**
     * Настраивает брокер сообщений для WebSocket.
     *
     * @param registry конфигурация брокера сообщений
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
        log.debug("✅ WebSocket Broker настроен!");
    }

}

