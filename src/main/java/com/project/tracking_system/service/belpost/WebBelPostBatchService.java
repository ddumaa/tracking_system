package com.project.tracking_system.service.belpost;

import com.project.tracking_system.dto.TrackInfoDTO;
import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.webdriver.WebDriverFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;
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

    /** CSS-селектор предупреждения об отсутствии данных. */
    private static final By NO_DATA_WARNING = By.cssSelector(
            ".alert-message.alert-message--warning, app-alert-message .alert-message--warning"
    );

    /** блок трека и его детали */
    private static final By TRACK_ITEM = By.cssSelector("article.track-item");
    private static final By DETAILS_ITEM = By.cssSelector("dl.track-item__details .track-details__item");


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
                // ✅ Ретрай только для сценария #1
                boolean willRetry = attempt < maxAttempts;
                log.warn("⏳ Лимит по {} (попытка {}/{}). {}",
                        number, attempt, maxAttempts,
                        willRetry ? "Ждём " + (retryDelayMs / 1000) + " сек. и повторяем" : "Достигнут предел попыток");
                if (willRetry) {
                    sleep(retryDelayMs);     // ждём только тут
                    continue;                // вторая попытка
                }
                return new TrackInfoListDTO(); // всё, выходим без дополнительных ожиданий
            } catch (TimeoutException e) {
                // ❌ НЕ ретраим и НЕ ждём — по ТЗ ретрай только для сценария #1
                log.warn("⏱️ Timeout при парсинге {} на попытке {} — пропускаем без ожиданий", number, attempt);
                return new TrackInfoListDTO();
            } catch (Exception e) {
                // Любая другая ошибка — лог и выход без ожиданий
                log.error("❌ Ошибка при парсинге {} на попытке {}: {}", number, attempt, e.getMessage(), e);
                return new TrackInfoListDTO();
            }
        }
        return new TrackInfoListDTO();
    }

    /**
     * Парсит один трек-номер, используя уже инициализированный драйвер.
     */
    private TrackInfoListDTO tryParseTrack(WebDriver driver, String number) throws Exception {
        TrackInfoListDTO dto = new TrackInfoListDTO();

        driver.get("https://belpost.by/Otsleditotpravleniye?number=" + number);

        if (isRateLimitErrorDisplayed(driver)) {
            throw new RateLimitException("Превышено количество запросов");
        }

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

        // 1) Ждём ЛИБО предупреждение, ЛИБО article.track-item (presence, без isDisplayed)
        WebElement awaited;
        try {
            awaited = wait.until(d -> {
                if (isRateLimitErrorDisplayed(d)) {
                    throw new RateLimitException("Превышено количество запросов");
                }
                List<WebElement> warn = d.findElements(NO_DATA_WARNING);
                if (!warn.isEmpty()) return warn.get(0);        // всегда отдаём приоритет предупреждению

                List<WebElement> items = d.findElements(TRACK_ITEM);
                if (!items.isEmpty()) return items.get(0);

                return null;
            });
        } catch (TimeoutException te) {
            // Last-chance пере-классификация: если к этому моменту DOM уже содержит текст «нет данных» — считаем сценарием #2
            if (isRateLimitErrorDisplayed(driver)) {
                throw new RateLimitException("Превышено количество запросов");
            }
            if (pageSaysNoData(driver)) {
                log.info("ℹ️ Нет данных по {} (обнаружено после таймаута по DOM-тексту) — пропускаем", number);
                return dto;
            }
            throw te;
        }

        // 2) Если вернулось предупреждение — это сценарий #2
        if (isNoDataWarningDisplayed(awaited)) {
            log.info("ℹ️ Нет данных по номеру {} — пропускаем без ожиданий", number);
            return dto;
        }

        // 3) Иначе это article.track-item. На всякий случай проверим, нет ли предупреждения ВНУТРИ него.
        WebElement trackItem = awaited;
        if (!trackItem.findElements(NO_DATA_WARNING).isEmpty()) {
            log.info("ℹ️ Нет данных по номеру {} (предупреждение внутри track-item) — пропускаем", number);
            return dto;
        }

        // 4) Раскрываем аккордеон при необходимости
        WebElement header = trackItem.findElement(By.cssSelector("header"));
        if (!"true".equals(header.getAttribute("aria-expanded"))) {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript("arguments[0].scrollIntoView({block:'center', inline:'center'});", header);
            wait.until(ExpectedConditions.elementToBeClickable(header));
            js.executeScript("arguments[0].click();", header);
            wait.until(ExpectedConditions.attributeToBe(header, "aria-expanded", "true"));
        }

        // 5) Ждём ИЛИ появление деталей, ИЛИ появление предупреждения внутри track-item
        boolean ready = wait.until(drv -> {
            try {
                if (!trackItem.findElements(NO_DATA_WARNING).isEmpty()) return true; // это «нет данных»
                return !trackItem.findElements(DETAILS_ITEM).isEmpty();              // это «есть данные»
            } catch (StaleElementReferenceException e) {
                return false;
            }
        });

        // 6) Если внутри появился warning — сценарий #2
        if (!trackItem.findElements(NO_DATA_WARNING).isEmpty() || pageSaysNoData(driver)) {
            log.info("ℹ️ Нет данных по номеру {} — пропускаем без ожиданий", number);
            return dto;
        }

        // 7) Парсинг деталей. findElements, а не findElement — без исключений.
        List<WebElement> items = trackItem.findElements(DETAILS_ITEM);
        if (items.isEmpty()) {
            // На всякий случай не кидаем NoSuchElement — трактуем как «нет данных»
            log.info("ℹ️ Детали не найдены для {}, трактуем как «нет данных»", number);
            return dto;
        }

        for (WebElement item : items) {
            String title = safeText(item, By.cssSelector("dt"));
            String date  = safeText(item, By.cssSelector("dd li.text-secondary"));
            if (!title.isEmpty() || !date.isEmpty()) {
                dto.addTrackInfo(new TrackInfoDTO(date, title));
            }
        }

        return dto;
    }

    private String safeText(WebElement root, By by) {
        try {
            WebElement el = root.findElement(by);
            return el.getText() == null ? "" : el.getText().trim();
        } catch (NoSuchElementException | StaleElementReferenceException e) {
            return "";
        }
    }

    /**
     * Определяет, является ли переданный элемент предупреждением об отсутствии данных.
     * Метод не выполняет дополнительных ожиданий, а анализирует уже найденный элемент.
     *
     * @param element элемент, полученный после ожидания
     * @return {@code true}, если элемент представляет предупреждение
     */
    private boolean isNoDataWarningDisplayed(WebElement element) {
        try {
            if (element == null) return false;
            String text = element.getText();
            if (text != null && text.contains("У нас пока нет данных")) return true;

            List<WebElement> nested = element.findElements(NO_DATA_WARNING);
            return !nested.isEmpty() && nested.get(0).getText().contains("У нас пока нет данных");
        } catch (StaleElementReferenceException ignored) {
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
            WebElement title = driver.findElement(By.cssSelector(".swal2-title"));
            WebElement text  = driver.findElement(By.cssSelector("#swal2-content, .swal2-html-container"));
            return title.isDisplayed() && text.getText().contains("Превышено количество запросов");
        } catch (NoSuchElementException e) {
            return false;
        }
    }

    /**
     * Исключение, сигнализирующее о превышении лимита запросов Белпочты.
     * Расширяет {@link RuntimeException}, чтобы его можно было выбрасывать
     * внутри лямбд без обязательного объявления в сигнатурах методов.
     */
    public class RateLimitException extends RuntimeException {
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

    /** Быстрый JS-чек текста предупреждения в DOM (обходит гонки visible/animation) */
    private boolean pageSaysNoData(WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            String text = (String) js.executeScript(
                    "const el=document.querySelector('.alert-message--warning');return el?el.textContent:'';");
            return text != null && text.contains("У нас пока нет данных");
        } catch (Exception ignore) {
            return false;
        }
    }

}