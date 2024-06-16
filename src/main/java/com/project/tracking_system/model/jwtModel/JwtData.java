package com.project.tracking_system.model.jwtModel;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JwtData {

    @JsonProperty("LoginName")
    private String LoginName;
    @JsonProperty("Password")
    private String Password;
    @JsonProperty("LoginNameTypeId")
    private String LoginNameTypeId;

}
