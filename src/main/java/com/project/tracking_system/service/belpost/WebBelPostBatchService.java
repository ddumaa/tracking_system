package com.project.tracking_system.service.belpost;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.webdriver.WebDriverFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Сервис пакетной обработки треков Белпочты через Selenium.
 * <p>
 * Создаёт единый экземпляр {@link WebDriver} и последовательно
 * парсит каждый трек, закрывая браузер по завершении.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebBelPostBatchService {

    private final WebDriverFactory webDriverFactory;

    /**
     * Обрабатывает список трек-номеров, возвращая информацию по каждому.
     *
     * @param trackNumbers список номеров Белпочты
     * @return отображение номер → данные о треке
     */
    public Map<String, TrackInfoListDTO> processBatch(List<String> trackNumbers) {
        Map<String, TrackInfoListDTO> result = new HashMap<>();
        if (trackNumbers == null || trackNumbers.isEmpty()) {
            return result;
        }

        WebDriver driver = webDriverFactory.create();
        try {
            driver.get("https://belpost.by/Otsleditotpravleniye");
            for (String number : trackNumbers) {
                result.put(number, parseTrack(driver, number));
            }
        } finally {
            driver.quit();
        }
        return result;
    }

    /**
     * Парсит один трек-номер, используя уже инициализированный драйвер.
     */
    private TrackInfoListDTO parseTrack(WebDriver driver, String number) {
        TrackInfoListDTO trackInfoListDTO = new TrackInfoListDTO();
        try {
            WebElement input = driver.findElement(By.cssSelector("input[name='barcode']"));
            input.clear();
            input.sendKeys(number);
            input.sendKeys(Keys.ENTER);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            WebElement trackItem = wait.until(
                    ExpectedConditions.visibilityOfElementLocated(By.cssSelector("article.track-item")));

            WebElement trackItemHeader = driver.findElement(By.cssSelector("app-track-item header"));
            if (!"true".equals(trackItemHeader.getAttribute("aria-expanded"))) {
                JavascriptExecutor js = (JavascriptExecutor) driver;
                js.executeScript("arguments[0].scrollIntoView({block: 'center', inline: 'center'});", trackItemHeader);
                wait.until(ExpectedConditions.elementToBeClickable(trackItemHeader));
                js.executeScript("arguments[0].click();", trackItemHeader);
                new WebDriverWait(driver, Duration.ofSeconds(10))
                        .until(ExpectedConditions.attributeToBe(trackItemHeader, "aria-expanded", "true"));
            }

            WebElement trackDetails = trackItem.findElement(By.cssSelector("dl.track-item__details"));
            List<WebElement> trackItems = trackDetails.findElements(By.cssSelector("div.track-details__item"));
            for (WebElement trackItemElement : trackItems) {
                String title = trackItemElement.findElement(By.cssSelector("dt")).getText();
                WebElement contentElement = trackItemElement.findElement(By.cssSelector("dd"));
                WebElement dateElement = contentElement.findElement(By.cssSelector("li.text-secondary"));
                String dateContent = dateElement.getText();
                trackInfoListDTO.addTrackInfo(new com.project.tracking_system.dto.TrackInfoDTO(dateContent, title));
            }
        } catch (Exception e) {
            log.error("Ошибка при парсинге BelPost для {}: {}", number, e.getMessage(), e);
        }
        return trackInfoListDTO;
    }
}
