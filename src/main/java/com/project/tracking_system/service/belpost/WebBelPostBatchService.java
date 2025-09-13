package com.project.tracking_system.service.belpost;

import com.project.tracking_system.dto.TrackInfoDTO;
import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.webdriver.WebDriverFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Value;
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
     * Задержка между повторными запросами к сайту Белпочты (мс).
     * Позволяет избежать блокировок при превышении лимитов.
     */
    @Value("${belpost.retry.delay-ms:60000}")
    private long retryDelayMs;

    /**
     * Максимальное число попыток запроса информации о треке.
     * Позволяет ограничить длительность обработки при ошибках.
     */
    @Value("${belpost.retry.max-attempts:2}")
    private int maxAttempts;

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

        // Пытаемся создать WebDriver и корректно обработать возможные ошибки инициализации
        WebDriver driver;
        try {
            driver = webDriverFactory.create();
        } catch (WebDriverException e) {
            log.error("❌ Не удалось создать WebDriver: {}", e.getMessage(), e);
            // Возвращаем пустой результат, чтобы вызывающий код мог продолжить работу
            return result;
        }

        try {
            for (String number : trackNumbers) {
                result.put(number, parseTrack(driver, number));
            }
        } finally {
            // Закрываем драйвер в блоке finally, чтобы гарантировать освобождение ресурсов
            driver.quit();
        }
        return result;
    }

    /**
     * Загружает информацию по одному трек-номеру.
     * <p>
     * Для каждого вызова создаётся отдельный {@link WebDriver},
     * который закрывается после завершения парсинга.
     * </p>
     *
     * @param trackNumber номер Белпочты
     * @return данные о треке или пустой DTO при ошибке
     */
    public TrackInfoListDTO parseTrack(String trackNumber) {
        Map<String, TrackInfoListDTO> map = processBatch(List.of(trackNumber));
        return map.getOrDefault(trackNumber, new TrackInfoListDTO());
    }

    /**
     * Пытается получить данные по треку с указанным номером, повторяя запрос
     * при возникновении временных ошибок.
     * <p>Метод предполагает, что драйвер создаётся и закрывается вызывающей
     * стороной. {@link WebDriver} не потокобезопасен, поэтому передавать
     * один экземпляр между потоками нельзя.</p>
     *
     * @param driver активный экземпляр {@link WebDriver}
     * @param number трек-номер Белпочты
     * @return список событий трека или пустой объект при неудаче
     */
    public TrackInfoListDTO parseTrack(WebDriver driver, String number) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return tryParseTrack(driver, number);
            } catch (RateLimitException e) {
                log.warn("⏳ Лимит запросов — ждём {} сек. перед повтором ({}): {}", retryDelayMs / 1000, number, e.getMessage());
                sleep(retryDelayMs);
            } catch (TimeoutException e) {
                log.warn("⏳ TimeoutException на {} попытке для {}. Ждём {} сек...", attempt, number, retryDelayMs / 1000);
                sleep(retryDelayMs);
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

        // Если Белпочта ещё не внесла данные по этому треку, возвращаем пустой результат
        if (isNoDataWarningDisplayed(driver)) {
            log.debug("Предупреждение об отсутствии данных для номера {}", number);
            return dto;
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

    /**
     * Проверяет, появилось ли сообщение об отсутствии данных по трек-номеру.
     *
     * @param driver активный {@link WebDriver}
     * @return {@code true}, если отображается предупреждение
     */
    private boolean isNoDataWarningDisplayed(WebDriver driver) {
        try {
            WebElement warning = driver.findElement(By.cssSelector(".alert-message.alert-message--warning"));
            return warning.isDisplayed()
                    && warning.getText().contains("У нас пока нет данных");
        } catch (NoSuchElementException e) {
            return false;
        }
    }

    /**
     * Проверяет, отображается ли на странице сообщение о превышении лимита запросов.
     *
     * @param driver активный {@link WebDriver}
     * @return {@code true}, если сообщение о лимите найдено
     */
    private boolean isRateLimitErrorDisplayed(WebDriver driver) {
        try {
            WebElement errorPopup = driver.findElement(By.cssSelector(".swal2-title"));
            WebElement errorText = driver.findElement(By.cssSelector("#swal2-content"));
            return errorPopup.isDisplayed() && errorText.getText().contains("Превышено количество запросов");
        } catch (NoSuchElementException e) {
            return false;
        }
    }

    /**
     * Исключение, сигнализирующее о превышении лимита запросов Белпочты.
     */
    public class RateLimitException extends Exception {
        public RateLimitException(String message) {
            super(message);
        }
    }

    /**
     * Приостанавливает текущий поток на указанное число миллисекунд.
     *
     * @param millis задержка в миллисекундах
     */
    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

}