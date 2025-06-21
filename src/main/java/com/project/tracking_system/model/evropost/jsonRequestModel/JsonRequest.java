package com.project.tracking_system.model.evropost.jsonRequestModel;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Класс, представляющий структуру запроса, отправляемого в систему EuroPost.
 * <p>
 * Этот класс используется для формирования общего запроса, который содержит информацию о CRC (контрольной сумме) и пакете данных,
 * необходимом для выполнения различных операций в системе EuroPost.
 * </p>
 *
 * @author Dmitriy Anisimov
 * @date 07.01.2025
 */
@Component
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class JsonRequest {

    @JsonProperty("CRC")
    private String crc = "";
    @JsonProperty("Packet")
    private JsonPacket packet;

}