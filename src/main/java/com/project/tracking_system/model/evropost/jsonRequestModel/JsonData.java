package com.project.tracking_system.model.evropost.jsonRequestModel;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Класс, представляющий данные для отправки в JSON запросах.
 * <p>
 * Этот класс используется для хранения данных, таких как имя пользователя, пароль и другие параметры,
 * необходимые для взаимодействия с системой EuroPost через JWT.
 * </p>
 *
 * @author Dmitriy Anisimov
 * @date 07.01.2025
 */
@Component
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JsonData {

    @Value("${evro.jwt.LoginName}")
    private String loginName;

    @Value("${evro.jwt.Password}")
    private String password;

    @Value("${evro.jwt.LoginNameTypeId}")
    private String loginNameTypeId;

    private String number;

    private String postalItemExternalId;

}