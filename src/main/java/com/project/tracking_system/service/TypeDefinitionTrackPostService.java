package com.project.tracking_system.service;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.maper.JsonEvroTrackingResponseMapper;
import com.project.tracking_system.model.evropost.jsonResponseModel.JsonEvroTrackingResponse;
import com.project.tracking_system.service.belpost.WebBelPost;
import com.project.tracking_system.service.jsonEvropostService.JsonEvroTrackingService;
import org.springframework.beans.factory.annotation.Autowired;
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
@Service
public class TypeDefinitionTrackPostService {

    private final WebBelPost webBelPost;
    private final JsonEvroTrackingService jsonEvroTrackingService;
    private final JsonEvroTrackingResponseMapper jsonEvroTrackingResponseMapper;

    /**
     * Конструктор класса {@link TypeDefinitionTrackPostService}.
     *
     * @param webBelPost сервис для работы с WebBelPost
     * @param jsonEvroTrackingService сервис для получения данных о посылке от EuroPost
     * @param jsonEvroTrackingResponseMapper маппер для преобразования ответа от EuroPost в объект DTO
     */
    @Autowired
    public TypeDefinitionTrackPostService(WebBelPost webBelPost, JsonEvroTrackingService jsonEvroTrackingService,
                                          JsonEvroTrackingResponseMapper jsonEvroTrackingResponseMapper) {
        this.webBelPost = webBelPost;
        this.jsonEvroTrackingService = jsonEvroTrackingService;
        this.jsonEvroTrackingResponseMapper = jsonEvroTrackingResponseMapper;
    }

    /**
     * Асинхронный метод для получения информации о статусе посылки по номеру отслеживания.
     * <p>
     * В зависимости от формата номера отслеживания, сервис обращается к разным источникам данных:
     * WebBelPost для белорусских почтовых отправлений и EuroPost для международных.
     * </p>
     *
     * @param number номер отслеживания посылки
     * @return объект {@link CompletableFuture} с результатом обработки запроса
     * @throws IllegalArgumentException если номер отслеживания имеет некорректный формат
     */
    @Async("Post")
    public CompletableFuture<TrackInfoListDTO> getTypeDefinitionTrackPostServiceAsync(User user, String number) {
        if (number.matches("^PC\\d{9}BY$") || number.matches("^BV\\d{9}BY$") ||
                number.matches("^BP\\d{9}BY$")) {
            return webBelPost.webAutomationAsync(number);
        }
        if (number.matches("^BY\\d{12}$")) {
            JsonEvroTrackingResponse json = jsonEvroTrackingService.getJson(user, number);
            TrackInfoListDTO trackInfoListDTO = jsonEvroTrackingResponseMapper.mapJsonEvroTrackingResponseToDTO(json);
            return CompletableFuture.completedFuture(trackInfoListDTO);
        }
        throw new IllegalArgumentException("Указан некорректный код посылки: " + number);
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
    public TrackInfoListDTO getTypeDefinitionTrackPostService(User user, String number) {
        try {
            return getTypeDefinitionTrackPostServiceAsync(user, number).get();
        } catch (ExecutionException | InterruptedException e) {
            // Обрабатываем ошибки при ожидании асинхронного ответа
            e.printStackTrace();
            return new TrackInfoListDTO();  // Возвращаем пустой объект, если произошла ошибка
        } catch (IllegalArgumentException e) {
            // Обрабатываем ошибку неправильного формата трек-номера
            throw e;
        }
    }
}