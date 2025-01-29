package com.project.tracking_system.service.jsonEvropostService;

import com.fasterxml.jackson.databind.JsonNode;
import com.project.tracking_system.model.evropost.jsonRequestModel.*;
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
    private final JsonData jsonData;
    private final JsonRequest jsonRequest;
    private final JsonPacket jsonPacket;

    @Autowired
    public GetJwtTokenService(JsonHandlerService jsonHandlerService, JsonData jsonData,
                              JsonRequest jsonRequest, JsonPacket jsonPacket) {
        this.jsonHandlerService = jsonHandlerService;
        this.jsonData = jsonData;
        this.jsonRequest = jsonRequest;
        this.jsonPacket = jsonPacket;
    }

    /**
     * Получает JWT токен от внешнего API.
     * <p>
     * Метод выполняет запрос для получения JWT токена, извлекает его из ответа и сохраняет в объект {@link JsonPacket}.
     * Если токен не найден, выбрасывается исключение {@link RuntimeException}.
     * </p>
     */
    // Получение системного токена
    public String getSystemTokenFromApi() {

        JsonDataAbstract data = new JsonGetJWTData(
                jsonData.getLoginName(),
                jsonData.getPassword(),
                jsonData.getLoginNameTypeId()
        );

        JsonPacket packet = new JsonPacket(
                null,
                JsonMethodName.GET_JWT.toString(),
                jsonPacket.getServiceNumber(),
                data
                );

        JsonRequest request = new JsonRequest(
                jsonRequest.getCrc(),
                packet
        );

        // Делаем запрос
        JsonNode response = jsonHandlerService.jsonRequest(request);

        // Извлекаем JWT токен из ответа
        JsonNode jwtNode = response.path("Table").path(0).path("JWT");
        if (jwtNode.isMissingNode()) {
            throw new RuntimeException("Токен JWT не найден в ответе при запросе системного JWT");
        }

        return jwtNode.asText();
    }

    // Получение пользовательского токена
    public String getUserTokenFromApi(String username, String password, String serviceNumber) {

        JsonDataAbstract data = new JsonGetJWTData(
                username,
                password,
                jsonData.getLoginNameTypeId()
        );

        JsonPacket packet = new JsonPacket(
                null,
                JsonMethodName.GET_JWT.toString(),
                serviceNumber,
                data
        );

        JsonRequest request = new JsonRequest(
                jsonRequest.getCrc(),
                packet
        );

        // Делаем запрос
        JsonNode response = jsonHandlerService.jsonRequest(request);

        // Извлекаем JWT токен из ответа
        JsonNode jwtNode = response.path("Table").path(0).path("JWT");
        if (jwtNode.isMissingNode()) {
            throw new RuntimeException("Токен JWT не найден в ответе при запросе пользовательского JWT");
        }

        return jwtNode.asText();
    }

}