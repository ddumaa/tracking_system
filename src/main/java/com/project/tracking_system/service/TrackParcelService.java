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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
            Long userId = user.get().getId();
            TrackParcel trackParcel = trackParcelRepository.findByNumberAndUserId(number, userId);
            if (trackParcel != null) {
                // обновляем существующую запись
                trackParcel.setStatus(statusTrackService.setStatus(trackInfoDTOList));
                trackParcel.setData(trackInfoDTOList.get(0).getTimex());
            } else {
                // создаём новую запись
                trackParcel = new TrackParcel();
                trackParcel.setNumber(number);
                trackParcel.setUser(user.get());
                trackParcel.setStatus(statusTrackService.setStatus(trackInfoDTOList));
                trackParcel.setData(trackInfoDTOList.get(0).getTimex());
            }
            trackParcelRepository.save(trackParcel);

        }
    }

    public Page<TrackParcelDTO> findByUserTracks(String username, int page, int size){
        Optional<User> byUser = userService.findByUser(username);
        if (byUser.isPresent()) {
            Long id = byUser.get().getId();
            Pageable pageable = PageRequest.of(page, size);
            Page<TrackParcel> trackParcels = trackParcelRepository.findByUserId(id, pageable);
            return trackParcels.map(TrackParcelDTO::new);
        }
        return Page.empty();
    }

    public Page<TrackParcelDTO> findByUserTracksAndStatus(String username, GlobalStatus status, int page, int size) {
        Optional<User> byUser = userService.findByUser(username);
        if (byUser.isPresent()) {
            Long id = byUser.get().getId();
            // Получаем статус как строку
            String statusString = status != null ? status.getDescription() : null;
            // Создаем объект Pageable
            Pageable pageable = PageRequest.of(page, size);
            // Получаем страницу посылок
            Page<TrackParcel> trackParcels = trackParcelRepository.findByUserIdAndStatus(id, statusString, pageable);
            // Преобразуем страницу TrackParcel в страницу TrackParcelDTO
            return trackParcels.map(TrackParcelDTO::new);
        }
        return Page.empty();
    }

    public List<TrackParcelDTO> findAllByUserTracks(String username) {
        Optional<User> byUser = userService.findByUser(username);
        if (byUser.isPresent()) {
            Long id = byUser.get().getId();
            List<TrackParcel> trackParcels = trackParcelRepository.findByUserId(id);
            return convertToDTO(trackParcels);
        }
        return List.of();
    }

    // Вспомогательный метод для преобразования посылок в DTO
    private List<TrackParcelDTO> convertToDTO(List<TrackParcel> parcels) {
        List<TrackParcelDTO> dtoList = new ArrayList<>();
        for (TrackParcel parcel : parcels) {
            dtoList.add(new TrackParcelDTO(parcel));
        }
        return dtoList;
    }

    public void updateHistory (String name){
        List<TrackParcelDTO> byUserTrack = findAllByUserTracks(name);
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
                            trackParcel.setStatus(statusTrackService.setStatus(list));
                            trackParcel.setData(list.get(0).getTimex());
                            trackParcelRepository.save(trackParcel);
                        });
                futures.add(future); // Добавляем асинхронную задачу в список
            }
        }
        // Ждём завершения всех асинхронных операций
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    public void deleteByNumbersAndUserId(List<String> numbers, Long userId) {
        List<TrackParcel> parcelsToDelete = trackParcelRepository.findByNumberInAndUserId(numbers, userId);
        if (parcelsToDelete.isEmpty()) {
            throw new RuntimeException("Нет посылок для удаления.");
        }
        trackParcelRepository.deleteAll(parcelsToDelete);
    }

}