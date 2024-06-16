package com.project.tracking_system.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.tracking_system.model.jwtModel.JwtData;
import com.project.tracking_system.model.jwtModel.JwtRequest;
import com.project.tracking_system.model.JwtToken;
import com.project.tracking_system.model.jwtModel.Packet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class JwtTokenService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final JwtToken jwtToken;
    private final Packet packet;
    private final JwtData jwtData;

    @Autowired
    public JwtTokenService(RestTemplate restTemplate, ObjectMapper objectMapper, JwtToken jwtToken, Packet packet, JwtData jwtData) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.jwtToken = jwtToken;
        this.packet = packet;
        this.jwtData = jwtData;
    }

    public String getJwtToken(String url, String ServiceNumber, String LoginName, String Password, String LoginNameTypeId) {

        JwtRequest jwtRequest = new JwtRequest(
                "",
                new Packet(
                        "null",
                        "GetJWT",
                        ServiceNumber,
                        new JwtData(
                                LoginName,
                                Password,
                                LoginNameTypeId
                        )
                )
        );

        //проверка отправляемого json
        ObjectMapper mapper = new ObjectMapper();
        try {
            String jsonString = mapper.writeValueAsString(jwtRequest);
            System.out.println(jsonString);  // Посмотрите, как выглядит JSON, отправляемый на сервер
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        // Отправка запроса и получиние токена JWT.
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<JwtRequest> entity = new HttpEntity<>(jwtRequest, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

        // Извлечение токена JWT из ответа
        JsonNode jsonNode;
        try {
            if (response.getStatusCode() != HttpStatus.OK) {
                throw new RuntimeException("Не удалось получить токен JWT, код состояния.: " + response.getStatusCode());
            }

            String responseBody = response.getBody();
            if (responseBody == null) {
                throw new RuntimeException("Тело ответа имеет значение null");
            }

            jsonNode = objectMapper.readTree(responseBody);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Ошибка анализа ответа JSON.", e);
        }

        JsonNode jwtNode = jsonNode.path("Table").path(0).path("JWT");
        if (jwtNode.isMissingNode()) {
            throw new RuntimeException("Токен JWT не найден в ответе");
        }

        jwtToken.setToken(jwtNode.asText());
        return jwtToken.getToken();
    }
}