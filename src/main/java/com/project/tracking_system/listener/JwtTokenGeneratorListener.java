package com.project.tracking_system.listener;

import com.project.tracking_system.model.jsonRequestModel.JsonPacket;
import com.project.tracking_system.service.JsonService.GetJwtTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenGeneratorListener implements ApplicationListener<ApplicationEvent> {

    private final GetJwtTokenService jwtTokenService;
    private final JsonPacket jsonPacket;

    @Autowired
    public JwtTokenGeneratorListener(GetJwtTokenService jwtTokenService, JsonPacket jsonPacket) {
        this.jwtTokenService = jwtTokenService;
        this.jsonPacket = jsonPacket;

    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if(jsonPacket.getJWT().equals("null")) jsonPacket.setJWT(jwtTokenService.getJwtToken());
    }
}
