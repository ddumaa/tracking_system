package com.project.tracking_system.service.track;

import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.service.analytics.DeliveryHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Сервис удаления треков пользователя.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class TrackDeletionService {

    private final TrackParcelRepository trackParcelRepository;
    private final DeliveryHistoryService deliveryHistoryService;

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

        // Обнуляем связь с DeliveryHistory, чтобы Hibernate не пытался сохранять зависимую сущность
        for (TrackParcel parcel : parcelsToDelete) {
            deliveryHistoryService.handleTrackParcelBeforeDelete(parcel);

            if (parcel.getDeliveryHistory() != null) {
                parcel.getDeliveryHistory().setTrackParcel(null);
                parcel.setDeliveryHistory(null);
            }
        }

        trackParcelRepository.deleteAll(parcelsToDelete);
        log.info("✅ Удалены {} посылок пользователя ID={}", parcelsToDelete.size(), userId);
    }
}
