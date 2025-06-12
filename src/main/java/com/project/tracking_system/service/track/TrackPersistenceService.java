package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.dto.TrackParcelDTO;
import com.project.tracking_system.entity.*;
import com.project.tracking_system.repository.*;
import com.project.tracking_system.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Сервис для сохранения и поиска посылок пользователей.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class TrackPersistenceService {

    private final TrackParcelRepository trackParcelRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final SubscriptionService subscriptionService;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final TrackAnalyticsService trackAnalyticsService;
    private final StatusTrackService statusTrackService;
    private final TypeDefinitionTrackPostService typeDefinitionTrackPostService;

    /**
     * Сохраняет или обновляет посылку пользователя.
     *
     * @param number   номер посылки
     * @param info     информация о посылке
     * @param storeId  магазин
     * @param userId   владелец
     */
    @Transactional
    public void save(String number, TrackInfoListDTO info, Long storeId, Long userId) {
        if (number == null || info == null) {
            throw new IllegalArgumentException("Отсутствует посылка");
        }
        TrackParcel parcel = trackParcelRepository.findByNumberAndUserId(number, userId);
        boolean isNew = parcel == null;
        GlobalStatus oldStatus = isNew ? null : parcel.getStatus();
        ZonedDateTime prevDate = null;
        Long prevStoreId = null;

        if (isNew) {
            int remaining = subscriptionService.canSaveMoreTracks(userId, 1);
            if (remaining <= 0) {
                throw new IllegalArgumentException("Вы не можете сохранить больше посылок, так как превышен лимит сохранённых посылок.");
            }
            Store store = storeRepository.getReferenceById(storeId);
            User user = userRepository.getReferenceById(userId);
            parcel = new TrackParcel();
            parcel.setNumber(number.toUpperCase());
            parcel.setStore(store);
            parcel.setUser(user);
        } else {
            prevStoreId = parcel.getStore().getId();
            prevDate = parcel.getData();
            if (!parcel.getStore().getId().equals(storeId)) {
                parcel.setStore(storeRepository.getReferenceById(storeId));
            }
        }

        GlobalStatus newStatus = statusTrackService.setStatus(info.getList());
        parcel.setStatus(newStatus);
        String lastDate = info.getList().get(0).getTimex();
        ZoneId zone = userService.getUserZone(userId);
        ZonedDateTime zonedDateTime = com.project.tracking_system.utils.DateParserUtils.parse(lastDate, zone);
        parcel.setData(zonedDateTime);

        trackParcelRepository.save(parcel);

        PostalServiceType serviceType = typeDefinitionTrackPostService.detectPostalService(number);
        trackAnalyticsService.updateAnalytics(parcel, isNew, prevStoreId, prevDate, serviceType, zonedDateTime, oldStatus, newStatus, info);
    }

    /** Проверяет принадлежность посылки пользователю. */
    public boolean userOwnsParcel(String itemNumber, Long userId) {
        return trackParcelRepository.existsByNumberAndUserId(itemNumber, userId);
    }

    /** Возвращает страницу посылок по магазинам. */
    @Transactional
    public Page<TrackParcelDTO> findByStoreTracks(List<Long> storeIds, int page, int size, Long userId) {
        Pageable pageable = PageRequest.of(page, size);
        Page<TrackParcel> parcels = trackParcelRepository.findByStoreIdIn(storeIds, pageable);
        ZoneId zone = userService.getUserZone(userId);
        return parcels.map(p -> new TrackParcelDTO(p, zone));
    }

    /** Возвращает страницу посылок по магазинам и статусу. */
    @Transactional
    public Page<TrackParcelDTO> findByStoreTracksAndStatus(List<Long> storeIds, GlobalStatus status, int page, int size, Long userId) {
        Pageable pageable = PageRequest.of(page, size);
        Page<TrackParcel> parcels = trackParcelRepository.findByStoreIdInAndStatus(storeIds, status, pageable);
        ZoneId zone = userService.getUserZone(userId);
        return parcels.map(p -> new TrackParcelDTO(p, zone));
    }

    /** Возвращает все посылки пользователя. */
    @Transactional
    public List<TrackParcelDTO> findAllByUserTracks(Long userId) {
        List<TrackParcel> parcels = trackParcelRepository.findByUserId(userId);
        ZoneId zone = userService.getUserZone(userId);
        return parcels.stream().map(p -> new TrackParcelDTO(p, zone)).toList();
    }

    /** Удаляет посылки пользователя по номерам. */
    @Transactional
    public void deleteByNumbersAndUserId(List<String> numbers, Long userId) {
        List<TrackParcel> parcels = trackParcelRepository.findByNumberInAndUserId(numbers, userId);
        if (parcels.isEmpty()) {
            throw new RuntimeException("Нет посылок для удаления.");
        }
        trackParcelRepository.deleteAll(parcels);
    }

    /** Является ли трек новым в магазине. */
    @Transactional
    public boolean isNewTrack(String number, Long storeId) {
        if (storeId == null) return true;
        return trackParcelRepository.findByNumberAndStoreId(number, storeId) == null;
    }

    /** Инкремент счётчика обновлений пользователя. */
    @Transactional
    public void incrementUpdateCount(Long userId, int count) {
        userSubscriptionRepository.incrementUpdateCount(userId, count, java.time.LocalDate.now(java.time.ZoneOffset.UTC));
    }

    /** Количество всех посылок. */
    @Transactional
    public long countAllParcels() {
        return trackParcelRepository.count();
    }
}
