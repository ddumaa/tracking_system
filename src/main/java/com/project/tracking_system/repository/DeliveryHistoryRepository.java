package com.project.tracking_system.repository;

import com.project.tracking_system.entity.DeliveryHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * @author Dmitriy Anisimov
 * @date 15.03.2025
 */
public interface DeliveryHistoryRepository extends JpaRepository<DeliveryHistory, Integer> {

    Optional<DeliveryHistory> findByTrackParcelId(Long trackParcelId);



    // Получить записи по дате отправки в указанном диапазоне
    List<DeliveryHistory> findByStoreIdInAndSendDateBetween(List<Long> storeIds,
                                                            ZonedDateTime from,
                                                            ZonedDateTime to);

    // Получить записи по дате получения в указанном диапазоне
    List<DeliveryHistory> findByStoreIdInAndReceivedDateBetween(List<Long> storeIds,
                                                                ZonedDateTime from,
                                                                ZonedDateTime to);

    // Получить записи по дате возврата в указанном диапазоне
    List<DeliveryHistory> findByStoreIdInAndReturnedDateBetween(List<Long> storeIds,
                                                                ZonedDateTime from,
                                                                ZonedDateTime to);

}