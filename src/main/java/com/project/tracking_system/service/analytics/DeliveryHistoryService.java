package com.project.tracking_system.service.analytics;

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
import java.util.Optional;
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
        ZonedDateTime sendDate = null, receivedDate = null, returnedDate = null;

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

        return new DeliveryDates(sendDate, receivedDate, returnedDate);
    }

    /**
     * Пересчитывает средний срок доставки для магазина и обновляет в StoreStatistics.
     */
    @Transactional
    public void updateAverageDeliveryDays(Store store) {
        Double avgDays = deliveryHistoryRepository.findAverageDeliveryTimeForStore(store.getId());

        StoreStatistics statistics = storeAnalyticsRepository.findByStoreId(store.getId())
                .orElseThrow(() -> new IllegalStateException("Статистика не найдена для магазина ID=" + store.getId()));

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

    /**
     *  Получает историю доставки по ID посылки.
     */
    @Transactional(readOnly = true)
    public Optional<DeliveryHistory> getHistoryByParcelId(Long parcelId) {
        return deliveryHistoryRepository.findByTrackParcelId(parcelId);
    }

    /**
     *  Класс-обёртка для дат.
     */
    private record DeliveryDates(ZonedDateTime sendDate, ZonedDateTime receivedDate, ZonedDateTime returnedDate) {}
}
