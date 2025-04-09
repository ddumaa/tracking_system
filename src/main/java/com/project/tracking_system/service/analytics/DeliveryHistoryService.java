package com.project.tracking_system.service.analytics;

import com.project.tracking_system.dto.DeliveryDates;
import com.project.tracking_system.dto.PostalServiceStatsDTO;
import com.project.tracking_system.dto.TrackInfoDTO;
import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.entity.*;
import com.project.tracking_system.repository.DeliveryHistoryRepository;
import com.project.tracking_system.repository.StoreAnalyticsRepository;
import com.project.tracking_system.service.track.StatusTrackService;
import com.project.tracking_system.service.track.TypeDefinitionTrackPostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
        updateAverageDeliveryDays(trackParcel.getStore());

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
     * Пересчитывает средний срок доставки для магазина и обновляет в StoreStatistics.
     */
    @Transactional
    public void updateAverageDeliveryDays(Store store) {
        Double avgDays = deliveryHistoryRepository.findAverageDeliveryTimeToFinalPoint(store.getId());
        Double avgPickup = deliveryHistoryRepository.findAvgPickupTimeForStore(store.getId());

        StoreStatistics statistics = storeAnalyticsRepository.findByStoreId(store.getId())
                .orElseThrow(() -> new IllegalStateException("Статистика не найдена для магазина ID=" + store.getId()));

        statistics.setAveragePickupDays(avgPickup);
        statistics.setAverageDeliveryDays(avgDays);

        storeAnalyticsRepository.save(statistics);

        log.info("📦 Среднее время доставки обновлено для {}: {} дней", store.getName(), avgDays);
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

    public List<PostalServiceStatsDTO> getStatsByPostalService(Long storeId) {
        List<Object[]> rawData = deliveryHistoryRepository.getRawStatsByPostalService(storeId);
        return rawData.stream()
                .map(this::mapToDto)
                .toList();
    }

    public List<PostalServiceStatsDTO> getStatsByPostalServiceForStores(List<Long> storeIds) {
        List<Object[]> rawData = deliveryHistoryRepository.getRawStatsByPostalServiceForStores(storeIds);
        return rawData.stream()
                .map(this::mapToDto)
                .toList();
    }

    /**
     * Преобразует массив данных, полученных из запроса к БД,
     * в объект {@link PostalServiceStatsDTO} c локализованным названием почтовой службы.
     *
     * @param row массив полей: [кодСлужбы, отправлено, доставлено, возвращено, средняяДоставка]
     * @return заполненный DTO со строковым именем почтовой службы, числом отправленных, доставленных и возвращённых
     */
    private PostalServiceStatsDTO mapToDto(Object[] row) {
        String code = (String) row[0];
        PostalServiceType type = PostalServiceType.fromCode(code);
        String displayName = type.getDisplayName();

        int sent = row[1] != null ? ((Number) row[1]).intValue() : 0;
        int delivered = row[2] != null ? ((Number) row[2]).intValue() : 0;
        int returned = row[3] != null ? ((Number) row[3]).intValue() : 0;
        double avgDeliveryDays = row[4] != null ? ((Number) row[4]).doubleValue() : 0.0;
        double avgPickupTimeDays = row[5] != null ? ((Number) row[5]).doubleValue() : 0.0;

        return new PostalServiceStatsDTO(
                displayName,
                sent,
                delivered,
                returned,
                avgDeliveryDays,
                avgPickupTimeDays
        );
    }

}