package com.project.tracking_system.model.jsonRequestModel;

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
    private String loginName;

    @Value("${evro.jwt.Password}")
    private String password;

    @Value("${evro.jwt.LoginNameTypeId}")
    private String loginNameTypeId;

    private String number;

    private String postalItemExternalId;

}