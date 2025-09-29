package com.project.tracking_system.service.telegram;

import com.project.tracking_system.repository.CustomerRepository;
import com.project.tracking_system.service.admin.AdminNotificationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Компонент, выполняющий широковещательную рассылку активного объявления в Telegram.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TelegramAnnouncementBroadcaster {

    private final AdminNotificationService adminNotificationService;
    private final CustomerRepository customerRepository;
    private final TelegramAnnouncementSender announcementSender;

    /**
     * Отправить активное объявление всем подтверждённым подписчикам Telegram.
     * <p>
     * Метод запрашивает актуальный баннер у сервиса административных уведомлений и,
     * только если объявление существует, перебирает идентификаторы подтверждённых чатов.
     * Для каждого адресата вызывается публичный метод бота, повторно использующий
     * логику отрисовки баннера без загрузки лишних данных о покупателе.
     * </p>
     *
     * @param notificationId идентификатор уведомления, инициировавшего рассылку
     */
    public void broadcastActiveAnnouncement(Long notificationId) {
        adminNotificationService.findActiveNotification().ifPresentOrElse(activeNotification -> {
            Long activeId = activeNotification.getId();
            List<Long> chatIds = customerRepository.findConfirmedTelegramChatIds();
            if (chatIds.isEmpty()) {
                log.debug("Нет подтверждённых чатов для рассылки объявления {}", activeId);
                return;
            }
            chatIds.forEach(chatId -> {
                if (chatId == null) {
                    return;
                }
                announcementSender.showActiveAnnouncement(chatId);
            });
        }, () -> log.warn("Рассылка объявления {} отменена: активное уведомление отсутствует", notificationId));
    }
}
