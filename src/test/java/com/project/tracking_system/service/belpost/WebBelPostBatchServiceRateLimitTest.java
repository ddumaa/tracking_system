package com.project.tracking_system.service.belpost;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.webdriver.WebDriverFactory;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Проверяет, что при появлении уведомления о превышении лимита запросов
 * обработка трека прерывается и возвращается пустой результат.
 */
class WebBelPostBatchServiceRateLimitTest {

    /**
     * Эмулируем всплывающее окно лимита запросов и убеждаемся,
     * что трек не парсится.
     */
    @Test
    void shouldSkipWhenRateLimitAppears() {
        WebDriver driver = mock(WebDriver.class);
        when(driver.findElement(any(By.class))).thenThrow(NoSuchElementException.class);
        when(driver.findElements(any(By.class))).thenReturn(List.of());

        WebBelPostBatchService service = new WebBelPostBatchService(mock(WebDriverFactory.class));
        ReflectionTestUtils.setField(service, "maxAttempts", 1);
        ReflectionTestUtils.setField(service, "retryDelayMs", 0L);

        try (MockedConstruction<WebDriverWait> mockWait =
                     Mockito.mockConstruction(WebDriverWait.class,
                             (wait, context) -> when(wait.until(any()))
                                     .thenThrow(service.new RateLimitException("limit")))) {

            TrackInfoListDTO dto = service.parseTrack(driver, "123");
            assertTrue(dto.getList().isEmpty(),
                    "При превышении лимита запросов данные не должны парситься");
        }
    }
}

