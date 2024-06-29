package com.project.tracking_system.listener;

import com.project.tracking_system.service.jsonEvropostService.GetJwtTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenGeneratorListener implements ApplicationListener<ContextRefreshedEvent> {

    private final GetJwtTokenService jwtTokenService;
    private boolean isExecuted = false;

    @Autowired
    public JwtTokenGeneratorListener(GetJwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (!isExecuted) {
            jwtTokenService.getJwtToken();
            isExecuted = true;
        }
    }
}
