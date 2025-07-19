package com.project.tracking_system.service.belpost;

import com.project.tracking_system.dto.TrackInfoDTO;
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
            for (String number : trackNumbers) {
                result.put(number, parseTrack(driver, number));
            }
        } finally {
            driver.quit();
        }
        return result;
    }

    private TrackInfoListDTO parseTrack(WebDriver driver, String number) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                return tryParseTrack(driver, number);
            } catch (RateLimitException e) {
                log.warn("⏳ Лимит запросов — ждём 60 секунд перед повтором ({}): {}", number, e.getMessage());
                sleep(60_000);
            } catch (TimeoutException e) {
                log.warn("⏳ TimeoutException на {} попытке для {}. Ждём 60 секунд...", attempt, number);
                sleep(60_000);
            } catch (Exception e) {
                log.error("❌ Ошибка при парсинге {} на попытке {}: {}", number, attempt, e.getMessage(), e);
            }
        }

        return new TrackInfoListDTO();
    }

    /**
     * Парсит один трек-номер, используя уже инициализированный драйвер.
     */
    private TrackInfoListDTO tryParseTrack(WebDriver driver, String number) throws Exception {
        TrackInfoListDTO dto = new TrackInfoListDTO();

        String url = "https://belpost.by/Otsleditotpravleniye?number=" + number;
        driver.get(url);

        // СРАЗУ проверяем ошибку лимита
        if (isRateLimitErrorDisplayed(driver)) {
            throw new RateLimitException("Превышено количество запросов");
        }

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(12));
        WebElement trackItem = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.cssSelector("article.track-item")));

        WebElement trackItemHeader = trackItem.findElement(By.cssSelector("header"));
        if (!"true".equals(trackItemHeader.getAttribute("aria-expanded"))) {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript("arguments[0].scrollIntoView({block: 'center', inline: 'center'});", trackItemHeader);
            wait.until(ExpectedConditions.elementToBeClickable(trackItemHeader));
            js.executeScript("arguments[0].click();", trackItemHeader);
            wait.until(ExpectedConditions.attributeToBe(trackItemHeader, "aria-expanded", "true"));
        }

        wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.cssSelector("dl.track-item__details .track-details__item")));

        WebElement trackDetails = trackItem.findElement(By.cssSelector("dl.track-item__details"));
        List<WebElement> trackItems = trackDetails.findElements(By.cssSelector("div.track-details__item"));

        for (WebElement item : trackItems) {
            String title = item.findElement(By.cssSelector("dt")).getText().trim();
            String date = item.findElement(By.cssSelector("dd li.text-secondary")).getText().trim();
            dto.addTrackInfo(new TrackInfoDTO(date, title));
        }

        return dto;
    }

    private boolean isRateLimitErrorDisplayed(WebDriver driver) {
        try {
            WebElement errorPopup = driver.findElement(By.cssSelector(".swal2-title"));
            WebElement errorText = driver.findElement(By.cssSelector("#swal2-content"));
            return errorPopup.isDisplayed() && errorText.getText().contains("Превышено количество запросов");
        } catch (NoSuchElementException e) {
            return false;
        }
    }

    public class RateLimitException extends Exception {
        public RateLimitException(String message) {
            super(message);
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

}