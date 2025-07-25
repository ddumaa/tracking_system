package com.project.tracking_system.service.jsonEvropostService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.tracking_system.dto.ResolvedCredentialsDTO;
import com.project.tracking_system.model.evropost.jsonRequestModel.JsonRequest;
import com.project.tracking_system.model.evropost.jsonResponseModel.JsonEvroTracking;
import com.project.tracking_system.model.evropost.jsonResponseModel.JsonEvroTrackingResponse;
import com.project.tracking_system.service.user.UserService;
import com.project.tracking_system.utils.UserCredentialsResolver;
import jakarta.json.bind.JsonbException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Сервис для получения информации о трекинге посылки от ЕвроПочты.
 * <p>
 * Этот сервис выполняет запрос для получения данных о трекинге посылки по номеру и десериализует
 * ответ в объект {@link JsonEvroTrackingResponse}, содержащий список объектов {@link JsonEvroTracking}.
 * </p>
 *
 * @author Dmitriy Anisimov
 * @date 07.01.2025
 */
@RequiredArgsConstructor
@Slf4j
@Service
public class JsonEvroTrackingService {

    private final JsonHandlerService jsonHandlerService;
    private final RequestFactory requestFactory;
    private final UserService userService;
    private final UserCredentialsResolver userCredentialsResolver;

    /**
     * Получает информацию о трекинге посылки от ЕвроПочты.
     * <p>
     * Метод выполняет запрос к внешнему API для получения данных о трекинге посылки по номеру,
     * а затем десериализует ответ в объект {@link JsonEvroTrackingResponse}.
     * </p>
     *
     * @param number Номер посылки, для которой требуется получить информацию о трекинге.
     * @return {@link JsonEvroTrackingResponse} объект с данными о трекинге.
     * @throws IllegalStateException если произошла ошибка десериализации JSON или запроса
     */
    public JsonEvroTrackingResponse getJson(Long userId, String number) {
        ResolvedCredentialsDTO credentials;
        boolean useCustomCredentials = false;

        if (userId == null) {
            log.warn("Анонимный пользователь делает запрос. Используем системные учетные данные.");
            credentials = userCredentialsResolver.getSystemCredentials(); // Новый метод
        } else {
            useCustomCredentials = userService.isUsingCustomCredentials(userId);
            log.info("Запрос на получение данных для пользователя ID: {}, почтовый номер: {}", userId, number);
            credentials = userService.resolveCredentials(userId);
        }

        log.info("Данные для запроса определены. Используются {} данные.",
                useCustomCredentials ? "пользовательские" : "системные");

        // Выполняем запрос
        JsonRequest jsonRequest = requestFactory.createTrackingRequest(
                credentials.getJwt(),
                credentials.getServiceNumber(),
                number);
        log.debug("Запрос создан. Почтовый номер: {}", number);

        JsonNode jsonNode;
        try {
            jsonNode = jsonHandlerService.jsonRequest(jsonRequest);
            log.info("Запрос успешно выполнен для почтового номера: {}", number);
        } catch (Exception e) {
            log.error("Ошибка при выполнении запроса для почтового номера: {}", number, e);
            throw new IllegalStateException("Ошибка при выполнении запроса.", e);
        }

        JsonEvroTrackingResponse response = new JsonEvroTrackingResponse();
        JsonNode tableNode = jsonNode.path("Table");

        try {
            ObjectMapper mapper = new ObjectMapper();
            List<JsonEvroTracking> table = mapper.convertValue(tableNode, new TypeReference<List<JsonEvroTracking>>() {});
            response.setTable(table);
            log.info("Ответ успешно обработан. Количество записей: {}", table.size());
            return response;
        } catch (JsonbException e) {
            log.error("Ошибка десериализации ответа JSON для почтового номера: {}", number, e);
            throw new IllegalStateException("Ошибка десериализации ответа JSON.", e);
        }
    }

}