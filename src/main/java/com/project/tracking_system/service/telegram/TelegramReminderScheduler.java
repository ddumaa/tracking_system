package com.project.tracking_system.service.telegram;

import com.project.tracking_system.entity.CustomerNotificationLog;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.NotificationType;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.repository.CustomerNotificationLogRepository;
import com.project.tracking_system.repository.TrackParcelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
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
    @Scheduled(cron = "0 0 8 * * *", zone = "UTC")
    @Transactional
    public void sendReminders() {
        ZonedDateTime threshold = ZonedDateTime.now(ZoneOffset.UTC).minusDays(3);
        List<TrackParcel> parcels = trackParcelRepository
                .findWaitingForPickupBefore(GlobalStatus.WAITING_FOR_CUSTOMER, threshold);

        for (TrackParcel parcel : parcels) {
            boolean exists = customerNotificationLogRepository
                    .existsByParcelIdAndStatusAndNotificationType(
                            parcel.getId(),
                            GlobalStatus.WAITING_FOR_CUSTOMER,
                            NotificationType.REMINDER
                    );
            if (exists) {
                log.debug("⏭ Напоминание уже отправлено для трека {}", parcel.getNumber());
                continue;
            }

            telegramNotificationService.sendReminder(parcel);
            log.info("📨 Напоминание отправлено для трека {}", parcel.getNumber());

            CustomerNotificationLog logEntry = new CustomerNotificationLog();
            logEntry.setCustomer(parcel.getCustomer());
            logEntry.setParcel(parcel);
            logEntry.setStatus(GlobalStatus.WAITING_FOR_CUSTOMER);
            logEntry.setNotificationType(NotificationType.REMINDER);
            logEntry.setSentAt(ZonedDateTime.now(ZoneOffset.UTC));
            customerNotificationLogRepository.save(logEntry);
        }
    }
}
