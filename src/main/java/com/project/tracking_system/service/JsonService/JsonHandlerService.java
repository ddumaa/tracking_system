package com.project.tracking_system.service.JsonService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.tracking_system.model.jsonRequestModel.JsonRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class JsonHandlerService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${evro.jwt.ApiUrl}")
    private String url;

    @Autowired
    public JsonHandlerService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public JsonNode jsonRequest(JsonRequest jsonRequest) {

        // Отправка запроса и получиние ответа.
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<JsonRequest> entity = new HttpEntity<>(jsonRequest, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

        // Извлечение токена JWT из ответа
        JsonNode jsonNode;
        try {
            if (response.getStatusCode() != HttpStatus.OK) {
                throw new RuntimeException("Не удалось получить ответ, код состояния.: " + response.getStatusCode());
            }

            String responseBody = response.getBody();
            if (responseBody == null) {
                throw new RuntimeException("Тело ответа имеет значение null");
            }

            jsonNode = objectMapper.readTree(responseBody);
        } catch (
                JsonProcessingException e) {
            throw new RuntimeException("Ошибка анализа ответа JSON.", e);
        }

        return jsonNode;
    }

}
