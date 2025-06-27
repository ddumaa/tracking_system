package com.project.tracking_system.service.jsonEvropostService;

import com.fasterxml.jackson.databind.JsonNode;
import com.project.tracking_system.model.evropost.jsonRequestModel.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequiredArgsConstructor
@Slf4j
@Service
public class GetJwtTokenService {

    private final JsonHandlerService jsonHandlerService;
    private final JsonData jsonData;
    private final JsonRequest jsonRequest;
    private final JsonPacket jsonPacket;

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

        return fetchJwtToken(data, jsonPacket.getServiceNumber());
    }

    // Получение пользовательского токена
    public String getUserTokenFromApi(String username, String password, String serviceNumber) {

        JsonDataAbstract data = new JsonGetJWTData(
                username,
                password,
                jsonData.getLoginNameTypeId()
        );

        return fetchJwtToken(data, serviceNumber);
    }

    /**
     * Формирует запрос и извлекает JWT токен из ответа.
     *
     * @param data          данные для аутентификации
     * @param serviceNumber номер сервиса ЕвроПочты
     * @return JWT токен в виде строки
     * @throws RuntimeException если токен отсутствует в ответе
     */
    private String fetchJwtToken(JsonDataAbstract data, String serviceNumber) {

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
            throw new RuntimeException("Токен JWT не найден в ответе");
        }

        return jwtNode.asText();
    }

}