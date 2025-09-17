package com.project.tracking_system.service.telegram.support;

import com.project.tracking_system.entity.BuyerBotScreen;
import com.project.tracking_system.entity.BuyerChatState;
import com.project.tracking_system.service.telegram.ChatSession;
import com.project.tracking_system.service.telegram.ChatSessionRepository;

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
    public void updateAnchorAndScreen(Long chatId, Integer anchorMessageId, BuyerBotScreen screen) {
        if (chatId == null) {
            return;
        }
        ChatSession session = sessions.computeIfAbsent(chatId,
                id -> new ChatSession(id, BuyerChatState.IDLE, null, null));
        session.setAnchorMessageId(anchorMessageId);
        session.setLastScreen(screen);
    }

    @Override
    public void clearAnchor(Long chatId) {
        if (chatId == null) {
            return;
        }
        ChatSession session = sessions.get(chatId);
        if (session != null) {
            session.setAnchorMessageId(null);
        }
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
                session.getAnchorMessageId(), session.getLastScreen());
        return copy;
    }
}
