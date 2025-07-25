package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackParcelDTO;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.PostalServiceType;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.repository.UserSubscriptionRepository;
import com.project.tracking_system.service.user.UserService;
import com.project.tracking_system.utils.PhoneUtils;
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
     * Возвращает тип почтовой службы для сохранённой посылки.
     *
     * @param number номер посылки
     * @return тип службы или {@code null}, если посылка не найдена
     */
    @Transactional(readOnly = true)
    public PostalServiceType getPostalServiceType(String number) {
        TrackParcel parcel = trackParcelRepository.findByNumberWithStoreAndUser(number);
        if (parcel != null && parcel.getDeliveryHistory() != null) {
            return parcel.getDeliveryHistory().getPostalService();
        }
        return null;
    }

    /**
     * Проверяет, принадлежит ли посылка пользователю.
     *
     * @param itemNumber номер посылки
     * @param userId     идентификатор пользователя
     * @return true, если посылка принадлежит пользователю
     */
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
    public Page<TrackParcelDTO> findByStoreTracksAndStatus(List<Long> storeIds, GlobalStatus status, int page, int size, Long userId) {
        Pageable pageable = PageRequest.of(page, size);
        Page<TrackParcel> trackParcels = trackParcelRepository.findByStoreIdInAndStatus(storeIds, status, pageable);
        ZoneId userZone = userService.getUserZone(userId);
        return trackParcels.map(track -> new TrackParcelDTO(track, userZone));
    }

    /**
     * Выполняет поиск посылок по номеру или номеру телефона покупателя.
     *
     * @param storeIds список магазинов
     * @param status   фильтр статуса (может быть {@code null})
     * @param query    строка поиска
     * @param page     номер страницы
     * @param size     размер страницы
     * @param userId   идентификатор пользователя
     * @return страница найденных посылок
     */
    @Transactional(readOnly = true)
    public Page<TrackParcelDTO> searchByNumberOrPhone(List<Long> storeIds,
                                                      GlobalStatus status,
                                                      String query,
                                                      int page,
                                                      int size,
                                                      Long userId) {
        Pageable pageable = PageRequest.of(page, size);
        String phoneDigits = PhoneUtils.extractDigits(query);
        Page<TrackParcel> parcels = trackParcelRepository.searchByNumberOrPhone(
                storeIds, userId, status, query, phoneDigits, pageable);
        ZoneId userZone = userService.getUserZone(userId);
        return parcels.map(track -> new TrackParcelDTO(track, userZone));
    }

    /**
     * Подсчитывает общее количество посылок в системе.
     *
     * @return количество всех посылок
     */
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
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
     * Возвращает посылку по номеру и пользователю.
     * <p>
     * Используется для проверки времени последнего обновления
     * и извлечения связанных данных.
     * </p>
     *
     * @param number номер посылки
     * @param userId идентификатор пользователя
     * @return посылка или {@code null}, если не найдена
     */
    @Transactional(readOnly = true)
    public TrackParcel findByNumberAndUserId(String number, Long userId) {
        return trackParcelRepository.findByNumberAndUserId(number, userId);
    }

    /**
     * Возвращает все посылки указанного магазина.
     *
     * @param storeId идентификатор магазина
     * @param userId  идентификатор пользователя
     * @return список посылок магазина
     */
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
    public List<TrackParcelDTO> findAllByUserTracks(Long userId) {
        List<TrackParcel> trackParcels = trackParcelRepository.findByUserId(userId);
        ZoneId userZone = userService.getUserZone(userId);
        return trackParcels.stream()
                .map(track -> new TrackParcelDTO(track, userZone))
                .toList();
    }
}
