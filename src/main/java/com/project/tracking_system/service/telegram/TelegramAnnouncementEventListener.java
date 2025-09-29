package com.project.tracking_system.service.telegram;

import com.project.tracking_system.service.admin.event.AdminAnnouncementEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Обработчик доменных событий административных объявлений для запуска рассылки в Telegram.
 */
@Component
@RequiredArgsConstructor
public class TelegramAnnouncementEventListener {

    private final TelegramAnnouncementBroadcaster announcementBroadcaster;

    /**
     * Реагирует на подтверждённую активацию или сброс объявления после фиксации транзакции.
     *
     * @param event событие изменения административного объявления
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAdminAnnouncement(AdminAnnouncementEvent event) {
        if (event == null) {
            return;
        }
        announcementBroadcaster.broadcastActiveAnnouncement(event.notificationId());
    }
}
