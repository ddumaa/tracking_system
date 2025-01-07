package com.project.tracking_system.model.evropost.jsonRequestModel;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * Класс, представляющий данные для получения JWT токена в системе EuroPost.
 * <p>
 * Этот класс используется для отправки данных аутентификации, включая имя пользователя, пароль и тип имени,
 * чтобы получить JWT токен для дальнейшего взаимодействия с системой EuroPost.
 * </p>
 *
 * @author Dmitriy Anisimov
 * @date 07.01.2025
 */
@Getter
@Setter
public class JsonGetJWTData extends JsonDataAbstract{

    @JsonProperty("LoginName")
    private String loginName;

    @JsonProperty("Password")
    private String password;

    @JsonProperty("LoginNameTypeId")
    private String loginNameTypeId;

    public JsonGetJWTData(String loginName, String password, String loginNameTypeId) {
        this.loginName = loginName;
        this.password = password;
        this.loginNameTypeId = loginNameTypeId;
    }
}
