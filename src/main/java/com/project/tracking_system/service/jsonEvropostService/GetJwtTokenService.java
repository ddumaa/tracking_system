package com.project.tracking_system.service.jsonEvropostService;

import com.fasterxml.jackson.databind.JsonNode;
import com.project.tracking_system.model.evropost.jsonRequestModel.JsonPacket;
import com.project.tracking_system.model.evropost.jsonRequestModel.JsonRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Сервис для получения JWT токена.
 * <p>
 * Этот сервис выполняет запрос для получения JWT токена от внешнего API и сохраняет его для дальнейшего использования.
 * </p>
 *
 * @author Dmitriy Anisimov
 * @date 07.01.2025
 */
@Service
public class GetJwtTokenService {

    private final JsonHandlerService jsonHandlerService;
    private final RequestFactory requestFactory;

    @Autowired
    public GetJwtTokenService(RequestFactory requestFactory, JsonHandlerService jsonHandlerService) {
        this.requestFactory = requestFactory;
        this.jsonHandlerService = jsonHandlerService;
    }

    /**
     * Получает JWT токен от внешнего API.
     * <p>
     * Метод выполняет запрос для получения JWT токена, извлекает его из ответа и сохраняет в объект {@link JsonPacket}.
     * Если токен не найден, выбрасывается исключение {@link RuntimeException}.
     * </p>
     */
    public String getJwtToken() {
        JsonRequest jsonRequest = requestFactory.createGetJWTRequest();
        JsonNode jsonNode = jsonHandlerService.jsonRequest(jsonRequest);

        JsonNode jwtNode = jsonNode.path("Table").path(0).path("JWT");
        if (jwtNode.isMissingNode()) {
            throw new RuntimeException("Токен JWT не найден в ответе");
        }

        return jwtNode.asText(); // Возвращаем сгенерированный токен
    }
}