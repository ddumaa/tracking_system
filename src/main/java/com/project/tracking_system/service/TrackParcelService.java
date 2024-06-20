package com.project.tracking_system.service;

import com.project.tracking_system.dto.TrackParcelDTO;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.repository.TrackParcelRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class TrackParcelService {

    private final TrackParcelRepository trackParcelRepository;
    private final UserService userService;

    @Autowired
    public TrackParcelService(TrackParcelRepository trackParcelRepository, UserService userService) {
        this.trackParcelRepository = trackParcelRepository;
        this.userService = userService;
    }

    public void save(String number, String username) {
        if (number == null) {
            throw new IllegalArgumentException("Отсутствует номер посылки");
        }
        Optional<User> byUser = userService.findByUser(username);
        if (byUser.isPresent()) {
            TrackParcel trackParcel = new TrackParcel();
            trackParcel.setNumber(number);
            trackParcel.setUser(byUser.get());
            trackParcelRepository.save(trackParcel);
        }
    }

    public List<TrackParcelDTO> findByUserTrack(String username) {
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
}