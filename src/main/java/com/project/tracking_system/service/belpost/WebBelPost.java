package com.project.tracking_system.service.belpost;

import com.project.tracking_system.dto.TrackInfoDTO;
import com.project.tracking_system.dto.TrackInfoListDTO;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class WebBelPost {

    @Async("Post")
    public CompletableFuture<TrackInfoListDTO> webAutomationAsync(String number) {
        TrackInfoListDTO trackInfoListDTO = webAutomation(number);
        return CompletableFuture.completedFuture(trackInfoListDTO);
    }

    public TrackInfoListDTO webAutomation(String number) {
        WebDriver driver = null;
        TrackInfoListDTO trackInfoListDTO = new TrackInfoListDTO();
        try {
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless");
            options.addArguments("--disable-gpu");
            options.addArguments("--window-size=1920,1080");
            options.addArguments("--ignore-certificate-errors");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--remote-debugging-port=9222");

            driver = new ChromeDriver(options);
            driver.get("https://belpost.by/Otsleditotpravleniye");

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            WebElement inputField = wait.until(ExpectedConditions.
                    visibilityOfElementLocated(By.name("number")));
            inputField.sendKeys(number);

            WebElement button = driver.findElement(By.xpath("//button[@type='submit']"));
            button.click();

            WebDriverWait wait2 = new WebDriverWait(driver, Duration.ofSeconds(10));
            wait2.until(ExpectedConditions.titleContains("Отследить отправление"));

            WebElement trackItem = wait2.until(ExpectedConditions.
                    visibilityOfElementLocated(By.cssSelector("article.track-item")));

            WebElement trackItemHeader = driver.findElement(By.cssSelector("app-track-item header"));

            if (!trackItemHeader.getAttribute("aria-expanded").equals("true")) {
                JavascriptExecutor js = (JavascriptExecutor) driver;
                js.executeScript("arguments[0]." +
                        "scrollIntoView({block: 'center', inline: 'center'});", trackItemHeader);

                wait2.until(ExpectedConditions.elementToBeClickable(trackItemHeader));

                js.executeScript("arguments[0].click();", trackItemHeader);

                WebDriverWait wait3 = new WebDriverWait(driver, Duration.ofSeconds(10));
                wait3.until(ExpectedConditions.
                        attributeToBe(trackItemHeader, "aria-expanded", "true"));
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
            System.err.println("Error: " + e.getMessage());
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
        return trackInfoListDTO;
    }
}