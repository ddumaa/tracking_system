package com.project.tracking_system.listener;

import com.project.tracking_system.model.JwtToken;
import com.project.tracking_system.service.JsonService.GetJwtTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenGeneratorListener implements ApplicationListener<ApplicationEvent> {

    private final GetJwtTokenService jwtTokenService;
    private final JwtToken jwtToken;

    @Autowired
    public JwtTokenGeneratorListener(GetJwtTokenService jwtTokenService, JwtToken jwtToken) {
        this.jwtTokenService = jwtTokenService;

        this.jwtToken = jwtToken;

    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if(jwtToken == null) jwtToken.setToken(jwtTokenService.getJwtToken());
    }
}
