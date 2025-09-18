package com.project.tracking_system.service.telegram;

import com.project.tracking_system.entity.BuyerBotScreen;
import com.project.tracking_system.entity.BuyerChatState;

/**
 * Представление состояния чата покупателя, хранящееся в устойчивом хранилище.
 * <p>
 * Используется ботом для восстановления сценария при обработке новых сообщений.
 * </p>
 */
public class ChatSession {

    private final Long chatId;
    private BuyerChatState state;
    private Integer anchorMessageId;
    private BuyerBotScreen lastScreen;
    private boolean persistentKeyboardHidden;
    private boolean contactRequestSent;
    private Long currentNotificationId;
    private Integer announcementAnchorMessageId;
    private boolean announcementSeen;

    /**
     * Создаёт представление состояния чата.
     *
     * @param chatId          идентификатор чата Telegram
     * @param state           сценарное состояние диалога
     * @param anchorMessageId идентификатор якорного сообщения
     * @param lastScreen      последний отображённый экран
     */
    public ChatSession(Long chatId,
                       BuyerChatState state,
                       Integer anchorMessageId,
                       BuyerBotScreen lastScreen) {
        this(chatId, state, anchorMessageId, lastScreen, true, false);
    }

    /**
     * Создаёт представление состояния чата с указанием статуса клавиатуры.
     *
     * @param chatId                  идентификатор чата Telegram
     * @param state                   сценарное состояние диалога
     * @param anchorMessageId         идентификатор якорного сообщения
     * @param lastScreen              последний отображённый экран
     * @param persistentKeyboardHidden признак того, что меню-клавиатура скрыта
     */
    public ChatSession(Long chatId,
                       BuyerChatState state,
                       Integer anchorMessageId,
                       BuyerBotScreen lastScreen,
                       boolean persistentKeyboardHidden) {
        this(chatId, state, anchorMessageId, lastScreen, persistentKeyboardHidden, false);
    }

    /**
     * Создаёт представление состояния чата с расширенной информацией о показанных сообщениях.
     *
     * @param chatId                  идентификатор чата Telegram
     * @param state                   сценарное состояние диалога
     * @param anchorMessageId         идентификатор якорного сообщения
     * @param lastScreen              последний отображённый экран
     * @param persistentKeyboardHidden признак того, что меню-клавиатура скрыта
     * @param contactRequestSent      признак того, что запрос контакта уже отправлен
     */
    public ChatSession(Long chatId,
                       BuyerChatState state,
                       Integer anchorMessageId,
                       BuyerBotScreen lastScreen,
                       boolean persistentKeyboardHidden,
                       boolean contactRequestSent) {
        this.chatId = chatId;
        this.state = state != null ? state : BuyerChatState.IDLE;
        this.anchorMessageId = anchorMessageId;
        this.lastScreen = lastScreen;
        this.persistentKeyboardHidden = persistentKeyboardHidden;
        this.contactRequestSent = contactRequestSent;
        this.currentNotificationId = null;
        this.announcementAnchorMessageId = null;
        this.announcementSeen = false;
    }

    /**
     * Возвращает идентификатор чата Telegram.
     *
     * @return уникальный идентификатор чата
     */
    public Long getChatId() {
        return chatId;
    }

    /**
     * Возвращает сценарное состояние диалога.
     *
     * @return состояние чата
     */
    public BuyerChatState getState() {
        return state;
    }

    /**
     * Устанавливает новое состояние диалога.
     *
     * @param state состояние, которое необходимо зафиксировать
     */
    public void setState(BuyerChatState state) {
        this.state = state != null ? state : BuyerChatState.IDLE;
    }

    /**
     * Возвращает идентификатор якорного сообщения с инлайн-кнопками.
     *
     * @return идентификатор сообщения или {@code null}
     */
    public Integer getAnchorMessageId() {
        return anchorMessageId;
    }

    /**
     * Сохраняет идентификатор якорного сообщения.
     *
     * @param anchorMessageId идентификатор сообщения или {@code null}
     */
    public void setAnchorMessageId(Integer anchorMessageId) {
        this.anchorMessageId = anchorMessageId;
    }

    /**
     * Возвращает последний экран, отрисованный ботом для пользователя.
     *
     * @return тип экрана или {@code null}, если информация отсутствует
     */
    public BuyerBotScreen getLastScreen() {
        return lastScreen;
    }

    /**
     * Устанавливает последний экран, который необходимо восстановить после рестарта.
     *
     * @param lastScreen тип экрана для сохранения
     */
    public void setLastScreen(BuyerBotScreen lastScreen) {
        this.lastScreen = lastScreen;
    }

    /**
     * Показывает, скрыта ли клавиатура постоянного меню у пользователя.
     *
     * @return {@code true}, если клавиатура отсутствует и требует переотправки
     */
    public boolean isPersistentKeyboardHidden() {
        return persistentKeyboardHidden;
    }

    /**
     * Фиксирует состояние постоянной клавиатуры меню.
     *
     * @param persistentKeyboardHidden {@code true}, если клавиатура скрыта
     */
    public void setPersistentKeyboardHidden(boolean persistentKeyboardHidden) {
        this.persistentKeyboardHidden = persistentKeyboardHidden;
    }

    /**
     * Показывает, отправлялся ли запрос контакта в текущей сессии.
     *
     * @return {@code true}, если запрос контакта уже был отправлен
     */
    public boolean isContactRequestSent() {
        return contactRequestSent;
    }

    /**
     * Фиксирует факт отправки запроса контакта.
     *
     * @param contactRequestSent {@code true}, если сообщение уже показано пользователю
     */
    public void setContactRequestSent(boolean contactRequestSent) {
        this.contactRequestSent = contactRequestSent;
    }

    /**
     * Возвращает идентификатор текущего объявления для покупателя.
     *
     * @return идентификатор объявления или {@code null}
     */
    public Long getCurrentNotificationId() {
        return currentNotificationId;
    }

    /**
     * Сохраняет идентификатор актуального объявления для последующего показа.
     *
     * @param currentNotificationId идентификатор объявления или {@code null}
     */
    public void setCurrentNotificationId(Long currentNotificationId) {
        this.currentNotificationId = currentNotificationId;
    }

    /**
     * Возвращает идентификатор сообщения с объявлением, отправленного пользователю.
     *
     * @return идентификатор сообщения или {@code null}
     */
    public Integer getAnnouncementAnchorMessageId() {
        return announcementAnchorMessageId;
    }

    /**
     * Сохраняет сообщение, содержащее объявление и его элементы управления.
     *
     * @param announcementAnchorMessageId идентификатор сообщения или {@code null}
     */
    public void setAnnouncementAnchorMessageId(Integer announcementAnchorMessageId) {
        this.announcementAnchorMessageId = announcementAnchorMessageId;
    }

    /**
     * Проверяет, видел ли пользователь актуальное объявление.
     *
     * @return {@code true}, если объявление уже просмотрено
     */
    public boolean isAnnouncementSeen() {
        return announcementSeen;
    }

    /**
     * Фиксирует факт просмотра текущего объявления пользователем.
     *
     * @param announcementSeen {@code true}, если объявление просмотрено
     */
    public void setAnnouncementSeen(boolean announcementSeen) {
        this.announcementSeen = announcementSeen;
    }
}
