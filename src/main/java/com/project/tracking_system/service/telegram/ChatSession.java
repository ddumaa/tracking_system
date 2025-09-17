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
        this.chatId = chatId;
        this.state = state != null ? state : BuyerChatState.IDLE;
        this.anchorMessageId = anchorMessageId;
        this.lastScreen = lastScreen;
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
}
