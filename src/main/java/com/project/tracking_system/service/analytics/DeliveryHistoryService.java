package com.project.tracking_system.service.analytics;

import com.project.tracking_system.dto.DeliveryDates;
import com.project.tracking_system.dto.TrackInfoDTO;
import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.entity.*;
import com.project.tracking_system.repository.DeliveryHistoryRepository;
import com.project.tracking_system.repository.StoreAnalyticsRepository;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.repository.PostalServiceStatisticsRepository;
import com.project.tracking_system.repository.StoreDailyStatisticsRepository;
import com.project.tracking_system.repository.PostalServiceDailyStatisticsRepository;
import com.project.tracking_system.service.track.StatusTrackService;
import com.project.tracking_system.service.track.TypeDefinitionTrackPostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.Duration;
import com.project.tracking_system.utils.DateParserUtils;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * @author Dmitriy Anisimov
 * @date 15.03.2025
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class DeliveryHistoryService {

    private final StoreAnalyticsRepository storeAnalyticsRepository;
    private final DeliveryHistoryRepository deliveryHistoryRepository;
    private final TypeDefinitionTrackPostService typeDefinitionTrackPostService;
    private final StatusTrackService statusTrackService;
    private final TrackParcelRepository trackParcelRepository;
    private final PostalServiceStatisticsRepository postalServiceStatisticsRepository;
    private final StoreDailyStatisticsRepository storeDailyStatisticsRepository;
    private final PostalServiceDailyStatisticsRepository postalServiceDailyStatisticsRepository;

    /**
     * Обновляет или создаёт запись {@link DeliveryHistory}, когда меняется статус посылки.
     * <p>
     * Полная история отслеживания из {@link TrackInfoListDTO} анализируется для извлечения
     * всех значимых дат (отправки, прибытия, получения, возврата).
     * Если новый статус считается финальным, метод передаёт управление в
     * {@link #registerFinalStatus(DeliveryHistory, GlobalStatus)}, чтобы обновить
     * накопительную статистику.
     * </p>
     *
     * @param trackParcel       посылка, у которой изменился статус
     * @param oldStatus         предыдущий статус посылки
     * @param newStatus         новый статус посылки
     * @param trackInfoListDTO  список событий трекинга, полученных от службы отслеживания
     */
    @Transactional
    public void updateDeliveryHistory(TrackParcel trackParcel, GlobalStatus oldStatus, GlobalStatus newStatus, TrackInfoListDTO trackInfoListDTO) {
        // Получаем историю или создаём новую
        DeliveryHistory history = deliveryHistoryRepository.findByTrackParcelId(trackParcel.getId())
                .orElseGet(() -> {
                    log.info("Создаём новую запись истории для трека {}", trackParcel.getNumber());

                    // Определяем почтовую службу
                    PostalServiceType serviceType = typeDefinitionTrackPostService.detectPostalService(trackParcel.getNumber());
                    return new DeliveryHistory(trackParcel, trackParcel.getStore(), serviceType, null, null, null);
                });

        //  Если статус НЕ изменился — ничего не делаем
        if (oldStatus == null || !newStatus.equals(oldStatus)) {
            log.info("Новый трек или статус изменился, обновляем историю...");
        } else {
            log.debug("Статус не изменился, обновление истории не требуется для {}", trackParcel.getNumber());
            return;
        }

        //  Определяем часовой пояс пользователя и извлекаем даты из трека
        ZoneId userZone = ZoneId.of(trackParcel.getUser().getTimeZone());
        DeliveryDates deliveryDates = extractDatesFromTrackInfo(trackParcel, trackInfoListDTO, userZone);

        // Устанавливаем дату отправки, если она доступна
        setHistoryDate("Дата отправки", history.getSendDate(), deliveryDates.sendDate(), history::setSendDate);

        if (newStatus == GlobalStatus.DELIVERED) {
            setHistoryDate("Дата получения", history.getReceivedDate(), deliveryDates.receivedDate(), history::setReceivedDate);
        }

        if (newStatus == GlobalStatus.RETURNED) {
            setHistoryDate("Дата возврата", history.getReturnedDate(), deliveryDates.returnedDate(), history::setReturnedDate);
        }

        if (history.getArrivedDate() == null && deliveryDates.arrivedDate() != null) {
            // Фиксируем дату прибытия на пункт выдачи, даже если текущий статус уже финальный
            setHistoryDate(
                    "Дата прибытия на пункт выдачи",
                    history.getArrivedDate(),
                    deliveryDates.arrivedDate(),
                    history::setArrivedDate
            );
        }

        // Считаем и обновляем среднее время доставки
        if (newStatus.isFinal()) {
            registerFinalStatus(history, newStatus);
        }

        // Сохраняем историю, если что-то изменилось
        deliveryHistoryRepository.save(history);
        log.info("История доставки обновлена: {}", trackParcel.getNumber());
    }

    /**
     * Извлекает ключевые даты из списка статусов трекинга.
     *
     * @param trackParcel    посылка, для которой анализируем историю
     * @param trackInfoListDTO список событий трекинга
     * @param userZone       часовой пояс пользователя
     * @return набор извлечённых дат
     */
    private DeliveryDates extractDatesFromTrackInfo(TrackParcel trackParcel, TrackInfoListDTO trackInfoListDTO, ZoneId userZone) {
        List<TrackInfoDTO> trackInfoList = trackInfoListDTO.getList();

        if (trackInfoList.isEmpty()) {
            log.warn("⚠ Пустой список статусов для трека {}", trackParcel.getNumber());
            return new DeliveryDates(null, null, null);
        }

        PostalServiceType serviceType = typeDefinitionTrackPostService.detectPostalService(trackParcel.getNumber());
        ZonedDateTime sendDate = null, receivedDate = null, returnedDate = null, arrivedDate = null;

        //  Определяем дату отправки
        if (serviceType == PostalServiceType.BELPOST) {
            sendDate = DateParserUtils.parse(trackInfoList.get(trackInfoList.size() - 1).getTimex(), userZone); // Последний статус
        } else if (serviceType == PostalServiceType.EVROPOST && trackInfoList.size() > 1) {
            sendDate = DateParserUtils.parse(trackInfoList.get(trackInfoList.size() - 2).getTimex(), userZone); // Предпоследний статус
        } else {
            log.info("Европочта: Недостаточно данных для даты отправки. Трек: {}", trackParcel.getNumber());
        }

        // Определяем дату получения или возврата
        TrackInfoDTO latestStatus = trackInfoList.get(0);
        GlobalStatus finalStatus = statusTrackService.setStatus(List.of(latestStatus));

        if (finalStatus == GlobalStatus.DELIVERED) {
            receivedDate = DateParserUtils.parse(latestStatus.getTimex(), userZone);
        } else if (finalStatus == GlobalStatus.RETURNED) {
            returnedDate = DateParserUtils.parse(latestStatus.getTimex(), userZone);
        }

        // Поиск первого (по времени) статуса WAITING_FOR_CUSTOMER
        for (int i = trackInfoList.size() - 1; i >= 0; i--) {
            TrackInfoDTO info = trackInfoList.get(i);
            GlobalStatus status = statusTrackService.setStatus(List.of(info));
            if (status == GlobalStatus.WAITING_FOR_CUSTOMER) {
                arrivedDate = DateParserUtils.parse(info.getTimex(), userZone);
                log.info("Извлечена дата прибытия на пункт выдачи: {}", arrivedDate);
                break;
            }
        }

        return new DeliveryDates(sendDate, receivedDate, returnedDate, arrivedDate);
    }

    /**
     * Обрабатывает финальный статус доставки (DELIVERED или RETURNED) и обновляет накопительную статистику магазина.
     *
     * <p>Метод выполняет инкремент счётчиков доставленных или возвращённых посылок, а также
     * рассчитывает и накапливает общее время доставки и забора. Выполняется только один раз
     * для каждой посылки, после чего флаг {@code includedInStatistics} устанавливается в {@code true}.</p>
     *
     * Условия для учёта:
     * - Статус должен быть финальным (DELIVERED или RETURNED)
     * - Все необходимые даты (отправки, получения или возврата) должны быть заполнены
     * - Посылка не должна быть уже учтена в статистике
     *
     * @param history история доставки, содержащая даты и связанные данные
     * @param status  новый статус, достигнутый посылкой
     */
    @Transactional
    public void registerFinalStatus(DeliveryHistory history, GlobalStatus status) {
        TrackParcel trackParcel = history.getTrackParcel();

        // Skip analytics update for UNKNOWN postal service
        if (history.getPostalService() == PostalServiceType.UNKNOWN) {
            log.warn("⛔ Skipping analytics update for UNKNOWN service: {}", trackParcel.getNumber());
            return;
        }

        if(trackParcel.isIncludedInStatistics()){
            log.debug("📦 Посылка {} уже учтена в статистике — пропускаем", trackParcel.getNumber());
            return;
        }

        Store store = history.getStore();
        StoreStatistics stats = storeAnalyticsRepository.findByStoreId(store.getId())
                .orElseThrow(() -> new IllegalStateException("Статистика не найдена"));
        PostalServiceStatistics psStats = getOrCreateServiceStats(store, history.getPostalService());

        BigDecimal deliveryDays = null;
        BigDecimal pickupDays = null;
        LocalDate eventDate = null;

        if (status == GlobalStatus.DELIVERED) {
            stats.setTotalDelivered(stats.getTotalDelivered() + 1);
            psStats.setTotalDelivered(psStats.getTotalDelivered() + 1);

            if (history.getReceivedDate() != null) {
                // День получения используется как ключ для ежедневной статистики
                eventDate = history.getReceivedDate().toLocalDate();
            }

            if (history.getSendDate() != null && history.getArrivedDate() != null) {
                // Считаем время доставки от отправки до прибытия
                deliveryDays = BigDecimal.valueOf(
                        Duration.between(history.getSendDate(), history.getArrivedDate()).toHours() / 24.0);
                stats.setSumDeliveryDays(stats.getSumDeliveryDays().add(deliveryDays));
                psStats.setSumDeliveryDays(psStats.getSumDeliveryDays().add(deliveryDays));
            }

            if (history.getArrivedDate() != null && history.getReceivedDate() != null) {
                // Время ожидания клиента на пункте выдачи
                pickupDays = BigDecimal.valueOf(
                        Duration.between(history.getArrivedDate(), history.getReceivedDate()).toDays());
                stats.setSumPickupDays(stats.getSumPickupDays().add(pickupDays));
                psStats.setSumPickupDays(psStats.getSumPickupDays().add(pickupDays));
            }

        } else if (status == GlobalStatus.RETURNED) {
            stats.setTotalReturned(stats.getTotalReturned() + 1);
            psStats.setTotalReturned(psStats.getTotalReturned() + 1);
            if (history.getReturnedDate() != null) {
                eventDate = history.getReturnedDate().toLocalDate();
            }

            if (history.getSendDate() != null && history.getArrivedDate() != null) {
                // Считаем время доставки от отправки до прибытия
                deliveryDays = BigDecimal.valueOf(
                        Duration.between(history.getSendDate(), history.getArrivedDate()).toHours() / 24.0);
                stats.setSumDeliveryDays(stats.getSumDeliveryDays().add(deliveryDays));
                psStats.setSumDeliveryDays(psStats.getSumDeliveryDays().add(deliveryDays));
            }

            if (history.getArrivedDate() != null && history.getReturnedDate() != null) {
                // Возврат забран: считаем время от прибытия до возврата
                pickupDays = BigDecimal.valueOf(
                        Duration.between(history.getArrivedDate(), history.getReturnedDate()).toDays());
                stats.setSumPickupDays(stats.getSumPickupDays().add(pickupDays));
                psStats.setSumPickupDays(psStats.getSumPickupDays().add(pickupDays));
            }
        }

        if (eventDate != null) {
            updateDailyStats(store, history.getPostalService(), eventDate, status, deliveryDays, pickupDays);
        }

        stats.setUpdatedAt(ZonedDateTime.now());
        psStats.setUpdatedAt(ZonedDateTime.now());
        storeAnalyticsRepository.save(stats);
        postalServiceStatisticsRepository.save(psStats);

        trackParcel.setIncludedInStatistics(true);
        trackParcelRepository.save(trackParcel);

        log.info("📊 Обновлена накопительная статистика по магазину: {}", store.getName());
    }

    /**
     * Обновляет ежедневную статистику как для магазина, так и для почтовой службы.
     *
     * @param store        магазин, для которого ведётся статистика
     * @param serviceType  тип почтовой службы
     * @param eventDate    дата события доставки
     * @param status       финальный статус посылки
     * @param deliveryDays время доставки в днях
     * @param pickupDays   время выдачи посылки в днях
     */
    private void updateDailyStats(Store store,
                                  PostalServiceType serviceType,
                                  LocalDate eventDate,
                                  GlobalStatus status,
                                  BigDecimal deliveryDays,
                                  BigDecimal pickupDays) {
        // Skip updates for UNKNOWN postal service
        if (serviceType == PostalServiceType.UNKNOWN) {
            log.warn("⛔ Skipping daily stats update for UNKNOWN service: {}", store.getId());
            return;
        }
        // Поиск или создание ежедневной статистики по магазину
        StoreDailyStatistics daily = storeDailyStatisticsRepository
                .findByStoreIdAndDate(store.getId(), eventDate)
                .orElseGet(() -> {
                    StoreDailyStatistics d = new StoreDailyStatistics();
                    d.setStore(store);
                    d.setDate(eventDate);
                    return d;
                });

        // Поиск или создание ежедневной статистики почтовой службы
        PostalServiceDailyStatistics psDaily = postalServiceDailyStatisticsRepository
                .findByStoreIdAndPostalServiceTypeAndDate(store.getId(), serviceType, eventDate)
                .orElseGet(() -> {
                    PostalServiceDailyStatistics d = new PostalServiceDailyStatistics();
                    d.setStore(store);
                    d.setPostalServiceType(serviceType);
                    d.setDate(eventDate);
                    return d;
                });

        // Обновляем значения в зависимости от финального статуса
        if (status == GlobalStatus.DELIVERED) {
            daily.setDelivered(daily.getDelivered() + 1);
            psDaily.setDelivered(psDaily.getDelivered() + 1);

            // Добавляем время доставки, если оно рассчитано
            if (deliveryDays != null) {
                daily.setSumDeliveryDays(daily.getSumDeliveryDays().add(deliveryDays));
                psDaily.setSumDeliveryDays(psDaily.getSumDeliveryDays().add(deliveryDays));
            }

            // Добавляем время ожидания на пункте, если оно известно
            if (pickupDays != null) {
                daily.setSumPickupDays(daily.getSumPickupDays().add(pickupDays));
                psDaily.setSumPickupDays(psDaily.getSumPickupDays().add(pickupDays));
            }
        } else if (status == GlobalStatus.RETURNED) {
            daily.setReturned(daily.getReturned() + 1);
            psDaily.setReturned(psDaily.getReturned() + 1);

            // Время нахождения посылки на пункте выдачи перед возвратом
            if (pickupDays != null) {
                daily.setSumPickupDays(daily.getSumPickupDays().add(pickupDays));
                psDaily.setSumPickupDays(psDaily.getSumPickupDays().add(pickupDays));
            }
        }

        daily.setUpdatedAt(ZonedDateTime.now());
        psDaily.setUpdatedAt(ZonedDateTime.now());
        storeDailyStatisticsRepository.save(daily);
        postalServiceDailyStatisticsRepository.save(psDaily);
    }

    private PostalServiceStatistics getOrCreateServiceStats(Store store, PostalServiceType serviceType) {
        return postalServiceStatisticsRepository
                .findByStoreIdAndPostalServiceType(store.getId(), serviceType)
                .orElseGet(() -> {
                    PostalServiceStatistics stats = new PostalServiceStatistics();
                    stats.setStore(store);
                    stats.setPostalServiceType(serviceType);
                    return stats;
                });
    }

    /**
     * Обрабатывает удаление посылки и корректирует статистику, если посылка ещё не была учтена.
     *
     * <p>Если посылка не имела финального статуса и ещё не была включена в расчёты,
     * метод уменьшает значение {@code totalSent} в {@code StoreStatistics} на 1.</p>
     *
     * Это позволяет избежать искажения статистики при удалении черновиков и неактуальных треков.
     *
     * @param parcel объект удаляемой посылки
     */
    @Transactional
    public void handleTrackParcelBeforeDelete(TrackParcel parcel) {
        if (parcel.isIncludedInStatistics()) {
            log.debug("Удаляется уже учтённая в статистике посылка {}, статистику не трогаем", parcel.getNumber());
            return;
        }

        Store store = parcel.getStore();
        StoreStatistics stats = storeAnalyticsRepository.findByStoreId(store.getId())
                .orElseThrow(() -> new IllegalStateException("❌ Статистика для магазина не найдена"));
        PostalServiceType serviceType = parcel.getDeliveryHistory() != null
                ? parcel.getDeliveryHistory().getPostalService()
                : typeDefinitionTrackPostService.detectPostalService(parcel.getNumber());
        PostalServiceStatistics psStats = null;
        boolean updatePostalStats = serviceType != PostalServiceType.UNKNOWN;
        if (updatePostalStats) {
            psStats = postalServiceStatisticsRepository
                    .findByStoreIdAndPostalServiceType(store.getId(), serviceType)
                    .orElse(null);
        }

        if (stats.getTotalSent() > 0) {
            stats.setTotalSent(stats.getTotalSent() - 1);
            stats.setUpdatedAt(ZonedDateTime.now());
            storeAnalyticsRepository.save(stats);
            log.info("➖ Уменьшили totalSent после удаления неучтённой посылки: {}", parcel.getNumber());
        } else {
            log.warn("Попытка уменьшить totalSent, но он уже 0. Посылка: {}", parcel.getNumber());
        }

        if (updatePostalStats && psStats != null && psStats.getTotalSent() > 0) {
            psStats.setTotalSent(psStats.getTotalSent() - 1);
            psStats.setUpdatedAt(ZonedDateTime.now());
            postalServiceStatisticsRepository.save(psStats);
        }

        LocalDate day = parcel.getData() != null ? parcel.getData().toLocalDate() : null;
        if (day != null) {
            StoreDailyStatistics daily = storeDailyStatisticsRepository
                    .findByStoreIdAndDate(store.getId(), day)
                    .orElse(null);
            if (daily != null && daily.getSent() > 0) {
                daily.setSent(daily.getSent() - 1);
                daily.setUpdatedAt(ZonedDateTime.now());
                storeDailyStatisticsRepository.save(daily);
            }

            if (updatePostalStats) {
                PostalServiceDailyStatistics psDaily = postalServiceDailyStatisticsRepository
                        .findByStoreIdAndPostalServiceTypeAndDate(store.getId(), serviceType, day)
                        .orElse(null);
                if (psDaily != null && psDaily.getSent() > 0) {
                    psDaily.setSent(psDaily.getSent() - 1);
                    psDaily.setUpdatedAt(ZonedDateTime.now());
                    postalServiceDailyStatisticsRepository.save(psDaily);
                }
            }
        }
    }


    /**
     * Устанавливает дату в истории, если она изменилась.
     */
    private void setHistoryDate(String logMessage, ZonedDateTime oldDate, ZonedDateTime newDate, Consumer<ZonedDateTime> setter) {
        if (newDate != null && !Objects.equals(oldDate, newDate)) {
            log.info("{}: {}", logMessage, newDate);
            setter.accept(newDate);
        }
    }


}