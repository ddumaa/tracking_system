package com.project.tracking_system.service.jsonEvropostService;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.project.tracking_system.dto.ResolvedCredentialsDTO;
import com.project.tracking_system.model.evropost.jsonRequestModel.JsonRequest;
import com.project.tracking_system.service.user.UserService;
import com.project.tracking_system.utils.UserCredentialsResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Тесты для {@link JsonEvroTrackingService}, проверяющие выбор учётных данных
 * и корректность логирования разных сценариев.
 */
@ExtendWith(MockitoExtension.class)
class JsonEvroTrackingServiceTest {

    @Mock
    private JsonHandlerService jsonHandlerService;
    @Mock
    private RequestFactory requestFactory;
    @Mock
    private UserService userService;
    @Mock
    private UserCredentialsResolver userCredentialsResolver;

    @InjectMocks
    private JsonEvroTrackingService jsonEvroTrackingService;

    private Logger logger;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(JsonEvroTrackingService.class);
    }

    /**
     * Проверяет, что при включённом флаге и корректном userId
     * используются личные креды и фиксируется соответствующий лог.
     */
    @Test
    void whenCustomCredentialsEnabledAndUserIdProvided_thenPersonalCredentialsUsed() {
        Long userId = 1L;
        ResolvedCredentialsDTO creds = new ResolvedCredentialsDTO("jwt", "svc");

        when(userService.isUsingCustomCredentials(userId)).thenReturn(true);
        when(userService.resolveCredentials(userId)).thenReturn(creds);
        JsonRequest request = new JsonRequest();
        when(requestFactory.createTrackingRequest(eq("jwt"), eq("svc"), anyString())).thenReturn(request);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode node = mapper.createObjectNode();
        node.putArray("Table");
        when(jsonHandlerService.jsonRequest(request)).thenReturn(node);

        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        jsonEvroTrackingService.getJson(userId, "123");

        verify(userService).resolveCredentials(userId);
        verify(userCredentialsResolver, never()).getSystemCredentials();
        assertTrue(appender.list.stream()
                .anyMatch(event -> event.getLevel() == Level.INFO &&
                        event.getFormattedMessage().contains("Используем личные учётные данные пользователя ID=1")));

        logger.detachAppender(appender);
    }

    /**
     * Проверяет, что при включённом флаге и отсутствии личных кредитов
     * сервис пишет WARN и выбрасывает исключение.
     */
    @Test
    void whenCustomCredentialsEnabledButMissing_thenWarnAndThrow() {
        Long userId = 1L;

        when(userService.isUsingCustomCredentials(userId)).thenReturn(true);
        when(userService.resolveCredentials(userId)).thenReturn(null);

        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        assertThrows(IllegalStateException.class, () -> jsonEvroTrackingService.getJson(userId, "123"));

        assertTrue(appender.list.stream()
                .anyMatch(event -> event.getLevel() == Level.WARN &&
                        event.getFormattedMessage().contains("Личные учётные данные отсутствуют для пользователя ID=1")));
        verify(userCredentialsResolver, never()).getSystemCredentials();

        logger.detachAppender(appender);
    }

    /**
     * Проверяет, что при выключенном флаге сервис обращается к системным кредам
     * и пишет об этом информационный лог.
     */
    @Test
    void whenCustomCredentialsDisabled_thenSystemCredentialsUsed() {
        Long userId = 1L;
        ResolvedCredentialsDTO creds = new ResolvedCredentialsDTO("jwtSys", "svcSys");

        when(userService.isUsingCustomCredentials(userId)).thenReturn(false);
        when(userCredentialsResolver.getSystemCredentials()).thenReturn(creds);
        JsonRequest request = new JsonRequest();
        when(requestFactory.createTrackingRequest(eq("jwtSys"), eq("svcSys"), anyString())).thenReturn(request);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode node = mapper.createObjectNode();
        node.putArray("Table");
        when(jsonHandlerService.jsonRequest(request)).thenReturn(node);

        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        jsonEvroTrackingService.getJson(userId, "123");

        verify(userCredentialsResolver).getSystemCredentials();
        verify(userService, never()).resolveCredentials(anyLong());
        assertTrue(appender.list.stream()
                .anyMatch(event -> event.getLevel() == Level.INFO &&
                        event.getFormattedMessage().contains("интеграция с личными кредами отключена, используем системные")));

        logger.detachAppender(appender);
    }
}

