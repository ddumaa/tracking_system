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
     *
     * @param storeIds список идентификаторов магазинов
     * @param page     номер страницы
     * @param size     размер страницы
     * @param userId   идентификатор пользователя
     * @return страница посылок указанного пользователя
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
     *
     * @param storeIds список идентификаторов магазинов
     * @param status   статус посылки
     * @param page     номер страницы
     * @param size     размер страницы
     * @param userId   идентификатор пользователя
     * @return страница посылок
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
     *
     * @return количество всех посылок
     */
    @Transactional
    public long countAllParcels() {
        return trackParcelRepository.count();
    }

    /**
     * Проверяет, является ли посылка новой для указанного магазина.
     *
     * @param trackingNumber номер отслеживания
     * @param storeId        идентификатор магазина (может быть {@code null})
     * @return {@code true}, если такой посылки ещё нет в магазине
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
     *
     * @param userId идентификатор пользователя
     * @param count  величина увеличения счётчика
     */
    @Transactional
    public void incrementUpdateCount(Long userId, int count) {
        userSubscriptionRepository.incrementUpdateCount(userId, count, LocalDate.now(ZoneOffset.UTC));
    }

    /**
     * Возвращает все посылки указанного магазина.
     *
     * @param storeId идентификатор магазина
     * @param userId  идентификатор пользователя
     * @return список посылок магазина
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
     *
     * @param userId идентификатор пользователя
     * @return список всех его посылок
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
