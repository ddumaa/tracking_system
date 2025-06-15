package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackParcelDTO;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.repository.UserSubscriptionRepository;
import com.project.tracking_system.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.util.List;

/**
 * Базовый сервис для работы с посылками пользователя.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class TrackParcelService {

    private final UserService userService;
    private final TrackParcelRepository trackParcelRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;

    /**
     * Проверяет, принадлежит ли посылка пользователю.
     *
     * @param itemNumber номер посылки
     * @param userId     идентификатор пользователя
     * @return true, если посылка принадлежит пользователю
     */
    public boolean userOwnsParcel(String itemNumber, Long userId) {
        return trackParcelRepository.existsByNumberAndUserId(itemNumber, userId);
    }

    /**
     * Находит посылки по магазинам с учётом пагинации.
     */
    @Transactional
    public Page<TrackParcelDTO> findByStoreTracks(List<Long> storeIds, int page, int size, Long userId) {
        Pageable pageable = PageRequest.of(page, size);
        Page<TrackParcel> trackParcels = trackParcelRepository.findByStoreIdIn(storeIds, pageable);
        ZoneId userZone = userService.getUserZone(userId);
        return trackParcels.map(track -> new TrackParcelDTO(track, userZone));
    }

    /**
     * Ищет посылки магазинов по статусу с поддержкой пагинации.
     */
    @Transactional
    public Page<TrackParcelDTO> findByStoreTracksAndStatus(List<Long> storeIds, GlobalStatus status, int page, int size, Long userId) {
        Pageable pageable = PageRequest.of(page, size);
        Page<TrackParcel> trackParcels = trackParcelRepository.findByStoreIdInAndStatus(storeIds, status, pageable);
        ZoneId userZone = userService.getUserZone(userId);
        return trackParcels.map(track -> new TrackParcelDTO(track, userZone));
    }

    /**
     * Подсчитывает общее количество посылок в системе.
     */
    @Transactional
    public long countAllParcels() {
        return trackParcelRepository.count();
    }

    /**
     * Проверяет, является ли посылка новой для данного магазина.
     */
    @Transactional
    public boolean isNewTrack(String trackingNumber, Long storeId) {
        if (storeId == null) {
            return true;
        }
        TrackParcel existing = trackParcelRepository.findByNumberAndStoreId(trackingNumber, storeId);
        return (existing == null);
    }

    /**
     * Увеличивает счётчик обновлений треков для пользователя.
     */
    @Transactional
    public void incrementUpdateCount(Long userId, int count) {
        userSubscriptionRepository.incrementUpdateCount(userId, count, LocalDate.now(ZoneOffset.UTC));
    }

    /**
     * Возвращает все посылки магазина.
     */
    @Transactional
    public List<TrackParcelDTO> findAllByStoreTracks(Long storeId, Long userId) {
        List<TrackParcel> trackParcels = trackParcelRepository.findByStoreId(storeId);
        ZoneId userZone = userService.getUserZone(userId);
        return trackParcels.stream()
                .map(track -> new TrackParcelDTO(track, userZone))
                .toList();
    }

    /**
     * Возвращает все посылки пользователя.
     */
    @Transactional
    public List<TrackParcelDTO> findAllByUserTracks(Long userId) {
        List<TrackParcel> trackParcels = trackParcelRepository.findByUserId(userId);
        ZoneId userZone = userService.getUserZone(userId);
        return trackParcels.stream()
                .map(track -> new TrackParcelDTO(track, userZone))
                .toList();
    }
}
