package com.project.tracking_system.service.belpost;

import com.project.tracking_system.dto.TrackInfoDTO;
import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.utils.RateLimiter;
import com.project.tracking_system.utils.RetryUtils;
import com.project.tracking_system.webdriver.WebDriverPool;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Сервис для автоматизации процесса отслеживания посылок на сайте BelPost.
 * <p>
 * Этот сервис используется для автоматического отслеживания посылок на сайте BelPost с использованием {@link Selenium WebDriver}.
 * Запросы к сайту выполняются с паузой 2–3 секунды, чтобы не превышать допустимую частоту.
 * В процессе отслеживания извлекается информация о статусах посылки.
 * </p>
 *
 * @author Dmitriy Anisimov
 * @date 07.01.2025
 */
@RequiredArgsConstructor
@Service
@Slf4j
public class WebBelPost {

    private final WebDriverPool webDriverPool;
    /** Лимитер запросов к Belpost с интервалом 2–3 секунды. */
    private final RateLimiter rateLimiter;

    /** Максимальное число попыток выполнения веб-автоматизации. */
    private static final int MAX_ATTEMPTS = 3;
    /** Базовая задержка между попытками в миллисекундах. */
    private static final long BASE_DELAY_MS = 1000L;

    /**
     * Асинхронно выполняет процесс отслеживания посылки на сайте BelPost.
     *
     * @param number Номер посылки для отслеживания.
     * @return CompletableFuture с объектом TrackInfoListDTO, содержащим информацию о статусе посылки.
     */
    @Async("Post")
    public CompletableFuture<TrackInfoListDTO> webAutomationAsync(String number) {
        TrackInfoListDTO trackInfoListDTO = webAutomation(number);
        return CompletableFuture.completedFuture(trackInfoListDTO);
    }

    /**
     * Выполняет процесс отслеживания посылки на сайте BelPost.
     * <p>
     * Метод автоматизирует взаимодействие с веб-страницей, заполняя поле ввода
     * и извлекая информацию о статусе посылки.
     *
     * @param number Номер посылки для отслеживания.
     * @return TrackInfoListDTO объект, содержащий список статусов посылки.
     */
    public TrackInfoListDTO webAutomation(String number) {
        try {
            // Выполняем автоматизацию с повторными попытками при неудаче
            return RetryUtils.runWithRetry(
                    () -> performAutomation(number),
                    MAX_ATTEMPTS,
                    BASE_DELAY_MS,
                    TimeoutException.class,
                    WebDriverException.class
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Поток прерван во время веб-автоматизации: {}", e.getMessage(), e);
            return new TrackInfoListDTO();
        } catch (Exception e) {
            log.error("Ошибка веб-автоматизации BelPost: {}", e.getMessage(), e);
            return new TrackInfoListDTO();
        }
    }

    /**
     * Выполняет одну попытку автоматизации отслеживания на сайте BelPost.
     * <p>
     * Создаёт драйвер, подготавливает страницу и собирает статусы посылки.
     * </p>
     *
     * @param number номер посылки
     * @return список статусов посылки
     * @throws InterruptedException если поток был прерван при ожидании лимитера
     */
    private TrackInfoListDTO performAutomation(String number) throws InterruptedException {
        WebDriver driver = null;
        try {
            // При каждом запросе делаем паузу 2–3 секунды (настройка — в RateLimiter)
            rateLimiter.acquire();
            // Берём драйвер из пула
            driver = webDriverPool.borrowDriver();

            // Ограничиваем частоту переходов на сайт Belpost
            rateLimiter.acquire();

            // Передаём ссылку + номер
            String url = "https://belpost.by/Otsleditotpravleniye?number=" + number;
            driver.get(url);

            // Ожидание загрузки страницы с результатами отслеживания
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            wait.until(ExpectedConditions.titleContains("Отследить отправление"));

            // Ожидание появления информации о статусе посылки
            WebElement trackItem = wait.until(
                    ExpectedConditions.visibilityOfElementLocated(By.cssSelector("article.track-item"))
            );

            // Ожидание и развертывание заголовка информации о посылке
            WebElement trackItemHeader = driver.findElement(By.cssSelector("app-track-item header"));
            if (!"true".equals(trackItemHeader.getAttribute("aria-expanded"))) {
                JavascriptExecutor js = (JavascriptExecutor) driver;
                js.executeScript(
                        "arguments[0].scrollIntoView({block: 'center', inline: 'center'});",
                        trackItemHeader
                );

                wait.until(ExpectedConditions.elementToBeClickable(trackItemHeader));
                js.executeScript("arguments[0].click();", trackItemHeader);

                WebDriverWait waitExpand = new WebDriverWait(driver, Duration.ofSeconds(10));
                waitExpand.until(ExpectedConditions.attributeToBe(trackItemHeader, "aria-expanded", "true"));
            }

            // Извлечение информации о статусах посылки
            WebElement trackDetails = trackItem.findElement(By.cssSelector("dl.track-item__details"));
            List<WebElement> trackItems = trackDetails.findElements(By.cssSelector("div.track-details__item"));

            TrackInfoListDTO result = new TrackInfoListDTO();
            for (WebElement trackItemElement : trackItems) {
                String title = trackItemElement.findElement(By.cssSelector("dt")).getText();
                WebElement contentElement = trackItemElement.findElement(By.cssSelector("dd"));

                WebElement dateElement = contentElement.findElement(By.cssSelector("li.text-secondary"));
                String dateContent = dateElement.getText();
                TrackInfoDTO trackInfoDTO = new TrackInfoDTO(dateContent, title);
                result.addTrackInfo(trackInfoDTO);
            }
            return result;
        } finally {
            if (driver != null) {
                webDriverPool.returnDriver(driver);
            }
        }
    }
}