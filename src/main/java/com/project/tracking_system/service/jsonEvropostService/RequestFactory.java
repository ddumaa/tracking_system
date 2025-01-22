package com.project.tracking_system.service.jsonEvropostService;

import com.project.tracking_system.model.evropost.jsonRequestModel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Сервис для создания различных типов JSON-запросов к API ЕвроПочты.
 * <p>
 * Этот класс формирует запросы для различных операций с API ЕвроПочты, таких как отслеживание посылок
 * и получение JWT токена для дальнейших запросов.
 * </p>
 *
 * @author Дмитрий Анисимов
 * @date 07.01.2025
 */
@Service
public class RequestFactory {

    private final JsonData jsonData;
    private final JsonRequest jsonRequest;
    private final JsonPacket jsonPacket;

    @Autowired
    public RequestFactory(JsonData jsonData, JsonRequest jsonRequest, JsonPacket jsonPacket) {
        this.jsonData = jsonData;
        this.jsonRequest = jsonRequest;
        this.jsonPacket = jsonPacket;
    }

    /**
     * Создаёт запрос для отслеживания посылки по номеру.
     * <p>
     * Метод формирует JSON-запрос для получения информации по треку.
     * </p>
     *
     * @param number номер посылки, которую необходимо отследить.
     * @return {@link JsonRequest} сформированный запрос для отслеживания.
     */
    public JsonRequest createTrackingRequest(String number) {
        JsonDataAbstract data = new JsonTrackingData(number);
        JsonPacket packet = new JsonPacket(jsonPacket.getJwt(), JsonMethodName.POSTAL_TRACKING.toString(), jsonPacket.getServiceNumber(), data);
        return new JsonRequest(jsonRequest.getCrc(), packet);
    }

    /**
     * Создаёт запрос для получения JWT токена.
     * <p>
     * Метод формирует JSON-запрос для получения JWT токена.
     * </p>
     *
     * @return {@link JsonRequest} сформированный запрос для получения JWT токена.
     */
    public JsonRequest createGetJWTRequest() {
        JsonDataAbstract data = new JsonGetJWTData(jsonData.getLoginName(), jsonData.getPassword(), jsonData.getLoginNameTypeId());
        JsonPacket packet = new JsonPacket(null, JsonMethodName.GET_JWT.toString(), jsonPacket.getServiceNumber(), data);
        return new JsonRequest(jsonRequest.getCrc(), packet);
    }

}