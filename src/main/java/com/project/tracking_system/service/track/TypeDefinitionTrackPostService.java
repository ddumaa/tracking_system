package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.entity.PostalServiceType;
import com.project.tracking_system.mapper.JsonEvroTrackingResponseMapper;
import com.project.tracking_system.model.evropost.jsonResponseModel.JsonEvroTrackingResponse;
import com.project.tracking_system.service.belpost.WebBelPostBatchService;
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
 * Включает асинхронную обработку запросов для различных типов кодов посылок и
 * интеграцию с сервисами WebBelPostBatchService и EuroPost.
 * </p>
 *
 * @author Dmitriy Anisimov
 * @date Добавленно 07.01.2025
 */
@RequiredArgsConstructor
@Slf4j
@Service
public class TypeDefinitionTrackPostService {

    private final WebBelPostBatchService webBelPostBatchService;
    private final JsonEvroTrackingService jsonEvroTrackingService;
    private final JsonEvroTrackingResponseMapper jsonEvroTrackingResponseMapper;

    private final Map<String, Object> trackLocks = new ConcurrentHashMap<>();

    /**
     * Определяет почтовую службу на основе формата трек-номера.
     * <p>Возвращает {@link PostalServiceType#UNKNOWN}, если шаблон не подходит.</p>
     */
    public PostalServiceType detectPostalService(String number) {
        // Если номер не указан или состоит из пробелов, считаем службу неизвестной
        if (number == null || number.isBlank()) {
            return PostalServiceType.UNKNOWN;
        }

        if (number.matches("^(PC|BV|BP|PE)\\d{9}BY$")) {
            return PostalServiceType.BELPOST;
        }
        if (number.matches("^BY\\d{12}$")) {
            return PostalServiceType.EVROPOST;
        }
        return PostalServiceType.UNKNOWN;
    }

    /**
     * Асинхронный метод для получения информации о статусе посылки по номеру отслеживания.
     *
     * @param number номер отслеживания посылки
     * @return объект {@link CompletableFuture} с результатом обработки запроса
     * @throws IllegalArgumentException если номер отслеживания имеет некорректный формат
     */
    @Async("Post")
    public CompletableFuture<TrackInfoListDTO> getTypeDefinitionTrackPostServiceAsync(Long userId, String number) {
        // Аннотация @Async выполняет метод в отдельном потоке, поэтому дополнительный supplyAsync не требуется.
        PostalServiceType postalService = detectPostalService(number);

        log.info("📦 Запрос информации по треку: {} (Пользователь ID={})", number, userId);
        log.debug("🔎 Определяем почтовую службу: {} → {}", number, postalService);

        TrackInfoListDTO result;
        try {
            switch (postalService) {
                case BELPOST:
                    log.info("📨 Запрос к Белпочте для номера: {}", number);
                    result = webBelPostBatchService.parseTrack(number);
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
        } catch (Exception e) {
            log.error("Ошибка при обработке трек-номера {} для пользователя с ID {}: {}", number, userId, e.getMessage(), e);
            result = new TrackInfoListDTO();
        }

        // Возвращаем результат как уже завершённый CompletableFuture
        return CompletableFuture.completedFuture(result);
    }


    /**
     * Синхронный метод для получения информации о статусе посылки.
     * <p>
     * Этот метод ожидает завершения асинхронного запроса и возвращает результат или пустой объект в случае ошибки.
     * </p>
     *
     * @param number номер отслеживания посылки
     * @return объект {@link TrackInfoListDTO} с информацией о статусе посылки
     * @throws IllegalArgumentException если номер отслеживания имеет некорректный формат
     */
    public TrackInfoListDTO getTypeDefinitionTrackPostService(Long userId, String number) {
        // Используем лок для предотвращения одновременных запросов к одному треку
        Object lock = trackLocks.computeIfAbsent(number, key -> new Object());

        synchronized (lock) {
            try {
                log.info("⏳ [LOCKED] Запрос (синхронный) для трека: {} (Пользователь ID={})", number, userId);
                return getTypeDefinitionTrackPostServiceAsync(userId, number).get();
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