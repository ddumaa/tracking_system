package com.project.tracking_system.service.telegram;

import com.project.tracking_system.entity.BuyerBotScreen;
import com.project.tracking_system.entity.BuyerBotScreenState;
import com.project.tracking_system.entity.BuyerChatState;
import com.project.tracking_system.repository.BuyerBotScreenStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Реализация репозитория сессий, использующая базу данных для долговременного хранения.
 */
@Repository
@RequiredArgsConstructor
public class DatabaseChatSessionRepository implements ChatSessionRepository {

    private final BuyerBotScreenStateRepository repository;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<ChatSession> find(Long chatId) {
        if (chatId == null) {
            return Optional.empty();
        }
        return repository.findById(chatId).map(this::toSession);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public ChatSession save(ChatSession session) {
        if (session == null || session.getChatId() == null) {
            return session;
        }
        BuyerBotScreenState entity = getOrCreateEntity(session.getChatId());
        entity.setChatState(session.getState());
        entity.setAnchorMessageId(session.getAnchorMessageId());
        entity.setLastScreen(session.getLastScreen());
        BuyerBotScreenState saved = repository.save(entity);
        return toSession(saved);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public BuyerChatState getState(Long chatId) {
        if (chatId == null) {
            return BuyerChatState.IDLE;
        }
        return repository.findById(chatId)
                .map(BuyerBotScreenState::getChatState)
                .orElse(BuyerChatState.IDLE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void updateState(Long chatId, BuyerChatState state) {
        if (chatId == null || state == null) {
            return;
        }
        BuyerBotScreenState entity = getOrCreateEntity(chatId);
        entity.setChatState(state);
        repository.save(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void updateAnchor(Long chatId, Integer anchorMessageId) {
        if (chatId == null || anchorMessageId == null) {
            return;
        }
        BuyerBotScreenState entity = getOrCreateEntity(chatId);
        entity.setAnchorMessageId(anchorMessageId);
        repository.save(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void updateAnchorAndScreen(Long chatId, Integer anchorMessageId, BuyerBotScreen screen) {
        if (chatId == null) {
            return;
        }
        BuyerBotScreenState entity = getOrCreateEntity(chatId);
        entity.setAnchorMessageId(anchorMessageId);
        entity.setLastScreen(screen);
        repository.save(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void clearAnchor(Long chatId) {
        if (chatId == null) {
            return;
        }
        repository.findById(chatId).ifPresent(entity -> {
            entity.setAnchorMessageId(null);
            repository.save(entity);
        });
    }

    /**
     * Возвращает сущность состояния, создавая новую запись с настройками по умолчанию.
     *
     * @param chatId идентификатор чата Telegram
     * @return сущность для модификации
     */
    private BuyerBotScreenState getOrCreateEntity(Long chatId) {
        return repository.findById(chatId)
                .orElseGet(() -> new BuyerBotScreenState(chatId, null, null, BuyerChatState.IDLE));
    }

    /**
     * Преобразует сущность JPA в доменную модель.
     *
     * @param entity сущность из базы данных
     * @return представление сессии для бизнес-слоя
     */
    private ChatSession toSession(BuyerBotScreenState entity) {
        if (entity == null) {
            return null;
        }
        return new ChatSession(
                entity.getChatId(),
                entity.getChatState(),
                entity.getAnchorMessageId(),
                entity.getLastScreen()
        );
    }
}
