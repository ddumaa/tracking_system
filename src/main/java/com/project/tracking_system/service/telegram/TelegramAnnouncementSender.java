package com.project.tracking_system.service.telegram;

/**
 * Контракт для компонентов, способных отрисовать активное объявление администратора в чате Telegram.
 */
public interface TelegramAnnouncementSender {

    /**
     * Показать актуальное административное объявление подтверждённому покупателю.
     *
     * @param chatId идентификатор чата Telegram, в который необходимо отправить баннер
     */
    void showActiveAnnouncement(Long chatId);
}
