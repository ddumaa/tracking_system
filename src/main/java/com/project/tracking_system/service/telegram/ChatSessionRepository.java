package com.project.tracking_system.service.telegram;

import com.project.tracking_system.entity.BuyerBotScreen;
import com.project.tracking_system.entity.BuyerChatState;

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
}
