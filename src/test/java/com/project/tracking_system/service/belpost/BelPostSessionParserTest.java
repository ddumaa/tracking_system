package com.project.tracking_system.service.belpost;

import com.project.tracking_system.webdriver.WebDriverFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    private BelPostSessionParser parser;

    /**
     * Подготавливает мок-объекты и создаёт тестируемый сервис перед каждым тестом.
     */
    @BeforeEach
    void init() {
        // фабрика должна вернуть наш мок драйвера
        when(factory.create()).thenReturn(driver);
        // любые попытки найти элемент приводят к исключению,
        // чтобы не требовался настоящий браузер
        when(driver.findElement(any(By.class))).thenThrow(new NoSuchElementException("mock"));

        // создаём экземпляр парсера вручную после настройки мока
        parser = new BelPostSessionParser(factory);
    }

    /**
     * Убеждаемся, что при многократном вызове {@link BelPostSessionParser#parseTrack(String)}
     * драйвер создаётся только один раз.
     */
    @Test
    void parseTrack_ReusesSingleDriver() {
        parser.parseTrack("111");
        parser.parseTrack("222");

        verify(factory, times(1)).create();
    }

    /**
     * Проверяем, что метод {@link BelPostSessionParser#cleanup()} корректно завершает сессию.
     */
    @Test
    void cleanup_QuitsDriver() {
        parser.cleanup();
        verify(driver).quit();
    }
}
