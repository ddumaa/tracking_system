package com.project.tracking_system.service.analytics;

import com.project.tracking_system.dto.DeliveryDates;
import com.project.tracking_system.dto.PostalServiceStatsDTO;
import com.project.tracking_system.dto.TrackInfoDTO;
import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.entity.*;
import com.project.tracking_system.repository.DeliveryHistoryRepository;
import com.project.tracking_system.repository.StoreAnalyticsRepository;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.repository.PostalServiceStatisticsRepository;
import com.project.tracking_system.service.track.StatusTrackService;
import com.project.tracking_system.service.track.TypeDefinitionTrackPostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
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

    /**
     * Обновляет данные в истории доставки при изменении статуса посылки.
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

        //  Извлекаем даты из трека
        DeliveryDates deliveryDates = extractDatesFromTrackInfo(trackParcel, trackInfoListDTO);

        // Устанавливаем дату отправки, если она доступна
        setHistoryDate("Дата отправки", history.getSendDate(), deliveryDates.sendDate(), history::setSendDate);

        if (newStatus == GlobalStatus.DELIVERED) {
            setHistoryDate("Дата получения", history.getReceivedDate(), deliveryDates.receivedDate(), history::setReceivedDate);
        }

        if (newStatus == GlobalStatus.RETURNED) {
            setHistoryDate("Дата возврата", history.getReturnedDate(), deliveryDates.returnedDate(), history::setReturnedDate);
        }

        if (newStatus == GlobalStatus.WAITING_FOR_CUSTOMER) {
            setHistoryDate(
                    "Дата прибытия на пункт выдачи", history.getArrivedDate(), deliveryDates.arrivedDate(), history::setArrivedDate
            );
        }

        // Считаем и обновляем среднее время доставки
        if (newStatus == GlobalStatus.DELIVERED || newStatus == GlobalStatus.RETURNED) {
            registerFinalStatus(history, newStatus);
        }

        // Сохраняем историю, если что-то изменилось
        deliveryHistoryRepository.save(history);
        log.info("История доставки обновлена: {}", trackParcel.getNumber());
    }

    /**
     * Извлекает даты отправки и получения из списка статусов.
     */
    private DeliveryDates extractDatesFromTrackInfo(TrackParcel trackParcel, TrackInfoListDTO trackInfoListDTO) {
        List<TrackInfoDTO> trackInfoList = trackInfoListDTO.getList();

        if (trackInfoList.isEmpty()) {
            log.warn("⚠ Пустой список статусов для трека {}", trackParcel.getNumber());
            return new DeliveryDates(null, null, null);
        }

        PostalServiceType serviceType = typeDefinitionTrackPostService.detectPostalService(trackParcel.getNumber());
        ZonedDateTime sendDate = null, receivedDate = null, returnedDate = null, arrivedDate = null;

        //  Определяем дату отправки
        if (serviceType == PostalServiceType.BELPOST) {
            sendDate = parseDate(trackInfoList.get(trackInfoList.size() - 1).getTimex()); // Последний статус
        } else if (serviceType == PostalServiceType.EVROPOST && trackInfoList.size() > 1) {
            sendDate = parseDate(trackInfoList.get(trackInfoList.size() - 2).getTimex()); // Предпоследний статус
        } else {
            log.info("Европочта: Недостаточно данных для даты отправки. Трек: {}", trackParcel.getNumber());
        }

        // Определяем дату получения или возврата
        TrackInfoDTO latestStatus = trackInfoList.get(0);
        GlobalStatus finalStatus = statusTrackService.setStatus(List.of(latestStatus));

        if (finalStatus == GlobalStatus.DELIVERED) {
            receivedDate = parseDate(latestStatus.getTimex());
        } else if (finalStatus == GlobalStatus.RETURNED) {
            returnedDate = parseDate(latestStatus.getTimex());
        }

        // Поиск статуса WAITING_FOR_CUSTOMER
        for (TrackInfoDTO info : trackInfoList) {
            GlobalStatus status = statusTrackService.setStatus(List.of(info));
            if (status == GlobalStatus.WAITING_FOR_CUSTOMER) {
                arrivedDate = parseDate(info.getTimex());
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

        if(trackParcel.isIncludedInStatistics()){
            log.debug("📦 Посылка {} уже учтена в статистике — пропускаем", trackParcel.getNumber());
            return;
        }

        Store store = history.getStore();
        StoreStatistics stats = storeAnalyticsRepository.findByStoreId(store.getId())
                .orElseThrow(() -> new IllegalStateException("Статистика не найдена"));
        PostalServiceStatistics psStats = getOrCreateServiceStats(store, history.getPostalService());

        if (status == GlobalStatus.DELIVERED && history.getSendDate() != null && history.getReceivedDate() != null) {
            long deliveryDays = ChronoUnit.HOURS.between(history.getSendDate(), history.getReceivedDate()) / 24;
            stats.setTotalDelivered(stats.getTotalDelivered() + 1);
            stats.setSumDeliveryDays(stats.getSumDeliveryDays().add(BigDecimal.valueOf(deliveryDays)));
            psStats.setTotalDelivered(psStats.getTotalDelivered() + 1);
            psStats.setSumDeliveryDays(psStats.getSumDeliveryDays().add(BigDecimal.valueOf(deliveryDays)));

            if (history.getArrivedDate() != null) {
                long pickupDays = ChronoUnit.HOURS.between(history.getArrivedDate(), history.getReceivedDate()) / 24;
                stats.setSumPickupDays(stats.getSumPickupDays().add(BigDecimal.valueOf(pickupDays)));
                psStats.setSumPickupDays(psStats.getSumPickupDays().add(BigDecimal.valueOf(pickupDays)));
            }

        } else if (status == GlobalStatus.RETURNED && history.getArrivedDate() != null && history.getReturnedDate() != null) {
            long pickupDays = ChronoUnit.HOURS.between(history.getArrivedDate(), history.getReturnedDate()) / 24;
            stats.setTotalReturned(stats.getTotalReturned() + 1);
            stats.setSumPickupDays(stats.getSumPickupDays().add(BigDecimal.valueOf(pickupDays)));
            psStats.setTotalReturned(psStats.getTotalReturned() + 1);
            psStats.setSumPickupDays(psStats.getSumPickupDays().add(BigDecimal.valueOf(pickupDays)));
        }

        stats.setUpdatedAt(ZonedDateTime.now());
        psStats.setUpdatedAt(ZonedDateTime.now());
        storeAnalyticsRepository.save(stats);
        postalServiceStatisticsRepository.save(psStats);

        trackParcel.setIncludedInStatistics(true);
        trackParcelRepository.save(trackParcel);

        log.info("📊 Обновлена накопительная статистика по магазину: {}", store.getName());
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
        PostalServiceStatistics psStats = postalServiceStatisticsRepository
                .findByStoreIdAndPostalServiceType(store.getId(), serviceType)
                .orElse(null);

        if (stats.getTotalSent() > 0) {
            stats.setTotalSent(stats.getTotalSent() - 1);
            stats.setUpdatedAt(ZonedDateTime.now());
            storeAnalyticsRepository.save(stats);
            log.info("➖ Уменьшили totalSent после удаления неучтённой посылки: {}", parcel.getNumber());
        } else {
            log.warn("Попытка уменьшить totalSent, но он уже 0. Посылка: {}", parcel.getNumber());
        }

        if (psStats != null && psStats.getTotalSent() > 0) {
            psStats.setTotalSent(psStats.getTotalSent() - 1);
            psStats.setUpdatedAt(ZonedDateTime.now());
            postalServiceStatisticsRepository.save(psStats);
        }
    }

    /**
     * Парсит строковую дату в `ZonedDateTime`
     */
    private ZonedDateTime parseDate(String dateString) {
        if (dateString == null || dateString.isEmpty()) {
            return null;
        }
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
            LocalDateTime localDateTime = LocalDateTime.parse(dateString, formatter);

            // Заменить Europe/Minsk на userZone, когда будет передаваться из контекста - в будущем
            ZoneId inputZone = ZoneId.of("Europe/Minsk");
            return localDateTime.atZone(inputZone).withZoneSameInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            log.error("Ошибка парсинга даты: {}", dateString, e);
            return null;
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