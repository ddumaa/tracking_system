package com.project.tracking_system.service.belpost;

import com.project.tracking_system.webdriver.WebDriverFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

    @BeforeEach
    void init() {
        when(factory.create()).thenReturn(driver1, driver2);
        doNothing().when(driver1).get(anyString());
        doNothing().when(driver2).get(anyString());
        when(driver1.findElement(any(By.class))).thenThrow(new NoSuchElementException("mock"));
        when(driver2.findElement(any(By.class))).thenThrow(new NoSuchElementException("mock"));

        service = new WebBelPostBatchService(factory);
    }

    @Test
    void parseTrack_CreatesNewDriverEachCall() {
        service.parseTrack("111");
        service.parseTrack("222");

        verify(factory, times(2)).create();
        verify(driver1).quit();
        verify(driver2).quit();
    }
}
