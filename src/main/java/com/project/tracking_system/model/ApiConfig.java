package com.project.tracking_system.model;

import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Component
public class ApiConfig {

    @Value("${evro.jwt.ApiUrl}")
    private String url;

    @Value("${evro.jwt.ServiceNumber}")
    private String ServiceNumber;

    @Value("${evro.jwt.LoginNameTypeId}")
    private String LoginNameTypeId;

    @Value("${evro.jwt.LoginName}")
    private String LoginName;

    @Value("${evro.jwt.Password}")
    private String Password;

    @PostConstruct
    public void init() {
        System.out.println("ApiUrl: " + url);
        System.out.println("ServiceNumber: " + ServiceNumber);
        System.out.println("LoginNameTypeId: " + LoginNameTypeId);
        System.out.println("LoginName: " + LoginName);
        System.out.println("Password: " + Password);
    }
}
