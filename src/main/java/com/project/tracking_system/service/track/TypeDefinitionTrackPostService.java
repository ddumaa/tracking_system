package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.entity.PostalServiceType;
import com.project.tracking_system.maper.JsonEvroTrackingResponseMapper;
import com.project.tracking_system.model.evropost.jsonResponseModel.JsonEvroTrackingResponse;
import com.project.tracking_system.service.belpost.WebBelPost;
import com.project.tracking_system.service.jsonEvropostService.JsonEvroTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
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
     *
     * @param number номер отслеживания посылки
     * @return объект {@link CompletableFuture} с результатом обработки запроса
     * @throws IllegalArgumentException если номер отслеживания имеет некорректный формат
     */
    @Async("Post")
    public CompletableFuture<TrackInfoListDTO> getTypeDefinitionTrackPostServiceAsync(Long userId, String number) {
        return CompletableFuture.supplyAsync(() -> {


            PostalServiceType postalService = detectPostalService(number);

            log.info("📦 Запрос информации по треку: {} (Пользователь ID={})", number, userId);
            log.debug("🔎 Определяем почтовую службу: {} → {}", number, postalService);

            try {
                switch (postalService) {
                    case BELPOST:
                        log.info("📨 Запрос к Белпочте для номера: {}", number);
                        return webBelPost.webAutomationAsync(number).join();

                    case EVROPOST:
                        log.info("📨 Запрос к Европочте для номера: {}", number);
                        JsonEvroTrackingResponse json = jsonEvroTrackingService.getJson(userId, number);
                        return jsonEvroTrackingResponseMapper.mapJsonEvroTrackingResponseToDTO(json);

                    default:
                        log.warn("⚠️ Неизвестный формат трек-номера: {} (UNKNOWN)", number);
                        throw new IllegalArgumentException("Указан некорректный код посылки: " + number);
                }
            } catch (Exception e) {
                log.error("Ошибка при обработке трек-номера {} для пользователя с ID {}: {}", number, userId, e.getMessage(), e);
                return new TrackInfoListDTO();
            }
        });
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
        try {
            log.info("⏳ Запрос (синхронный) для трека: {} (Пользователь ID={})", number, userId);
            return getTypeDefinitionTrackPostServiceAsync(userId, number).get();
        } catch (ExecutionException | InterruptedException e) {
            log.error("Ошибка при получении данных по треку {} для пользователя с ID {}: {}", number, userId, e.getMessage(), e);
            Thread.currentThread().interrupt(); // Восстанавливаем статус прерывания потока
            return new TrackInfoListDTO(); // Возвращаем пустой объект, если произошла ошибка
        }
    }

}