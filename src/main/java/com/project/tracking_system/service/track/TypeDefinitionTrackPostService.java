package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.github.benmanes.caffeine.cache.Cache;
import com.project.tracking_system.entity.PostalServiceType;
import com.project.tracking_system.mapper.JsonEvroTrackingResponseMapper;
import com.project.tracking_system.model.evropost.jsonResponseModel.JsonEvroTrackingResponse;
import com.project.tracking_system.service.belpost.WebBelPost;
import com.project.tracking_system.service.jsonEvropostService.JsonEvroTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

/**
 * Сервис для получения информации о статусе почтовых отправлений.
 * <p>
 * Этот сервис предоставляет методы для получения информации о посылках на основе номера отслеживания.
 * Включает асинхронную обработку запросов для различных типов кодов посылок и интеграцию с сервисами WebBelPost и EuroPost.
 * </p>
 *
 * @author Dmitriy Anisimov
 * @date Добавленно 07.01.2025
 */
@RequiredArgsConstructor
@Slf4j
@Service
public class TypeDefinitionTrackPostService {

    private final WebBelPost webBelPost;
    private final JsonEvroTrackingService jsonEvroTrackingService;
    private final JsonEvroTrackingResponseMapper jsonEvroTrackingResponseMapper;
    /**
     * Кэш для хранения информации по трек-номерам.
     * Позволяет снизить количество обращений к внешним сервисам.
     */
    private final Cache<String, TrackInfoListDTO> trackInfoCache;

    private final Map<String, Object> trackLocks = new ConcurrentHashMap<>();

    /**
     * Определяет тип почтовой службы по номеру посылки.
     */
    public PostalServiceType detectPostalService(String number) {
        if (number.matches("^PC\\d{9}BY$") || number.matches("^BV\\d{9}BY$") || number.matches("^BP\\d{9}BY$")) {
            return PostalServiceType.BELPOST;
        }
        if (number.matches("^BY\\d{12}$")) {
            return PostalServiceType.EVROPOST;
        }
        return PostalServiceType.UNKNOWN;
    }

    /**
     * Асинхронный метод для получения информации о статусе посылки по номеру отслеживания.
     * Перед выполнением запроса метод проверяет наличие данных в кэше и
     * возвращает их при наличии, что снижает количество обращений к веб-сервисам.
     *
     * @param number номер отслеживания посылки
     * @return объект {@link CompletableFuture} с результатом обработки запроса
     * @throws IllegalArgumentException если номер отслеживания имеет некорректный формат
     */
    @Async("Post")
    public CompletableFuture<TrackInfoListDTO> getTypeDefinitionTrackPostServiceAsync(Long userId, String number) {
        TrackInfoListDTO cached = trackInfoCache.getIfPresent(number);
        if (cached != null) {
            log.debug("📦 Данные по треку {} получены из кэша", number);
            return CompletableFuture.completedFuture(cached);
        }

        PostalServiceType postalService = detectPostalService(number);

        log.info("📦 Запрос информации по треку: {} (Пользователь ID={})", number, userId);
        log.debug("🔎 Определяем почтовую службу: {} → {}", number, postalService);

        try {
            TrackInfoListDTO result;
            switch (postalService) {
                case BELPOST:
                    log.info("📨 Запрос к Белпочте для номера: {}", number);
                    // Запускаем selenium на Post-исполнителе, выполняя синхронный метод
                    result = webBelPost.webAutomation(number);
                    break;

                case EVROPOST:
                    log.info("📨 Запрос к Европочте для номера: {}", number);
                    JsonEvroTrackingResponse json = jsonEvroTrackingService.getJson(userId, number);
                    result = jsonEvroTrackingResponseMapper.mapJsonEvroTrackingResponseToDTO(json);
                    break;

                default:
                    log.warn("⚠️ Неизвестный формат трек-номера: {} (UNKNOWN)", number);
                    throw new IllegalArgumentException("Указан некорректный код посылки: " + number);
            }
            trackInfoCache.put(number, result);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            log.error("Ошибка при обработке трек-номера {} для пользователя с ID {}: {}", number, userId, e.getMessage(), e);
            return CompletableFuture.completedFuture(new TrackInfoListDTO());
        }
    }


    /**
     * Синхронный метод для получения информации о статусе посылки.
     * <p>
     * Сначала выполняется проверка кэша, после чего при необходимости
     * выполняется асинхронный запрос к почтовым сервисам.
     * </p>
     *
     * @param number номер отслеживания посылки
     * @return объект {@link TrackInfoListDTO} с информацией о статусе посылки
     * @throws IllegalArgumentException если номер отслеживания имеет некорректный формат
     */
    public TrackInfoListDTO getTypeDefinitionTrackPostService(Long userId, String number) {
        TrackInfoListDTO cached = trackInfoCache.getIfPresent(number);
        if (cached != null) {
            log.debug("📦 Синхронный запрос: данные по треку {} получены из кэша", number);
            return cached;
        }

        Object lock = trackLocks.computeIfAbsent(number, key -> new Object());

        synchronized (lock) {
            try {
                log.info("⏳ [LOCKED] Запрос (синхронный) для трека: {} (Пользователь ID={})", number, userId);
                TrackInfoListDTO result = getTypeDefinitionTrackPostServiceAsync(userId, number).get();
                trackInfoCache.put(number, result);
                return result;
            } catch (ExecutionException | InterruptedException e) {
                log.error("Ошибка при получении данных по треку {} для пользователя с ID {}: {}", number, userId, e.getMessage(), e);
                Thread.currentThread().interrupt();
                return new TrackInfoListDTO();
            } finally {
                trackLocks.remove(number); // очищаем мапу
            }
        }
    }

}