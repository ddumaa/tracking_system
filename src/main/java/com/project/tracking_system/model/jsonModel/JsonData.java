package com.project.tracking_system.model.jsonModel;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JsonData {

    @Value("${evro.jwt.LoginName}")
    @JsonProperty("LoginName")
    private String LoginName;

    @Value("${evro.jwt.Password}")
    @JsonProperty("Password")
    private String Password;

    @Value("${evro.jwt.LoginNameTypeId}")
    @JsonProperty("LoginNameTypeId")
    private String LoginNameTypeId;

    @JsonProperty("Number")
    private String Number;

    @JsonProperty("PostalItemExternalId")
    private String PostalItemExternalId;

    public JsonData(String Number, String PostalItemExternalId) {
        this.Number = Number;
        this.PostalItemExternalId = PostalItemExternalId;
    }

    public JsonData(String LoginName, String Password, String LoginNameTypeId) {
        this.LoginName = LoginName;
        this.Password = Password;
        this.LoginNameTypeId = LoginNameTypeId;
    }

}
