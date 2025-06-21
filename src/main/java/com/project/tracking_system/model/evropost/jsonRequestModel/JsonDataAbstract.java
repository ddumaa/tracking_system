package com.project.tracking_system.model.evropost.jsonRequestModel;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.Setter;

/**
 * Абстрактный класс для представления общих данных в JSON запросах, с динамическим типом.
 * <p>
 * Этот класс используется как базовый для сериализации и десериализации JSON-объектов с динамическим типом,
 * определяемым с помощью свойства {@code "type"}. Конкретные типы данных указываются через аннотацию {@link JsonSubTypes},
 * например, {@link JsonTrackingData} и {@link JsonGetJWTData}.
 * </p>
 *
 * @author Dmitriy Anisimov
 * @date 07.01.2025
 */
@Getter
@Setter
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = JsonTrackingData.class),
        @JsonSubTypes.Type(value = JsonGetJWTData.class)
})
public abstract class JsonDataAbstract {

}