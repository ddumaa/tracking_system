package com.project.tracking_system.listener;

import com.project.tracking_system.model.ApiConfig;
import com.project.tracking_system.model.JwtToken;
import com.project.tracking_system.model.jwtModel.JwtData;
import com.project.tracking_system.model.jwtModel.Packet;
import com.project.tracking_system.service.DecoderService;
import com.project.tracking_system.service.JwtTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenGeneratorListener implements ApplicationListener<ApplicationEvent> {

//    @Value("${secret.Password}")
//    private String SecretPassword;
    
    private final JwtTokenService jwtTokenService;
    private final DecoderService decoderService;
    private final ApiConfig apiConfig;
    private final JwtToken jwtToken;

    @Autowired
    public JwtTokenGeneratorListener(JwtTokenService jwtTokenService, DecoderService decoderService, ApiConfig apiConfig, JwtToken jwtToken) {
        this.jwtTokenService = jwtTokenService;
        this.decoderService = decoderService;
        this.apiConfig = apiConfig;
        this.jwtToken = jwtToken;
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
//        String Url = decoderService.decode(apiConfig.getUrl(), SecretPassword);
//        String ServiceNumber = decoderService.decode(apiConfig.getServiceNumber(), SecretPassword);
//        String LoginName = decoderService.decode(apiConfig.getLoginName(), SecretPassword);
//        String Password = decoderService.decode(apiConfig.getPassword(), SecretPassword);
//        String LoginNameTypeId = decoderService.decode(apiConfig.getLoginNameTypeId(), SecretPassword);

        jwtToken.setToken(jwtTokenService.getJwtToken(
                apiConfig.getUrl(),
                apiConfig.getServiceNumber(),
                apiConfig.getLoginName(),
                apiConfig.getPassword(),
                apiConfig.getLoginNameTypeId()
                )
        );
    }
}
