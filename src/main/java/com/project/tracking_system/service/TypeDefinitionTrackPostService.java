package com.project.tracking_system.service;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.maper.JsonEvroTrackingResponseMapper;
import com.project.tracking_system.model.evropost.jsonResponseModel.JsonEvroTrackingResponse;
import com.project.tracking_system.service.belpost.WebBelPost;
import com.project.tracking_system.service.jsonEvropostService.JsonEvroTrackingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TypeDefinitionTrackPostService {

    private final WebBelPost webBelPost;
    private final JsonEvroTrackingService jsonEvroTrackingService;
    private final JsonEvroTrackingResponseMapper jsonEvroTrackingResponseMapper;

    @Autowired
    public TypeDefinitionTrackPostService(WebBelPost webBelPost, JsonEvroTrackingService jsonEvroTrackingService,
                                          JsonEvroTrackingResponseMapper jsonEvroTrackingResponseMapper) {
        this.webBelPost = webBelPost;
        this.jsonEvroTrackingService = jsonEvroTrackingService;
        this.jsonEvroTrackingResponseMapper = jsonEvroTrackingResponseMapper;
    }

    public TrackInfoListDTO getTypeDefinitionTrackPostService(String number) {
        TrackInfoListDTO trackInfoListDTO = new TrackInfoListDTO();
        if (number.matches("^PC\\d{9}BY$") || number.matches("^BV\\d{9}BY$") ||
                number.matches("^BP\\d{9}BY$")) {
            trackInfoListDTO = webBelPost.webAutomation(number);
        }
        if (number.matches("^BY\\d{12}$")){
            JsonEvroTrackingResponse json = jsonEvroTrackingService.getJson(number);
            trackInfoListDTO = jsonEvroTrackingResponseMapper.mapJsonEvroTrackingResponseToDTO(json);
        }
        return trackInfoListDTO;
    }

}