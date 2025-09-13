package com.project.tracking_system.service.belpost;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.webdriver.WebDriverFactory;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Тестирует пропуск трека при отображении предупреждения об отсутствии данных.
 */
class WebBelPostBatchServiceNoDataWarningTest {

    /**
     * Убеждаемся, что при появлении предупреждения трек пропускается.
     */
    @Test
    void shouldSkipTrackWhenWarningAppears() throws Exception {
        WebDriver driver = mock(WebDriver.class);
        when(driver.findElement(any(By.class))).thenThrow(NoSuchElementException.class);

        WebElement warning = mock(WebElement.class);
        when(warning.isDisplayed()).thenReturn(true);
        when(warning.getAttribute("class")).thenReturn("alert-message alert-message--warning");
        when(warning.getText()).thenReturn("У нас пока нет данных");

        try (MockedConstruction<WebDriverWait> mockWait = Mockito.mockConstruction(WebDriverWait.class,
                (wait, context) -> when(wait.until(any())).thenReturn(warning))) {

            WebBelPostBatchService service = new WebBelPostBatchService(mock(WebDriverFactory.class));
            ReflectionTestUtils.setField(service, "maxAttempts", 1);
            ReflectionTestUtils.setField(service, "retryDelayMs", 0L);

            TrackInfoListDTO dto = service.parseTrack(driver, "123");

            assertTrue(dto.getList().isEmpty(), "Трек не должен содержать событий");
        }
    }
}

