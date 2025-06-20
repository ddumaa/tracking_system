package com.project.tracking_system.service.telegram;

import com.project.tracking_system.entity.CustomerNotificationLog;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.NotificationType;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.StoreTelegramSettings;
import com.project.tracking_system.repository.CustomerNotificationLogRepository;
import com.project.tracking_system.repository.TrackParcelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Планировщик напоминаний о невыкупленных посылках.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramReminderScheduler {

    private final TrackParcelRepository trackParcelRepository;
    private final CustomerNotificationLogRepository customerNotificationLogRepository;
    private final TelegramNotificationService telegramNotificationService;

    /**
     * Ежедневно отправляет напоминания покупателям.
     * <p>
     * Находит посылки в статусе {@link GlobalStatus#WAITING_FOR_CUSTOMER},
     * прибывшие на пункт выдачи более трёх дней назад, и отправляет
     * напоминание через Telegram.
     * </p>
     */
    @Transactional
    public void sendReminders() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime threshold = now.minusDays(1);
        List<TrackParcel> parcels = trackParcelRepository
                .findWaitingForPickupBefore(GlobalStatus.WAITING_FOR_CUSTOMER, threshold);

        for (TrackParcel parcel : parcels) {
            StoreTelegramSettings settings = parcel.getStore().getTelegramSettings();
            if (settings == null || !settings.isEnabled() || !settings.isRemindersEnabled()) {
                continue;
            }

            ZonedDateTime arrived = parcel.getDeliveryHistory() != null ? parcel.getDeliveryHistory().getArrivedDate() : null;
            if (arrived == null || arrived.plusDays(settings.getReminderStartAfterDays()).isAfter(now)) {
                continue;
            }

            CustomerNotificationLog last = customerNotificationLogRepository
                    .findTopByParcelIdAndNotificationTypeOrderBySentAtDesc(parcel.getId(), NotificationType.REMINDER);
            if (last != null && last.getSentAt().plusDays(settings.getReminderRepeatIntervalDays()).isAfter(now)) {
                continue;
            }

            telegramNotificationService.sendReminder(parcel);
            log.info("📨 Напоминание отправлено для трека {}", parcel.getNumber());

            CustomerNotificationLog logEntry = new CustomerNotificationLog();
            logEntry.setCustomer(parcel.getCustomer());
            logEntry.setParcel(parcel);
            logEntry.setStatus(GlobalStatus.WAITING_FOR_CUSTOMER);
            logEntry.setNotificationType(NotificationType.REMINDER);
            logEntry.setSentAt(now);
            customerNotificationLogRepository.save(logEntry);
        }
    }
}
