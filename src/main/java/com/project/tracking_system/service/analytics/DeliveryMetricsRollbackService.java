package com.project.tracking_system.service.analytics;

import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.entity.DeliveryHistory;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.PostalServiceDailyStatistics;
import com.project.tracking_system.entity.PostalServiceStatistics;
import com.project.tracking_system.entity.PostalServiceType;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.StoreDailyStatistics;
import com.project.tracking_system.entity.StoreStatistics;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.repository.PostalServiceDailyStatisticsRepository;
import com.project.tracking_system.repository.PostalServiceStatisticsRepository;
import com.project.tracking_system.repository.StoreAnalyticsRepository;
import com.project.tracking_system.repository.StoreDailyStatisticsRepository;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.service.customer.CustomerService;
import com.project.tracking_system.service.customer.CustomerStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Сервис, отвечающий за откат статистических показателей при регрессе финального статуса трека.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryMetricsRollbackService {

    private final StoreAnalyticsRepository storeAnalyticsRepository;
    private final PostalServiceStatisticsRepository postalServiceStatisticsRepository;
    private final StoreDailyStatisticsRepository storeDailyStatisticsRepository;
    private final PostalServiceDailyStatisticsRepository postalServiceDailyStatisticsRepository;
    private final TrackParcelRepository trackParcelRepository;
    private final CustomerService customerService;
    private final CustomerStatsService customerStatsService;

    /**
     * Выполняет полный откат финального статуса, возвращая все связанные показатели к состоянию до учёта.
     * Метод управляет агрегатами магазина и почтовой службы, дневной статистикой и пользовательскими счётчиками.
     */
    public void rollbackFinalStatusMetrics(DeliveryHistory history,
                                           TrackParcel trackParcel,
                                           GlobalStatus previousStatus) {
        if (history == null || trackParcel == null || previousStatus == null || !previousStatus.isFinal()) {
            return;
        }

        boolean wasIncluded = trackParcel.isIncludedInStatistics();
        Store store = history.getStore();
        PostalServiceType serviceType = history.getPostalService();

        BigDecimal deliveryDays = null;
        if (history.getSendDate() != null && history.getArrivedDate() != null) {
            deliveryDays = BigDecimal.valueOf(
                    Duration.between(history.getSendDate(), history.getArrivedDate()).toHours() / 24.0
            );
        }

        BigDecimal pickupDays = null;
        LocalDate eventDate = null;
        if (previousStatus == GlobalStatus.DELIVERED) {
            if (history.getArrivedDate() != null && history.getReceivedDate() != null) {
                pickupDays = BigDecimal.valueOf(
                        Duration.between(history.getArrivedDate(), history.getReceivedDate()).toDays()
                );
            }
            if (history.getReceivedDate() != null) {
                eventDate = history.getReceivedDate().toLocalDate();
            }
        } else if (previousStatus == GlobalStatus.RETURNED) {
            if (history.getReturnedDate() != null) {
                eventDate = history.getReturnedDate().toLocalDate();
            }
        }

        if (wasIncluded && serviceType != PostalServiceType.UNKNOWN) {
            rollbackStoreAndServiceAggregates(store, serviceType, previousStatus, deliveryDays, pickupDays);
            rollbackDailyAggregates(store, serviceType, previousStatus, deliveryDays, pickupDays, eventDate);
        } else if (wasIncluded) {
            log.warn("⚠️ Невозможно откатить статистику для трека {}: неизвестная почтовая служба",
                    trackParcel.getNumber());
        }

        trackParcel.setIncludedInStatistics(false);
        trackParcelRepository.save(trackParcel);

        clearHistoryFinalDates(history);
        rollbackCustomerCounters(trackParcel, previousStatus, wasIncluded);

        log.info("↩️ Финальный статус {} откатан для трека {}", previousStatus, trackParcel.getNumber());
    }

    /**
     * Уменьшает агрегированные показатели магазина и почтовой службы, если трек исключается из финальной статистики.
     */
    private void rollbackStoreAndServiceAggregates(Store store,
                                                   PostalServiceType serviceType,
                                                   GlobalStatus status,
                                                   BigDecimal deliveryDays,
                                                   BigDecimal pickupDays) {
        Long storeId = store.getId();
        BigDecimal deliveryDelta = deliveryDays != null ? deliveryDays.negate() : BigDecimal.ZERO;
        BigDecimal pickupDelta = (status == GlobalStatus.DELIVERED && pickupDays != null)
                ? pickupDays.negate()
                : BigDecimal.ZERO;

        if (status == GlobalStatus.DELIVERED) {
            int storeUpdated = storeAnalyticsRepository.incrementDelivered(storeId, -1, deliveryDelta, pickupDelta);
            if (storeUpdated == 0) {
                Optional<StoreStatistics> statsOpt = storeAnalyticsRepository.findByStoreId(storeId);
                if (statsOpt.isPresent()) {
                    StoreStatistics stats = statsOpt.get();
                    stats.setTotalDelivered(Math.max(0, stats.getTotalDelivered() - 1));
                    if (deliveryDays != null) {
                        stats.setSumDeliveryDays(subtractNonNegative(stats.getSumDeliveryDays(), deliveryDays));
                    }
                    if (pickupDays != null) {
                        stats.setSumPickupDays(subtractNonNegative(stats.getSumPickupDays(), pickupDays));
                    }
                    stats.setUpdatedAt(ZonedDateTime.now());
                    storeAnalyticsRepository.save(stats);
                } else {
                    log.warn("Не найдена статистика магазина {} для отката доставленных посылок", storeId);
                }
            }

            int serviceUpdated = postalServiceStatisticsRepository.incrementDelivered(
                    storeId,
                    serviceType,
                    -1,
                    deliveryDelta,
                    pickupDelta
            );
            if (serviceUpdated == 0) {
                Optional<PostalServiceStatistics> psOpt = postalServiceStatisticsRepository
                        .findByStoreIdAndPostalServiceType(storeId, serviceType);
                if (psOpt.isPresent()) {
                    PostalServiceStatistics stats = psOpt.get();
                    stats.setTotalDelivered(Math.max(0, stats.getTotalDelivered() - 1));
                    if (deliveryDays != null) {
                        stats.setSumDeliveryDays(subtractNonNegative(stats.getSumDeliveryDays(), deliveryDays));
                    }
                    if (pickupDays != null) {
                        stats.setSumPickupDays(subtractNonNegative(stats.getSumPickupDays(), pickupDays));
                    }
                    stats.setUpdatedAt(ZonedDateTime.now());
                    postalServiceStatisticsRepository.save(stats);
                } else {
                    log.warn("Не найдена статистика службы {} магазина {} при откате доставленных посылок",
                            serviceType, storeId);
                }
            }
        } else if (status == GlobalStatus.RETURNED) {
            int storeUpdated = storeAnalyticsRepository.incrementReturned(storeId, -1, deliveryDelta, BigDecimal.ZERO);
            if (storeUpdated == 0) {
                Optional<StoreStatistics> statsOpt = storeAnalyticsRepository.findByStoreId(storeId);
                if (statsOpt.isPresent()) {
                    StoreStatistics stats = statsOpt.get();
                    stats.setTotalReturned(Math.max(0, stats.getTotalReturned() - 1));
                    if (deliveryDays != null) {
                        stats.setSumDeliveryDays(subtractNonNegative(stats.getSumDeliveryDays(), deliveryDays));
                    }
                    stats.setUpdatedAt(ZonedDateTime.now());
                    storeAnalyticsRepository.save(stats);
                } else {
                    log.warn("Не найдена статистика магазина {} для отката возвратов", storeId);
                }
            }

            int serviceUpdated = postalServiceStatisticsRepository.incrementReturned(
                    storeId,
                    serviceType,
                    -1,
                    deliveryDelta,
                    BigDecimal.ZERO
            );
            if (serviceUpdated == 0) {
                Optional<PostalServiceStatistics> psOpt = postalServiceStatisticsRepository
                        .findByStoreIdAndPostalServiceType(storeId, serviceType);
                if (psOpt.isPresent()) {
                    PostalServiceStatistics stats = psOpt.get();
                    stats.setTotalReturned(Math.max(0, stats.getTotalReturned() - 1));
                    if (deliveryDays != null) {
                        stats.setSumDeliveryDays(subtractNonNegative(stats.getSumDeliveryDays(), deliveryDays));
                    }
                    stats.setUpdatedAt(ZonedDateTime.now());
                    postalServiceStatisticsRepository.save(stats);
                } else {
                    log.warn("Не найдена статистика службы {} магазина {} при откате возвратов",
                            serviceType, storeId);
                }
            }
        }
    }

    /**
     * Корректирует ежедневную статистику магазина и почтовой службы, если финальное событие отменено.
     */
    private void rollbackDailyAggregates(Store store,
                                         PostalServiceType serviceType,
                                         GlobalStatus status,
                                         BigDecimal deliveryDays,
                                         BigDecimal pickupDays,
                                         LocalDate eventDate) {
        if (eventDate == null) {
            return;
        }

        Long storeId = store.getId();
        BigDecimal deliveryDelta = deliveryDays != null ? deliveryDays.negate() : BigDecimal.ZERO;
        BigDecimal pickupDelta = (status == GlobalStatus.DELIVERED && pickupDays != null)
                ? pickupDays.negate()
                : BigDecimal.ZERO;

        if (status == GlobalStatus.DELIVERED) {
            int storeDailyUpdated = storeDailyStatisticsRepository.incrementDelivered(
                    storeId,
                    eventDate,
                    -1,
                    deliveryDelta,
                    pickupDelta
            );
            if (storeDailyUpdated == 0) {
                Optional<StoreDailyStatistics> dailyOpt = storeDailyStatisticsRepository
                        .findByStoreIdAndDate(storeId, eventDate);
                if (dailyOpt.isPresent()) {
                    StoreDailyStatistics daily = dailyOpt.get();
                    daily.setDelivered(Math.max(0, daily.getDelivered() - 1));
                    if (deliveryDays != null) {
                        daily.setSumDeliveryDays(subtractNonNegative(daily.getSumDeliveryDays(), deliveryDays));
                    }
                    if (pickupDays != null) {
                        daily.setSumPickupDays(subtractNonNegative(daily.getSumPickupDays(), pickupDays));
                    }
                    daily.setUpdatedAt(ZonedDateTime.now());
                    storeDailyStatisticsRepository.save(daily);
                } else {
                    log.warn("Не найдена ежедневная статистика магазина {} за {} при откате доставленных",
                            storeId, eventDate);
                }
            }

            int psDailyUpdated = postalServiceDailyStatisticsRepository.incrementDelivered(
                    storeId,
                    serviceType,
                    eventDate,
                    -1,
                    deliveryDelta,
                    pickupDelta
            );
            if (psDailyUpdated == 0) {
                Optional<PostalServiceDailyStatistics> psDailyOpt = postalServiceDailyStatisticsRepository
                        .findByStoreIdAndPostalServiceTypeAndDate(storeId, serviceType, eventDate);
                if (psDailyOpt.isPresent()) {
                    PostalServiceDailyStatistics daily = psDailyOpt.get();
                    daily.setDelivered(Math.max(0, daily.getDelivered() - 1));
                    if (deliveryDays != null) {
                        daily.setSumDeliveryDays(subtractNonNegative(daily.getSumDeliveryDays(), deliveryDays));
                    }
                    if (pickupDays != null) {
                        daily.setSumPickupDays(subtractNonNegative(daily.getSumPickupDays(), pickupDays));
                    }
                    daily.setUpdatedAt(Instant.now());
                    postalServiceDailyStatisticsRepository.save(daily);
                } else {
                    log.warn(
                            "Не найдена ежедневная статистика службы {} магазина {} за {} при откате доставленных",
                            serviceType,
                            storeId,
                            eventDate
                    );
                }
            }
        } else if (status == GlobalStatus.RETURNED) {
            int storeDailyUpdated = storeDailyStatisticsRepository.incrementReturned(
                    storeId,
                    eventDate,
                    -1,
                    deliveryDelta,
                    BigDecimal.ZERO
            );
            if (storeDailyUpdated == 0) {
                Optional<StoreDailyStatistics> dailyOpt = storeDailyStatisticsRepository
                        .findByStoreIdAndDate(storeId, eventDate);
                if (dailyOpt.isPresent()) {
                    StoreDailyStatistics daily = dailyOpt.get();
                    daily.setReturned(Math.max(0, daily.getReturned() - 1));
                    if (deliveryDays != null) {
                        daily.setSumDeliveryDays(subtractNonNegative(daily.getSumDeliveryDays(), deliveryDays));
                    }
                    daily.setUpdatedAt(ZonedDateTime.now());
                    storeDailyStatisticsRepository.save(daily);
                } else {
                    log.warn("Не найдена ежедневная статистика магазина {} за {} при откате возвратов",
                            storeId, eventDate);
                }
            }

            int psDailyUpdated = postalServiceDailyStatisticsRepository.incrementReturned(
                    storeId,
                    serviceType,
                    eventDate,
                    -1,
                    deliveryDelta,
                    BigDecimal.ZERO
            );
            if (psDailyUpdated == 0) {
                Optional<PostalServiceDailyStatistics> psDailyOpt = postalServiceDailyStatisticsRepository
                        .findByStoreIdAndPostalServiceTypeAndDate(storeId, serviceType, eventDate);
                if (psDailyOpt.isPresent()) {
                    PostalServiceDailyStatistics daily = psDailyOpt.get();
                    daily.setReturned(Math.max(0, daily.getReturned() - 1));
                    if (deliveryDays != null) {
                        daily.setSumDeliveryDays(subtractNonNegative(daily.getSumDeliveryDays(), deliveryDays));
                    }
                    daily.setUpdatedAt(Instant.now());
                    postalServiceDailyStatisticsRepository.save(daily);
                } else {
                    log.warn("Не найдена ежедневная статистика службы {} магазина {} за {} при откате возвратов",
                            serviceType, storeId, eventDate);
                }
            }
        }
    }

    /**
     * Сбрасывает финальные даты получения и возврата, если посылка больше не находится в конечном статусе.
     */
    private void clearHistoryFinalDates(DeliveryHistory history) {
        history.setReceivedDate(null);
        history.setReturnedDate(null);
    }

    /**
     * Откатывает пользовательские счётчики покупателя, сохраняя корректное количество отправленных посылок.
     */
    private void rollbackCustomerCounters(TrackParcel trackParcel,
                                          GlobalStatus previousStatus,
                                          boolean wasIncludedInStats) {
        if (!wasIncludedInStats || trackParcel == null) {
            return;
        }

        Customer customer = trackParcel.getCustomer();
        if (customer == null) {
            return;
        }

        int sentBefore = customer.getSentCount();

        TrackParcel synthetic = new TrackParcel();
        synthetic.setStatus(previousStatus);
        synthetic.setCustomer(customer);
        customerService.rollbackStatsOnTrackDelete(synthetic);

        if (sentBefore > 0) {
            Customer refreshed = customerStatsService.incrementSent(customer);
            if (refreshed != null) {
                trackParcel.setCustomer(refreshed);
            }
        }
    }

    /**
     * Безопасно уменьшает сумму, гарантируя, что итоговое значение не станет отрицательным.
     */
    private BigDecimal subtractNonNegative(BigDecimal current, BigDecimal decrement) {
        if (current == null || decrement == null) {
            return current;
        }

        BigDecimal result = current.subtract(decrement);
        return result.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : result;
    }
}
