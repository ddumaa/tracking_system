package com.project.tracking_system.service.belpost;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.webdriver.WebDriverFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openqa.selenium.*;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Проверка обработки ситуации, когда Белпочта не предоставляет данные по треку.
 */
@ExtendWith(MockitoExtension.class)
class WebBelPostBatchServiceNoDataWarningTest {

    @Mock
    private WebDriverFactory factory;
    @Mock
    private WebDriver driver;
    @Mock
    private WebElement warning;

    private WebBelPostBatchService service;

    @BeforeEach
    void setUp() {
        service = new WebBelPostBatchService(factory);
        // Устанавливаем одну попытку, чтобы цикл не повторялся
        ReflectionTestUtils.setField(service, "maxAttempts", 1);
    }

    @Test
    void parseTrack_WarningShown_ReturnsEmpty() {
        // Драйвер сообщает о наличии предупреждения «данных нет»
        when(driver.findElement(any(By.class))).thenAnswer(invocation -> {
            By by = invocation.getArgument(0, By.class);
            if ("By.cssSelector: .alert-message.alert-message--warning".equals(by.toString())) {
                return warning;
            }
            throw new NoSuchElementException("not found");
        });
        when(warning.isDisplayed()).thenReturn(true);
        when(warning.getText()).thenReturn("У нас пока нет данных об этом трек-номере. Попробуйте проверить позже");
        // Вызов метода с подменённым драйвером
        TrackInfoListDTO dto = service.parseTrack(driver, "PC123456789BY");
        assertTrue(dto.getList().isEmpty());
    }
}
