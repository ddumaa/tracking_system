package com.project.tracking_system.service;

import com.project.tracking_system.dto.TrackParcelDTO;
import com.project.tracking_system.dto.TrackInfoDTO;
import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.service.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class TrackParcelService {

    private final TrackParcelRepository trackParcelRepository;
    private final UserService userService;
    private final TypeDefinitionTrackPostService typeDefinitionTrackPostService;
    private final StatusTrackService statusTrackService;

    @Autowired
    public TrackParcelService(TrackParcelRepository trackParcelRepository, UserService userService,
                              TypeDefinitionTrackPostService typeDefinitionTrackPostService,
                              StatusTrackService statusTrackService) {
        this.trackParcelRepository = trackParcelRepository;
        this.userService = userService;
        this.typeDefinitionTrackPostService = typeDefinitionTrackPostService;
        this.statusTrackService = statusTrackService;
    }

    public void save(String number, TrackInfoListDTO trackInfoListDTO, String username) {
        if (number == null || trackInfoListDTO == null) {
            throw new IllegalArgumentException("Отсутствует посылка");
        }
        List<TrackInfoDTO> trackInfoDTOList = trackInfoListDTO.getList();
        Optional<User> user = userService.findByUser(username);

        if (user.isPresent()) {
            TrackParcel trackParcel = new TrackParcel();
            trackParcel.setNumber(number);
            trackParcel.setUser(user.get());
            trackParcel.setStatus(statusTrackService.setStatus(trackInfoDTOList.get(0).getInfoTrack()));
            trackParcel.setData(trackInfoDTOList.get(0).getTimex());
            trackParcelRepository.save(trackParcel);
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
        TrackInfoListDTO trackInfoListDTO;
        for (TrackParcelDTO trackParcelDTO : byUserTrack) {
            if(trackParcelDTO.getStatus().startsWith("Вручена") ||
                    trackParcelDTO.getStatus().startsWith("Возврат забран") ){
                continue;
            } else {
                trackInfoListDTO = typeDefinitionTrackPostService.getTypeDefinitionTrackPostService(trackParcelDTO.getNumber());
            }
            List<TrackInfoDTO> list = trackInfoListDTO.getList();
            Optional<User> user = userService.findByUser(name);
            Long userId = user.get().getId();
            TrackParcel trackParcel = trackParcelRepository.findByNumberAndUserId(trackParcelDTO.getNumber(), userId);
            trackParcel.setStatus(statusTrackService.setStatus(list.get(0).getInfoTrack()));
            trackParcel.setData(list.get(0).getTimex());
            trackParcelRepository.save(trackParcel);
        }
    }
}