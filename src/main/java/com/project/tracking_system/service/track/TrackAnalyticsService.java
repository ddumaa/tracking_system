package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.entity.*;
import com.project.tracking_system.repository.*;
import com.project.tracking_system.service.analytics.DeliveryHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Сервис для обновления статистики и истории доставки посылок.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class TrackAnalyticsService {

    private final StoreAnalyticsRepository storeAnalyticsRepository;
    private final PostalServiceStatisticsRepository postalServiceStatisticsRepository;
    private final StoreDailyStatisticsRepository storeDailyStatisticsRepository;
    private final PostalServiceDailyStatisticsRepository postalServiceDailyStatisticsRepository;
    private final DeliveryHistoryService deliveryHistoryService;

    /**
     * Обновляет статистику магазинов и историю доставки.
     *
     * @param trackParcel     посылка
     * @param isNewParcel     новая ли посылка
     * @param previousStoreId идентификатор старого магазина
     * @param previousDate    предыдущая дата посылки
     * @param serviceType     тип почтовой службы
     * @param zonedDateTime   дата последнего события
     * @param oldStatus       старый статус
     * @param newStatus       новый статус
     * @param infoListDTO     список статусов трекинга
     */
    @Transactional
    public void updateAnalytics(TrackParcel trackParcel,
                                boolean isNewParcel,
                                Long previousStoreId,
                                ZonedDateTime previousDate,
                                PostalServiceType serviceType,
                                ZonedDateTime zonedDateTime,
                                GlobalStatus oldStatus,
                                GlobalStatus newStatus,
                                TrackInfoListDTO infoListDTO) {
        Long storeId = trackParcel.getStore().getId();
        boolean storeChanged = !isNewParcel && previousStoreId != null && !previousStoreId.equals(storeId);

        // Инкрементируем статистику нового магазина
        if (isNewParcel || storeChanged) {
            StoreStatistics statistics = storeAnalyticsRepository.findByStoreId(storeId)
                    .orElseThrow(() -> new IllegalStateException("Статистика не найдена"));

            int updated = storeAnalyticsRepository.incrementTotalSent(storeId, 1);
            if (updated == 0) {
                statistics.setTotalSent(statistics.getTotalSent() + 1);
                statistics.setUpdatedAt(ZonedDateTime.now(ZoneOffset.UTC));
                storeAnalyticsRepository.save(statistics);
            }

            if (serviceType != PostalServiceType.UNKNOWN) {
                int psUpdated = postalServiceStatisticsRepository.incrementTotalSent(storeId, serviceType, 1);
                if (psUpdated == 0) {
                    PostalServiceStatistics psStats = postalServiceStatisticsRepository
                            .findByStoreIdAndPostalServiceType(storeId, serviceType)
                            .orElseGet(() -> {
                                PostalServiceStatistics s = new PostalServiceStatistics();
                                s.setStore(statistics.getStore());
                                s.setPostalServiceType(serviceType);
                                return s;
                            });
                    psStats.setTotalSent(psStats.getTotalSent() + 1);
                    psStats.setUpdatedAt(ZonedDateTime.now(ZoneOffset.UTC));
                    postalServiceStatisticsRepository.save(psStats);
                }
            } else {
                log.warn("⛔ Skipping analytics update for UNKNOWN service: {}", trackParcel.getNumber());
            }

            LocalDate day = zonedDateTime.toLocalDate();
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
                int psdUpdated = postalServiceDailyStatisticsRepository.incrementSent(storeId, serviceType, day, 1);
                if (psdUpdated == 0) {
                    PostalServiceDailyStatistics psDaily = postalServiceDailyStatisticsRepository
                            .findByStoreIdAndPostalServiceTypeAndDate(storeId, serviceType, day)
                            .orElseGet(() -> {
                                PostalServiceDailyStatistics d = new PostalServiceDailyStatistics();
                                d.setStore(statistics.getStore());
                                d.setPostalServiceType(serviceType);
                                d.setDate(day);
                                return d;
                            });
                    psDaily.setSent(psDaily.getSent() + 1);
                    psDaily.setUpdatedAt(ZonedDateTime.now(ZoneOffset.UTC));
                    postalServiceDailyStatisticsRepository.save(psDaily);
                }
            }
        }

        // Декрементируем статистику старого магазина
        if (storeChanged) {
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

        // Обновляем историю доставки
        deliveryHistoryService.updateDeliveryHistory(trackParcel, oldStatus, newStatus, infoListDTO);
    }
}
