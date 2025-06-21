package com.project.tracking_system.model.evropost.jsonResponseModel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Модель для представления ответа от сервиса EuroPost, содержащего список данных о трекинге посылок.
 * <p>
 * Эта модель используется для сериализации и десериализации JSON-ответа, получаемого от сервиса отслеживания посылок EuroPost.
 * Она представляет собой обёртку для поля "Table", которое содержит список объектов {@link JsonEvroTracking}.
 * </p>
 *
 * @author Dmitriy Anisimov
 * @date 07.01.2025
 */
@Component
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class JsonEvroTrackingResponse {

    @JsonProperty("Table")
    private List<JsonEvroTracking> table;
}
