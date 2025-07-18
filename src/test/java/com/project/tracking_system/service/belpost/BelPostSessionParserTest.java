package com.project.tracking_system.service.belpost;

import com.project.tracking_system.webdriver.WebDriverFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.By;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Проверка повторного использования WebDriver в {@link BelPostSessionParser}.
 */
@ExtendWith(MockitoExtension.class)
class BelPostSessionParserTest {

    @Mock
    private WebDriverFactory factory;
    @Mock
    private WebDriver driver;

    @InjectMocks
    private BelPostSessionParser parser;

    @BeforeEach
    void init() {
        when(factory.create()).thenReturn(driver);
        when(driver.findElement(any(By.class))).thenThrow(new NoSuchElementException("mock"));
    }

    @Test
    void parseTrack_ReusesSingleDriver() {
        parser.parseTrack("111");
        parser.parseTrack("222");

        verify(factory, times(1)).create();
    }

    @Test
    void cleanup_QuitsDriver() {
        parser.cleanup();
        verify(driver).quit();
    }
}
