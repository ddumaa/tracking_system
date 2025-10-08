package com.project.tracking_system.service.track;

import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.repository.OrderReturnRequestRepository;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.service.analytics.DeliveryHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Сервис удаления треков пользователя.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class TrackDeletionService {

    private final TrackParcelRepository trackParcelRepository;
    private final DeliveryHistoryService deliveryHistoryService;
    private final OrderReturnRequestRepository orderReturnRequestRepository;

    /**
     * Удаляет посылки пользователя по номерам.
     *
     * @param numbers список номеров посылок
     * @param userId  идентификатор пользователя
     * @throws EntityNotFoundException если посылки не найдены
     */
    @Transactional
    public void deleteByNumbersAndUserId(List<String> numbers, Long userId) {
        log.info("Начало удаления посылок {} пользователя ID={}", numbers, userId);

        List<TrackParcel> parcelsToDelete = trackParcelRepository.findByNumberInAndUserId(numbers, userId);

        if (parcelsToDelete.isEmpty()) {
            log.warn("❌ Попытка удаления несуществующих посылок. userId={}, номера={}", userId, numbers);
            throw new EntityNotFoundException("Нет посылок для удаления");
        }

        detachDeliveryHistory(parcelsToDelete);
        deleteLinkedReturnRequests(parcelsToDelete);
        trackParcelRepository.deleteAll(parcelsToDelete);
        log.info("✅ Удалены {} посылок пользователя ID={}", parcelsToDelete.size(), userId);
    }

    /**
     * Удаляет посылки пользователя по их идентификаторам.
     *
     * @param ids    список идентификаторов посылок
     * @param userId идентификатор пользователя
     * @throws EntityNotFoundException если посылки не найдены
     */
    @Transactional
    public void deleteByIdsAndUserId(List<Long> ids, Long userId) {
        log.info("Начало удаления посылок по ID {} пользователя ID={}", ids, userId);

        List<TrackParcel> parcelsToDelete = trackParcelRepository.findByIdInAndUserId(ids, userId);

        if (parcelsToDelete.isEmpty()) {
            log.warn("❌ Попытка удаления несуществующих посылок по ID. userId={}, ids={}", userId, ids);
            throw new EntityNotFoundException("Нет посылок для удаления");
        }

        detachDeliveryHistory(parcelsToDelete);
        deleteLinkedReturnRequests(parcelsToDelete);
        trackParcelRepository.deleteAll(parcelsToDelete);
        log.info("✅ Удалены {} посылок пользователя ID={}", parcelsToDelete.size(), userId);
    }

    /**
     * Открепляет историю доставки от посылок перед удалением, чтобы Hibernate не пытался
     * каскадно сохранять связанные записи.
     *
     * @param parcels посылки, подлежащие очистке
     */
    private void detachDeliveryHistory(List<TrackParcel> parcels) {
        for (TrackParcel parcel : parcels) {
            deliveryHistoryService.handleTrackParcelBeforeDelete(parcel);

            if (parcel.getDeliveryHistory() != null) {
                parcel.getDeliveryHistory().setTrackParcel(null);
                parcel.setDeliveryHistory(null);
            }
        }
    }

    /**
     * Удаляет связанные с посылками заявки на возврат/обмен, чтобы избежать нарушений
     * внешних ключей при очистке треков.
     *
     * @param parcels список удаляемых посылок
     */
    private void deleteLinkedReturnRequests(List<TrackParcel> parcels) {
        List<Long> parcelIds = parcels.stream()
                .map(TrackParcel::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (parcelIds.isEmpty()) {
            return;
        }

        long deleted = orderReturnRequestRepository.deleteByParcel_IdIn(parcelIds);
        if (deleted > 0) {
            log.info("🗑️ Удалено {} заявок на возврат, связанных с посылками {}", deleted, parcelIds);
        }
    }
}
