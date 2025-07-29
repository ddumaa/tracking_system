package com.project.tracking_system.service.jsonEvropostService;

import com.project.tracking_system.model.evropost.jsonRequestModel.*;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
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
@Slf4j
@Service
@RequiredArgsConstructor
public class RequestFactory {

    private final JsonRequest jsonRequest;

    /**
     * Создаёт запрос для отслеживания посылки по номеру.
     * <p>
     * Метод формирует JSON-запрос для получения информации по треку.
     * </p>
     *
     * @param number номер посылки, которую необходимо отследить.
     * @return {@link JsonRequest} сформированный запрос для отслеживания.
     */
    public JsonRequest createTrackingRequest(String jwt, String serviceNumber, String postalNumber) {
        JsonDataAbstract data = new JsonTrackingData(postalNumber);
        JsonPacket packet = new JsonPacket(
                jwt,
                JsonMethodName.POSTAL_TRACKING.toString(),
                serviceNumber,
                data
        );

        log.info("Создан запрос для отслеживания. Почтовый номер: {}, Метод: {}",
                postalNumber, JsonMethodName.POSTAL_TRACKING);

        return new JsonRequest(jsonRequest.getCrc(), packet);
    }

}