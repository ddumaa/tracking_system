package com.project.tracking_system.service.belpost;

import com.project.tracking_system.webdriver.WebDriverFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openqa.selenium.WebDriver;
import static org.mockito.Mockito.*;

/**
 * Проверка того, что для каждого вызова {@link WebBelPostBatchService#parseTrack(String)}
 * создаётся новый экземпляр {@link WebDriver}.
 */
@ExtendWith(MockitoExtension.class)
class WebBelPostBatchServiceTest {

    @Mock
    private WebDriverFactory factory;
    @Mock
    private WebDriver driver1;
    @Mock
    private WebDriver driver2;

    private WebBelPostBatchService service;

    /**
     * Подготавливает окружение перед каждым тестом.
     *<p>
     * Настраивает фабрику драйверов так, чтобы при последовательных
     * вызовах {@code create()} возвращались разные объекты, имитируя
     * создание нового {@link WebDriver} для каждого трека.
     * </p>
     */
    @BeforeEach
    void init() {
        // Возвращаем разные драйверы при последовательных вызовах
        // фабрики, имитируя создание нового экземпляра для каждого
        // парсинга треков.
        when(factory.create())
                .thenReturn(driver1)
                .thenReturn(driver2);

        service = new WebBelPostBatchService(factory);
    }

    /**
     * Проверяет, что при каждом обращении к {@link WebBelPostBatchService#parseTrack(String)}
     * создаётся новый экземпляр драйвера и он корректно закрывается.
     */
    @Test
    void parseTrack_CreatesNewDriverEachCall() {
        service.parseTrack("111");
        service.parseTrack("222");

        verify(factory, times(2)).create();
        verify(driver1).quit();
        verify(driver2).quit();
    }
}
