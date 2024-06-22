package com.project.tracking_system.service;

import com.project.tracking_system.dto.TrackParcelDTO;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.model.jsonResponseModel.JsonEvroTracking;
import com.project.tracking_system.model.jsonResponseModel.JsonEvroTrackingResponse;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.service.JsonService.JsonEvroTrackingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class TrackParcelService {

    private final TrackParcelRepository trackParcelRepository;
    private final UserService userService;
    private final JsonEvroTrackingService jsonEvroTrackingService;

    @Autowired
    public TrackParcelService(TrackParcelRepository trackParcelRepository, UserService userService, JsonEvroTrackingService jsonEvroTrackingService) {
        this.trackParcelRepository = trackParcelRepository;
        this.userService = userService;
        this.jsonEvroTrackingService = jsonEvroTrackingService;

    }

    public void save(String number, JsonEvroTrackingResponse jsonEvroTrackingResponse, String username) {
        if (number == null || jsonEvroTrackingResponse == null) {
            throw new IllegalArgumentException("Отсутствует посылка");
        }
        List<JsonEvroTracking> table = jsonEvroTrackingResponse.getTable();
        Optional<User> user = userService.findByUser(username);
        if (user.isPresent()) {
            Long userId = user.get().getId();
            TrackParcel trackParcel = trackParcelRepository.findByNumberAndUserId(number, userId);
            if (trackParcel != null) {
                trackParcel.setStatus(table.get(0).getInfoTrack());
                trackParcel.setData(table.get(0).getTimex());
                trackParcelRepository.save(trackParcel);
            } else {
                trackParcel = new TrackParcel();
                trackParcel.setNumber(number);
                trackParcel.setUser(user.get());
                trackParcel.setStatus(table.get(0).getInfoTrack());
                trackParcel.setData(table.get(0).getTimex());
                trackParcelRepository.save(trackParcel);
            }
        }
    }

    public List<TrackParcelDTO> findByUserTracks(String username){
        Optional<User> byUser = userService.findByUser(username);
        if (byUser.isPresent()) {
            Long id = byUser.get().getId();
            List<TrackParcel> byUserId = trackParcelRepository.findByUserId(id);
            List<TrackParcelDTO> trackParcelDTOList = new ArrayList<>();
            for (TrackParcel trackParcel : byUserId) {
                trackParcelDTOList.add(new TrackParcelDTO(trackParcel));
            }
            return trackParcelDTOList;
        }
        return List.of();
    }

    public void updateHistory (String name){
        List<TrackParcelDTO> byUserTrack = findByUserTracks(name);
        for (TrackParcelDTO trackParcelDTO : byUserTrack) {
            JsonEvroTrackingResponse json = jsonEvroTrackingService.getJson(trackParcelDTO.getNumber());
            save(trackParcelDTO.getNumber(), json, name);
        }
    }
}