package com.project.tracking_system.service.telegram;

import com.project.tracking_system.entity.BuyerBotScreen;
import com.project.tracking_system.entity.BuyerChatState;

import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Хранилище сценарных состояний Telegram-диалогов с покупателями.
 * <p>
 * Предоставляет абстракцию над конкретной реализацией (БД, Redis и т.п.).
 * </p>
 */
public interface ChatSessionRepository {

    /**
     * Ищет сохранённую сессию по идентификатору чата.
     *
     * @param chatId идентификатор чата Telegram
     * @return найденная сессия или {@link Optional#empty()}, если данных нет
     */
    Optional<ChatSession> find(Long chatId);

    /**
     * Сохраняет или обновляет все данные сессии в хранилище.
     *
     * @param session представление сессии, которое требуется зафиксировать
     * @return сохранённая версия сессии
     */
    ChatSession save(ChatSession session);

    /**
     * Возвращает текущее состояние чата или {@link BuyerChatState#IDLE}, если данные отсутствуют.
     *
     * @param chatId идентификатор чата Telegram
     * @return зафиксированное состояние диалога
     */
    BuyerChatState getState(Long chatId);

    /**
     * Обновляет сценарное состояние чата, создавая запись при необходимости.
     *
     * @param chatId идентификатор чата Telegram
     * @param state  состояние, которое требуется зафиксировать
     */
    void updateState(Long chatId, BuyerChatState state);

    /**
     * Сохраняет идентификатор якорного сообщения без изменения экрана.
     *
     * @param chatId          идентификатор чата Telegram
     * @param anchorMessageId идентификатор сообщения
     */
    void updateAnchor(Long chatId, Integer anchorMessageId);

    /**
     * Сохраняет идентификатор сообщения и экран, которые нужно восстановить после рестарта.
     *
     * @param chatId          идентификатор чата Telegram
     * @param anchorMessageId идентификатор якорного сообщения
     * @param screen          экран, который должен отрисовываться
     */
    void updateAnchorAndScreen(Long chatId, Integer anchorMessageId, BuyerBotScreen screen);

    /**
     * Сбрасывает информацию о якорном сообщении.
     *
     * @param chatId идентификатор чата Telegram
     */
    void clearAnchor(Long chatId);

    /**
     * Помечает текущее якорное сообщение неактивным без изменения статуса клавиатуры.
     * <p>
     * Метод используется при принудительной переотправке экрана, когда необходимо снять
     * клавиатуру со старого сообщения и отправить новое, сохранив при этом информацию о
     * последнем экране.
     * </p>
     *
     * @param chatId идентификатор чата Telegram
     */
    void deactivateAnchor(Long chatId);

    /**
     * Проверяет, скрыта ли постоянная клавиатура меню у пользователя.
     *
     * @param chatId идентификатор чата Telegram
     * @return {@code true}, если клавиатура отсутствует и требуется переотправка
     */
    boolean isKeyboardHidden(Long chatId);

    /**
     * Помечает клавиатуру меню как скрытую пользователем.
     *
     * @param chatId идентификатор чата Telegram
     */
    void markKeyboardHidden(Long chatId);

    /**
     * Фиксирует, что клавиатура меню успешно показана пользователю.
     *
     * @param chatId идентификатор чата Telegram
     */
    void markKeyboardVisible(Long chatId);

    /**
     * Проверяет, зафиксирован ли факт отправки запроса контакта в чате.
     *
     * @param chatId идентификатор чата Telegram
     * @return {@code true}, если запрос контакта уже был отправлен
     */
    boolean isContactRequestSent(Long chatId);

    /**
     * Помечает, что пользователю отправлено сообщение с запросом контакта.
     *
     * @param chatId идентификатор чата Telegram
     */
    void markContactRequestSent(Long chatId);

    /**
     * Сбрасывает признак отправленного запроса контакта.
     *
     * @param chatId идентификатор чата Telegram
     */
    void clearContactRequestSent(Long chatId);

    /**
     * Проверяет, просмотрено ли последнее объявление пользователем.
     *
     * @param chatId идентификатор чата Telegram
     * @return {@code true}, если объявление уже просмотрено
     */
    boolean isAnnouncementSeen(Long chatId);

    /**
     * Помечает текущее объявление как просмотренное.
     *
     * @param chatId идентификатор чата Telegram
     */
    void markAnnouncementSeen(Long chatId);

    /**
     * Устанавливает новое объявление и сбрасывает признак просмотра.
     *
     * @param chatId              идентификатор чата Telegram
     * @param notificationId      идентификатор уведомления для показа
     * @param anchorMessageId     идентификатор сообщения Telegram, в котором отображено объявление
     * @param notificationUpdatedAt момент последнего обновления содержимого объявления
     */
    void updateAnnouncement(Long chatId,
                            Long notificationId,
                            Integer anchorMessageId,
                            ZonedDateTime notificationUpdatedAt);

    /**
     * Фиксирует, что активное объявление уже просмотрено пользователем без смены якорного сообщения.
     *
     * @param chatId         идентификатор чата Telegram
     * @param notificationId идентификатор активного уведомления администратора
     * @param updatedAt      момент последнего обновления уведомления
     */
    void setAnnouncementAsSeen(Long chatId, Long notificationId, ZonedDateTime updatedAt);
}
