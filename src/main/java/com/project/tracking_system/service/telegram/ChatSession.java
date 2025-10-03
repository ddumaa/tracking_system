package com.project.tracking_system.service.telegram;

import com.project.tracking_system.entity.BuyerBotScreen;
import com.project.tracking_system.entity.BuyerChatState;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    private final List<BuyerBotScreen> navigationPath;
    private boolean persistentKeyboardHidden;
    private boolean contactRequestSent;
    private Long currentNotificationId;
    private Integer announcementAnchorMessageId;
    private boolean announcementSeen;
    private ZonedDateTime announcementUpdatedAt;
    private ReturnRequestType returnRequestType;
    private String returnStoreName;
    private Long returnParcelId;
    private String returnParcelTrackNumber;
    private String returnReason;
    private String returnComment;
    private ZonedDateTime returnRequestedAt;
    private String returnReverseTrackNumber;
    private String returnIdempotencyKey;

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
        this.navigationPath = new ArrayList<>();
        this.persistentKeyboardHidden = persistentKeyboardHidden;
        this.contactRequestSent = contactRequestSent;
        this.currentNotificationId = null;
        this.announcementAnchorMessageId = null;
        this.announcementSeen = false;
        this.announcementUpdatedAt = null;
        this.returnRequestType = null;
        this.returnStoreName = null;
        this.returnParcelId = null;
        this.returnParcelTrackNumber = null;
        this.returnReason = null;
        this.returnComment = null;
        this.returnRequestedAt = null;
        this.returnReverseTrackNumber = null;
        this.returnIdempotencyKey = null;
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
     * Возвращает текущий путь навигации от корневого меню до активного экрана.
     *
     * @return неизменяемый список экранов
     */
    public List<BuyerBotScreen> getNavigationPath() {
        return Collections.unmodifiableList(new ArrayList<>(navigationPath));
    }

    /**
     * Заменяет путь навигации данными, восстановленными из хранилища.
     *
     * @param navigationPath последовательность экранов или {@code null}
     */
    public void setNavigationPath(List<BuyerBotScreen> navigationPath) {
        this.navigationPath.clear();
        if (navigationPath == null || navigationPath.isEmpty()) {
            return;
        }
        for (BuyerBotScreen screen : navigationPath) {
            if (screen != null) {
                this.navigationPath.add(screen);
            }
        }
    }

    /**
     * Формирует потенциальный путь навигации без изменения текущего состояния.
     *
     * @param screen         экран, который планируется показать
     * @param allowDuplicate разрешено ли добавлять повтор текущего экрана как отдельный шаг
     * @return путь навигации с учётом нового экрана
     */
    public List<BuyerBotScreen> projectNavigationPath(BuyerBotScreen screen, boolean allowDuplicate) {
        List<BuyerBotScreen> projected = new ArrayList<>(navigationPath);
        if (screen == null) {
            return projected;
        }

        if (screen == BuyerBotScreen.MENU) {
            projected.clear();
            projected.add(BuyerBotScreen.MENU);
            return projected;
        }

        if (projected.isEmpty()) {
            projected.add(BuyerBotScreen.MENU);
        }

        BuyerBotScreen current = projected.get(projected.size() - 1);
        if (current != screen) {
            projected.add(screen);
        } else if (allowDuplicate) {
            projected.add(screen);
        }
        return projected;
    }

    /**
     * Фиксирует путь навигации после отображения нового экрана.
     *
     * @param screen         экран, который был показан пользователю
     * @param allowDuplicate разрешено ли добавлять повтор текущего экрана как отдельный шаг
     */
    public void updateNavigationForScreen(BuyerBotScreen screen, boolean allowDuplicate) {
        if (screen == null) {
            return;
        }
        List<BuyerBotScreen> updated = projectNavigationPath(screen, allowDuplicate);
        navigationPath.clear();
        navigationPath.addAll(updated);
        this.lastScreen = screen;
    }

    /**
     * Возвращает экран, который следует показать при возврате на шаг назад.
     *
     * @return предыдущий экран или главное меню, если история пуста
     */
    public BuyerBotScreen navigateBack() {
        if (navigationPath.isEmpty()) {
            navigationPath.add(BuyerBotScreen.MENU);
            lastScreen = BuyerBotScreen.MENU;
            return BuyerBotScreen.MENU;
        }

        if (navigationPath.size() == 1) {
            BuyerBotScreen current = navigationPath.get(0);
            lastScreen = current;
            return current;
        }

        navigationPath.remove(navigationPath.size() - 1);
        BuyerBotScreen target = navigationPath.get(navigationPath.size() - 1);
        lastScreen = target;
        return target;
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

    /**
     * Возвращает время обновления объявления, которое видел пользователь.
     *
     * @return момент обновления или {@code null}, если информация отсутствует
     */
    public ZonedDateTime getAnnouncementUpdatedAt() {
        return announcementUpdatedAt;
    }

    /**
     * Сохраняет отметку о времени обновления показанного объявления.
     *
     * @param announcementUpdatedAt момент обновления объявления или {@code null}
     */
    public void setAnnouncementUpdatedAt(ZonedDateTime announcementUpdatedAt) {
        this.announcementUpdatedAt = announcementUpdatedAt;
    }

    /**
     * Возвращает выбранный пользователем тип заявки.
     *
     * @return тип заявки или {@code null}, если выбор ещё не сделан
     */
    public ReturnRequestType getReturnRequestType() {
        return returnRequestType;
    }

    /**
     * Сохраняет тип заявки, выбранный пользователем.
     *
     * @param returnRequestType тип заявки (возврат или обмен)
     */
    public void setReturnRequestType(ReturnRequestType returnRequestType) {
        this.returnRequestType = returnRequestType;
    }

    /**
     * Возвращает название магазина, выбранного пользователем для оформления заявки.
     *
     * @return название магазина или {@code null}
     */
    public String getReturnStoreName() {
        return returnStoreName;
    }

    /**
     * Сохраняет название магазина, выбранного пользователем.
     *
     * @param returnStoreName отображаемое название магазина
     */
    public void setReturnStoreName(String returnStoreName) {
        this.returnStoreName = returnStoreName;
    }

    /**
     * Возвращает идентификатор посылки, для которой оформляется возврат.
     *
     * @return идентификатор посылки или {@code null}
     */
    public Long getReturnParcelId() {
        return returnParcelId;
    }

    /**
     * Сохраняет идентификатор посылки, выбранной для возврата или обмена.
     *
     * @param returnParcelId идентификатор посылки
     */
    public void setReturnParcelId(Long returnParcelId) {
        this.returnParcelId = returnParcelId;
    }

    /**
     * Возвращает трек-номер исходной посылки, отображаемый пользователю.
     *
     * @return трек-номер или {@code null}, если он неизвестен
     */
    public String getReturnParcelTrackNumber() {
        return returnParcelTrackNumber;
    }

    /**
     * Сохраняет трек-номер исходной посылки для дальнейших подсказок.
     *
     * @param returnParcelTrackNumber трек-номер исходной посылки
     */
    public void setReturnParcelTrackNumber(String returnParcelTrackNumber) {
        this.returnParcelTrackNumber = returnParcelTrackNumber;
    }

    /**
     * Возвращает введённую пользователем причину возврата.
     *
     * @return причина или {@code null}
     */
    public String getReturnReason() {
        return returnReason;
    }

    /**
     * Сохраняет текст причины возврата.
     *
     * @param returnReason причина возврата
     */
    public void setReturnReason(String returnReason) {
        this.returnReason = returnReason;
    }

    /**
     * Возвращает дополнительный комментарий к заявке на возврат.
     *
     * @return комментарий или {@code null}
     */
    public String getReturnComment() {
        return returnComment;
    }

    /**
     * Сохраняет дополнительный комментарий, введённый пользователем.
     *
     * @param returnComment текст комментария
     */
    public void setReturnComment(String returnComment) {
        this.returnComment = returnComment;
    }

    /**
     * Возвращает дату, когда пользователь запросил возврат.
     *
     * @return момент запроса или {@code null}
     */
    public ZonedDateTime getReturnRequestedAt() {
        return returnRequestedAt;
    }

    /**
     * Сохраняет дату, когда пользователь решил оформить возврат.
     *
     * @param returnRequestedAt дата запроса возврата
     */
    public void setReturnRequestedAt(ZonedDateTime returnRequestedAt) {
        this.returnRequestedAt = returnRequestedAt;
    }

    /**
     * Возвращает трек-номер обратной отправки, если он известен пользователю.
     *
     * @return трек-номер обратной отправки или {@code null}
     */
    public String getReturnReverseTrackNumber() {
        return returnReverseTrackNumber;
    }

    /**
     * Сохраняет трек-номер обратной отправки.
     *
     * @param returnReverseTrackNumber трек-номер обратной отправки
     */
    public void setReturnReverseTrackNumber(String returnReverseTrackNumber) {
        this.returnReverseTrackNumber = returnReverseTrackNumber;
    }

    /**
     * Возвращает идемпотентный ключ заявки, сформированный в рамках диалога.
     *
     * @return идемпотентный ключ или {@code null}
     */
    public String getReturnIdempotencyKey() {
        return returnIdempotencyKey;
    }

    /**
     * Сохраняет идемпотентный ключ для регистрации заявки.
     *
     * @param returnIdempotencyKey идемпотентный ключ
     */
    public void setReturnIdempotencyKey(String returnIdempotencyKey) {
        this.returnIdempotencyKey = returnIdempotencyKey;
    }

    /**
     * Очищает временные данные оформления возврата и обмена.
     */
    public void clearReturnRequestData() {
        this.returnRequestType = null;
        this.returnStoreName = null;
        this.returnParcelId = null;
        this.returnParcelTrackNumber = null;
        this.returnReason = null;
        this.returnComment = null;
        this.returnRequestedAt = null;
        this.returnReverseTrackNumber = null;
        this.returnIdempotencyKey = null;
    }
}
