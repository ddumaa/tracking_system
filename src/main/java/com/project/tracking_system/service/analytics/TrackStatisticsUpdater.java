package com.project.tracking_system.service.analytics;

import com.project.tracking_system.entity.*;
import com.project.tracking_system.repository.*;
import com.project.tracking_system.service.track.TypeDefinitionTrackPostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;

/**
 * Обновляет статистику магазина и почтовых служб при сохранении или перемещении посылки.
 *
 * <p>Компонент инкапсулирует логику увеличения и уменьшения показателей для
 * {@link Store} и {@link PostalServiceType}.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrackStatisticsUpdater {

    private final StoreAnalyticsRepository storeAnalyticsRepository;
    private final PostalServiceStatisticsRepository postalServiceStatisticsRepository;
    private final StoreDailyStatisticsRepository storeDailyStatisticsRepository;
    private final PostalServiceDailyStatisticsRepository postalServiceDailyStatisticsRepository;
    private final TypeDefinitionTrackPostService typeDefinitionTrackPostService;

    /**
     * Обновляет статистику для переданной посылки.
     *
     * @param parcel          сохраняемая или обновляемая посылка
     * @param isNewParcel     {@code true}, если посылка создаётся впервые
     * @param previousStoreId идентификатор магазина до изменения (может быть {@code null})
     * @param previousDate    предыдущая отметка времени посылки
     */
    @Transactional
    public void updateStatistics(TrackParcel parcel,
                                 boolean isNewParcel,
                                 Long previousStoreId,
                                 ZonedDateTime previousDate) {
        boolean storeChanged = !isNewParcel
                && previousStoreId != null
                && !previousStoreId.equals(parcel.getStore().getId());

        PostalServiceType serviceType =
                typeDefinitionTrackPostService.detectPostalService(parcel.getNumber());

        if (isNewParcel || storeChanged) {
            incrementNewStore(parcel, serviceType);
        }
        if (storeChanged) {
            decrementOldStore(previousStoreId, serviceType, previousDate);
        }
    }

    /**
     * Увеличивает статистику магазина и почтовой службы для новой посылки
     * или при смене магазина.
     */
    private void incrementNewStore(TrackParcel parcel,
                                   PostalServiceType serviceType) {
        Long storeId = parcel.getStore().getId();
        StoreStatistics statistics = storeAnalyticsRepository.findByStoreId(storeId)
                .orElseThrow(() -> new IllegalStateException("Статистика не найдена"));

        // общее количество отправлений по магазину
        int updated = storeAnalyticsRepository.incrementTotalSent(storeId, 1);
        if (updated == 0) {
            statistics.setTotalSent(statistics.getTotalSent() + 1);
            statistics.setUpdatedAt(ZonedDateTime.now(ZoneOffset.UTC));
            storeAnalyticsRepository.save(statistics);
        }

        LocalDate day = parcel.getTimestamp().toLocalDate();
        int dailyUpdated = storeDailyStatisticsRepository.incrementSent(storeId, day, 1);
        if (dailyUpdated == 0) {
            StoreDailyStatistics daily = storeDailyStatisticsRepository
                    .findByStoreIdAndDate(storeId, day)
                    .orElseGet(() -> {
                        StoreDailyStatistics d = new StoreDailyStatistics();
                        d.setStore(statistics.getStore());
                        d.setDate(day);
                        return d;
                    });
            daily.setSent(daily.getSent() + 1);
            daily.setUpdatedAt(ZonedDateTime.now(ZoneOffset.UTC));
            storeDailyStatisticsRepository.save(daily);
        }

        if (serviceType != PostalServiceType.UNKNOWN) {
            updatePostalIncrement(storeId, statistics.getStore(), serviceType, day);
        } else {
            log.warn("⛔ Пропуск обновления аналитики для UNKNOWN службы: {}", parcel.getNumber());
        }
    }

    /**
     * Увеличивает статистику по выбранной почтовой службе для магазина.
     */
    private void updatePostalIncrement(Long storeId,
                                       Store store,
                                       PostalServiceType serviceType,
                                       LocalDate day) {
        int psUpdated = postalServiceStatisticsRepository.incrementTotalSent(storeId, serviceType, 1);
        if (psUpdated == 0) {
            PostalServiceStatistics psStats = postalServiceStatisticsRepository
                    .findByStoreIdAndPostalServiceType(storeId, serviceType)
                    .orElseGet(() -> {
                        PostalServiceStatistics s = new PostalServiceStatistics();
                        s.setStore(store);
                        s.setPostalServiceType(serviceType);
                        return s;
                    });
            psStats.setTotalSent(psStats.getTotalSent() + 1);
            psStats.setUpdatedAt(ZonedDateTime.now(ZoneOffset.UTC));
            postalServiceStatisticsRepository.save(psStats);
        }

        int psdUpdated = postalServiceDailyStatisticsRepository.incrementSent(storeId, serviceType, day, 1);
        if (psdUpdated == 0) {
            PostalServiceDailyStatistics psDaily = postalServiceDailyStatisticsRepository
                    .findByStoreIdAndPostalServiceTypeAndDate(storeId, serviceType, day)
                    .orElseGet(() -> {
                        PostalServiceDailyStatistics d = new PostalServiceDailyStatistics();
                        d.setStore(store);
                        d.setPostalServiceType(serviceType);
                        d.setDate(day);
                        return d;
                    });
            psDaily.setSent(psDaily.getSent() + 1);
            psDaily.setUpdatedAt(ZonedDateTime.now(ZoneOffset.UTC));
            postalServiceDailyStatisticsRepository.save(psDaily);
        }
    }

    /**
     * Уменьшает статистику предыдущего магазина, если посылка была перемещена.
     */
    private void decrementOldStore(Long previousStoreId,
                                   PostalServiceType serviceType,
                                   ZonedDateTime previousDate) {
        StoreStatistics oldStats = storeAnalyticsRepository.findByStoreId(previousStoreId)
                .orElseThrow(() -> new IllegalStateException("Статистика не найдена"));
        if (oldStats.getTotalSent() > 0) {
            oldStats.setTotalSent(oldStats.getTotalSent() - 1);
            oldStats.setUpdatedAt(ZonedDateTime.now(ZoneOffset.UTC));
            storeAnalyticsRepository.save(oldStats);
        }

        if (serviceType != PostalServiceType.UNKNOWN) {
            PostalServiceStatistics oldPs = postalServiceStatisticsRepository
                    .findByStoreIdAndPostalServiceType(previousStoreId, serviceType)
                    .orElse(null);
            if (oldPs != null && oldPs.getTotalSent() > 0) {
                oldPs.setTotalSent(oldPs.getTotalSent() - 1);
                oldPs.setUpdatedAt(ZonedDateTime.now(ZoneOffset.UTC));
                postalServiceStatisticsRepository.save(oldPs);
            }
        }

        if (previousDate != null) {
            LocalDate prevDay = previousDate.toLocalDate();
            StoreDailyStatistics oldDaily = storeDailyStatisticsRepository
                    .findByStoreIdAndDate(previousStoreId, prevDay)
                    .orElse(null);
            if (oldDaily != null && oldDaily.getSent() > 0) {
                oldDaily.setSent(oldDaily.getSent() - 1);
                oldDaily.setUpdatedAt(ZonedDateTime.now(ZoneOffset.UTC));
                storeDailyStatisticsRepository.save(oldDaily);
            }

            if (serviceType != PostalServiceType.UNKNOWN) {
                PostalServiceDailyStatistics oldPsDaily = postalServiceDailyStatisticsRepository
                        .findByStoreIdAndPostalServiceTypeAndDate(previousStoreId, serviceType, prevDay)
                        .orElse(null);
                if (oldPsDaily != null && oldPsDaily.getSent() > 0) {
                    oldPsDaily.setSent(oldPsDaily.getSent() - 1);
                    oldPsDaily.setUpdatedAt(ZonedDateTime.now(ZoneOffset.UTC));
                    postalServiceDailyStatisticsRepository.save(oldPsDaily);
                }
            }
        }
    }
}
