package com.project.tracking_system.service.telegram.support;

import com.project.tracking_system.entity.BuyerBotScreen;
import com.project.tracking_system.entity.BuyerChatState;
import com.project.tracking_system.service.telegram.ChatSession;
import com.project.tracking_system.service.telegram.ChatSessionRepository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Простая потокобезопасная реализация репозитория на основе памяти для тестов.
 */
public class InMemoryChatSessionRepository implements ChatSessionRepository {

    private final Map<Long, ChatSession> sessions = new ConcurrentHashMap<>();

    @Override
    public Optional<ChatSession> find(Long chatId) {
        if (chatId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(sessions.get(chatId))
                .map(this::copyOf);
    }

    @Override
    public ChatSession save(ChatSession session) {
        if (session == null || session.getChatId() == null) {
            return session;
        }
        sessions.put(session.getChatId(), copyOf(session));
        return session;
    }

    @Override
    public BuyerChatState getState(Long chatId) {
        if (chatId == null) {
            return BuyerChatState.IDLE;
        }
        return sessions.getOrDefault(chatId, new ChatSession(chatId, BuyerChatState.IDLE, null, null))
                .getState();
    }

    @Override
    public void updateState(Long chatId, BuyerChatState state) {
        if (chatId == null || state == null) {
            return;
        }
        ChatSession session = sessions.computeIfAbsent(chatId,
                id -> new ChatSession(id, BuyerChatState.IDLE, null, null));
        session.setState(state);
    }

    @Override
    public void updateAnchor(Long chatId, Integer anchorMessageId) {
        if (chatId == null || anchorMessageId == null) {
            return;
        }
        ChatSession session = sessions.computeIfAbsent(chatId,
                id -> new ChatSession(id, BuyerChatState.IDLE, null, null));
        session.setAnchorMessageId(anchorMessageId);
    }

    @Override
    public void updateAnchorAndScreen(Long chatId,
                                      Integer anchorMessageId,
                                      BuyerBotScreen screen,
                                      List<BuyerBotScreen> navigationPath) {
        if (chatId == null) {
            return;
        }
        ChatSession session = sessions.computeIfAbsent(chatId,
                id -> new ChatSession(id, BuyerChatState.IDLE, null, null));
        session.setAnchorMessageId(anchorMessageId);
        session.setLastScreen(screen);
        session.setNavigationPath(navigationPath);
    }

    @Override
    public void clearAnchor(Long chatId) {
        if (chatId == null) {
            return;
        }
        ChatSession session = sessions.get(chatId);
        if (session != null) {
            session.setAnchorMessageId(null);
            session.setPersistentKeyboardHidden(true);
        }
    }

    @Override
    public void deactivateAnchor(Long chatId) {
        if (chatId == null) {
            return;
        }
        ChatSession session = sessions.get(chatId);
        if (session != null) {
            session.setAnchorMessageId(null);
        }
    }

    @Override
    public boolean isKeyboardHidden(Long chatId) {
        if (chatId == null) {
            return false;
        }
        return sessions.getOrDefault(chatId, new ChatSession(chatId, BuyerChatState.IDLE, null, null))
                .isPersistentKeyboardHidden();
    }

    @Override
    public void markKeyboardHidden(Long chatId) {
        if (chatId == null) {
            return;
        }
        ChatSession session = sessions.computeIfAbsent(chatId,
                id -> new ChatSession(id, BuyerChatState.IDLE, null, null));
        session.setPersistentKeyboardHidden(true);
    }

    @Override
    public void markKeyboardVisible(Long chatId) {
        if (chatId == null) {
            return;
        }
        ChatSession session = sessions.computeIfAbsent(chatId,
                id -> new ChatSession(id, BuyerChatState.IDLE, null, null));
        session.setPersistentKeyboardHidden(false);
    }

    @Override
    public boolean isContactRequestSent(Long chatId) {
        if (chatId == null) {
            return false;
        }
        return sessions.getOrDefault(chatId, new ChatSession(chatId, BuyerChatState.IDLE, null, null))
                .isContactRequestSent();
    }

    @Override
    public void markContactRequestSent(Long chatId) {
        if (chatId == null) {
            return;
        }
        ChatSession session = sessions.computeIfAbsent(chatId,
                id -> new ChatSession(id, BuyerChatState.IDLE, null, null));
        session.setContactRequestSent(true);
    }

    @Override
    public void clearContactRequestSent(Long chatId) {
        if (chatId == null) {
            return;
        }
        ChatSession session = sessions.computeIfAbsent(chatId,
                id -> new ChatSession(id, BuyerChatState.IDLE, null, null));
        session.setContactRequestSent(false);
    }

    /**
     * Показывает, просмотрено ли объявление в текущей сессии.
     */
    @Override
    public boolean isAnnouncementSeen(Long chatId) {
        if (chatId == null) {
            return false;
        }
        return sessions.getOrDefault(chatId, new ChatSession(chatId, BuyerChatState.IDLE, null, null))
                .isAnnouncementSeen();
    }

    /**
     * Помечает объявление как прочитанное пользователем.
     */
    @Override
    public void markAnnouncementSeen(Long chatId) {
        if (chatId == null) {
            return;
        }
        ChatSession session = sessions.computeIfAbsent(chatId,
                id -> new ChatSession(id, BuyerChatState.IDLE, null, null));
        session.setAnnouncementSeen(true);
    }

    /**
     * Сохраняет параметры текущего объявления, сбрасывая признак просмотра.
     */
    @Override
    public void updateAnnouncement(Long chatId,
                                   Long notificationId,
                                   Integer anchorMessageId,
                                   ZonedDateTime notificationUpdatedAt) {
        if (chatId == null) {
            return;
        }
        ChatSession session = sessions.computeIfAbsent(chatId,
                id -> new ChatSession(id, BuyerChatState.IDLE, null, null));
        session.setCurrentNotificationId(notificationId);
        session.setAnnouncementAnchorMessageId(anchorMessageId);
        session.setAnnouncementSeen(false);
        session.setAnnouncementUpdatedAt(notificationUpdatedAt);
    }

    /**
     * Помечает активное объявление как просмотренное без изменения якоря.
     */
    @Override
    public void setAnnouncementAsSeen(Long chatId, Long notificationId, ZonedDateTime updatedAt) {
        if (chatId == null) {
            return;
        }
        ChatSession session = sessions.computeIfAbsent(chatId,
                id -> new ChatSession(id, BuyerChatState.IDLE, null, null));
        session.setCurrentNotificationId(notificationId);
        session.setAnnouncementSeen(true);
        session.setAnnouncementUpdatedAt(updatedAt);
    }

    /**
     * Создаёт копию сессии, чтобы тесты не изменяли внутреннее состояние напрямую.
     *
     * @param session исходная сессия
     * @return копия для выдачи наружу
     */
    private ChatSession copyOf(ChatSession session) {
        if (session == null) {
            return null;
        }
        ChatSession copy = new ChatSession(session.getChatId(), session.getState(),
                session.getAnchorMessageId(), session.getLastScreen(),
                session.isPersistentKeyboardHidden(),
                session.isContactRequestSent());
        copy.setCurrentNotificationId(session.getCurrentNotificationId());
        copy.setAnnouncementAnchorMessageId(session.getAnnouncementAnchorMessageId());
        copy.setAnnouncementSeen(session.isAnnouncementSeen());
        copy.setAnnouncementUpdatedAt(session.getAnnouncementUpdatedAt());
        copy.setNavigationPath(session.getNavigationPath());
        copy.setReturnParcelId(session.getReturnParcelId());
        copy.setReturnParcelTrackNumber(session.getReturnParcelTrackNumber());
        copy.setReturnReason(session.getReturnReason());
        copy.setReturnComment(session.getReturnComment());
        copy.setReturnRequestedAt(session.getReturnRequestedAt());
        copy.setReturnReverseTrackNumber(session.getReturnReverseTrackNumber());
        copy.setReturnIdempotencyKey(session.getReturnIdempotencyKey());
        return copy;
    }
}
