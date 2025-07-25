package com.project.tracking_system.service.jsonEvropostService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.tracking_system.model.evropost.jsonRequestModel.JsonRequest;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Сервис для выполнения JSON-запросов к API ЕвроПочты.
 * <p>
 * Этот сервис обрабатывает HTTP-запросы, отправляя запросы в формате JSON и обрабатывая ответы.
 * Он использует {@link RestTemplate} для выполнения запросов и {@link ObjectMapper} для обработки JSON-ответов.
 * </p>
 *
 * @author Dmitriy Anisimov
 * @date 07.01.2025
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class JsonHandlerService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;


    @Value("${evro.jwt.ApiUrl}")
    private String URL;

    /**
     * Выполняет HTTP POST-запрос к API ЕвроПочты с передачей JSON-объекта.
     * <p>
     * Метод формирует и отправляет POST-запрос с данным объектом в теле запроса.
     * Ответ обрабатывается, и если запрос успешен, возвращается десериализованный {@link JsonNode}.
     * </p>
     *
     * @param jsonRequest объект запроса, который будет сериализован в JSON.
     * @return {@link JsonNode} десериализованный ответ от API.
     * @throws IllegalStateException если запрос не удался или произошла ошибка при обработке ответа
     */
    public JsonNode jsonRequest(JsonRequest jsonRequest) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<JsonRequest> entity = new HttpEntity<>(jsonRequest, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(URL, entity, String.class);

        JsonNode jsonNode;
        try {
            if (response.getStatusCode() != HttpStatus.OK) {
                throw new IllegalStateException("Не удалось получить ответ, код состояния.: " + response.getStatusCode());
            }

            String responseBody = response.getBody();
            if (responseBody == null) {
                throw new IllegalStateException("Тело ответа имеет значение null");
            }

            jsonNode = objectMapper.readTree(responseBody);
        } catch (
                JsonProcessingException e) {
            throw new IllegalStateException("Ошибка анализа ответа JSON.", e);
        }

        return jsonNode;
    }
}