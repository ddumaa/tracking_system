package com.project.tracking_system.service.admin.event;

/**
 * Событие изменения активного административного объявления.
 * <p>
 * Публикуется сервисом уведомлений при активации или принудительном сбросе баннера,
 * чтобы связанные компоненты могли синхронно обновить пользователей Telegram.
 * </p>
 *
 * @param notificationId идентификатор уведомления, вызвавшего событие
 * @param type           тип изменения (активация или сброс)
 */
public record AdminAnnouncementEvent(Long notificationId, AdminAnnouncementEventType type) {

    /**
     * Тип события для обработки широковещательной рассылки объявлений.
     */
    public enum AdminAnnouncementEventType {
        /** Уведомление переведено в активное состояние. */
        ACTIVATED,
        /** Администратор запросил принудительный сброс просмотров. */
        RESET_REQUESTED
    }
}
