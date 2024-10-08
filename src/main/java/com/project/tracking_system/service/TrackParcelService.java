package com.project.tracking_system.service;

import com.project.tracking_system.dto.TrackParcelDTO;
import com.project.tracking_system.dto.TrackInfoDTO;
import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.model.GlobalStatus;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.service.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

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

    public List<TrackParcelDTO> findByUserTracksAndStatus(String username, GlobalStatus status) {
        Optional<User> byUser = userService.findByUser(username);
        if (byUser.isPresent()) {
            Long id = byUser.get().getId();
            // Получаем статус как строку
            String statusString = status != null ? status.getDescription() : null;
            List<TrackParcel> byUserIdAndStatus = trackParcelRepository.findByUserIdAndStatus(id, statusString);
            List<TrackParcelDTO> trackParcelDTOList = new ArrayList<>();
            for (TrackParcel trackParcel : byUserIdAndStatus) {
                trackParcelDTOList.add(new TrackParcelDTO(trackParcel));
            }
            return trackParcelDTOList;
        }
        return List.of();
    }

    public void updateHistory (String name){
        List<TrackParcelDTO> byUserTrack = findByUserTracks(name);
        List<CompletableFuture<Void>> futures = new ArrayList<>(); // Список для хранения асинхронных задач

        for (TrackParcelDTO trackParcelDTO : byUserTrack) {
            if (trackParcelDTO.getStatus().equals(GlobalStatus.DELIVERED.getDescription()) ||
                    trackParcelDTO.getStatus().equals(GlobalStatus.RETURNED_TO_SENDER.getDescription())) {
                continue;  // Пропускаем, если статус "Вручена" или "Возврат забран"
            } else {
                // Вызываем асинхронный метод и добавляем CompletableFuture в список
                CompletableFuture<Void> future = typeDefinitionTrackPostService
                        .getTypeDefinitionTrackPostServiceAsync(trackParcelDTO.getNumber())
                        .thenAccept(trackInfoListDTO -> {
                            List<TrackInfoDTO> list = trackInfoListDTO.getList();
                            Optional<User> user = userService.findByUser(name);
                            Long userId = user.get().getId();
                            TrackParcel trackParcel = trackParcelRepository.findByNumberAndUserId(trackParcelDTO.getNumber(), userId);
                            trackParcel.setStatus(statusTrackService.setStatus(list.get(0).getInfoTrack()));
                            trackParcel.setData(list.get(0).getTimex());
                            trackParcelRepository.save(trackParcel);
                        });
                futures.add(future); // Добавляем асинхронную задачу в список
            }
        }
        // Ждём завершения всех асинхронных операций
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }
}