package com.project.tracking_system.model.evropost.jsonRequestModel;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Класс, представляющий структуру пакета для отправки запросов в систему EuroPost.
 * <p>
 * Этот класс используется для формирования пакета данных, который отправляется в систему EuroPost для выполнения различных операций,
 * таких как отслеживание посылки или получение JWT токена.
 * </p>
 *
 * @author Dmitriy Anisimov
 * @date 07.01.2025
 */
@Component
@Data
@AllArgsConstructor
@NoArgsConstructor
public class JsonPacket {

    @JsonProperty("JWT")
    private String jwt;

    @JsonProperty("MethodName")
    private String methodName;

    @Value("${evro.jwt.ServiceNumber}")
    @JsonProperty("ServiceNumber")
    private String serviceNumber;

    @JsonProperty("Data")
    private JsonDataAbstract data;

}