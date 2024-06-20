package com.project.tracking_system.model.jsonRequestModel;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

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
