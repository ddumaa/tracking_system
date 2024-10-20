package com.project.tracking_system.service;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.maper.JsonEvroTrackingResponseMapper;
import com.project.tracking_system.model.evropost.jsonResponseModel.JsonEvroTrackingResponse;
import com.project.tracking_system.service.belpost.WebBelPost;
import com.project.tracking_system.service.jsonEvropostService.JsonEvroTrackingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

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

    @Async("Post")
    public CompletableFuture<TrackInfoListDTO> getTypeDefinitionTrackPostServiceAsync(String number) {
        if (number.matches("^PC\\d{9}BY$") || number.matches("^BV\\d{9}BY$") ||
                number.matches("^BP\\d{9}BY$")) {
            return webBelPost.webAutomationAsync(number);
        }
        if (number.matches("^BY\\d{12}$")) {
            JsonEvroTrackingResponse json = jsonEvroTrackingService.getJson(number);
            TrackInfoListDTO trackInfoListDTO = jsonEvroTrackingResponseMapper.mapJsonEvroTrackingResponseToDTO(json);
            return CompletableFuture.completedFuture(trackInfoListDTO);
        }
        throw new IllegalArgumentException("Указан некорректный код посылки.");
    }

    public TrackInfoListDTO getTypeDefinitionTrackPostService(String number) {
        try {
            return getTypeDefinitionTrackPostServiceAsync(number).get(); // Ожидание результата
        } catch (Exception e) {
            e.printStackTrace();
            return new TrackInfoListDTO(); // Возвращаем пустой объект при ошибке
        }
    }

}