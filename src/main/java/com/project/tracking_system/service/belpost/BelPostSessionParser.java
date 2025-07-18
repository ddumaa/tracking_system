package com.project.tracking_system.service.belpost;

import com.project.tracking_system.dto.TrackInfoDTO;
import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.webdriver.WebDriverFactory;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * Сервис, который поддерживает единственный сеанс {@link WebDriver} для работы
 * с сайтом Белпочты.
 * <p>
 * Экземпляр драйвера создаётся один раз при инициализации бина и
 * переиспользуется при парсинге различных трек‑номеров.
 * </p>
 */
@Service
@Slf4j
public class BelPostSessionParser {

    /** Браузерный драйвер, используемый для всех запросов. */
    private final WebDriver driver;

    /**
     * Создаёт сервис с единственным экземпляром драйвера.
     *
     * @param webDriverFactory фабрика для создания драйвера
     */
    public BelPostSessionParser(WebDriverFactory webDriverFactory) {
        this.driver = webDriverFactory.create();
        // загружаем страницу отслеживания один раз
        this.driver.get("https://belpost.by/Otsleditotpravleniye");
    }

    /**
     * Парсит информацию о посылке по указанному номеру.
     *
     * <p>Перед отправкой запроса поле ввода очищается, чтобы не
     * остались данные от предыдущего поиска.</p>
     *
     * @param number номер трека
     * @return список статусов посылки
     */
    public synchronized TrackInfoListDTO parseTrack(String number) {
        TrackInfoListDTO trackInfoListDTO = new TrackInfoListDTO();
        try {
            // Очищаем и заполняем поле с трек‑номером
            WebElement input = driver.findElement(By.cssSelector("input[name='barcode']"));
            input.clear();
            input.sendKeys(number);
            input.sendKeys(Keys.ENTER);

            // Ожидаем появления результатов
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            WebElement trackItem = wait.until(
                    ExpectedConditions.visibilityOfElementLocated(By.cssSelector("article.track-item")));

            WebElement trackItemHeader = driver.findElement(By.cssSelector("app-track-item header"));
            if (!"true".equals(trackItemHeader.getAttribute("aria-expanded"))) {
                JavascriptExecutor js = (JavascriptExecutor) driver;
                js.executeScript("arguments[0].scrollIntoView({block: 'center', inline: 'center'});",
                        trackItemHeader);
                wait.until(ExpectedConditions.elementToBeClickable(trackItemHeader));
                js.executeScript("arguments[0].click();", trackItemHeader);

                WebDriverWait wait3 = new WebDriverWait(driver, Duration.ofSeconds(10));
                wait3.until(ExpectedConditions.attributeToBe(trackItemHeader, "aria-expanded", "true"));
            }

            WebElement trackDetails = trackItem.findElement(By.cssSelector("dl.track-item__details"));
            List<WebElement> trackItems = trackDetails.findElements(By.cssSelector("div.track-details__item"));

            for (WebElement trackItemElement : trackItems) {
                String title = trackItemElement.findElement(By.cssSelector("dt")).getText();
                WebElement contentElement = trackItemElement.findElement(By.cssSelector("dd"));
                WebElement dateElement = contentElement.findElement(By.cssSelector("li.text-secondary"));
                String dateContent = dateElement.getText();
                TrackInfoDTO trackInfoDTO = new TrackInfoDTO(dateContent, title);
                trackInfoListDTO.addTrackInfo(trackInfoDTO);
            }
        } catch (Exception e) {
            log.error("Ошибка при парсинге BelPost: {}", e.getMessage(), e);
        }
        return trackInfoListDTO;
    }

    /**
     * Завершает работу драйвера при выключении приложения.
     */
    @PreDestroy
    public void cleanup() {
        driver.quit();
    }
}
