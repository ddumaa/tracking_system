package com.project.tracking_system.service.telegram;

import com.project.tracking_system.entity.BuyerAnnouncementState;
import com.project.tracking_system.entity.BuyerBotScreen;
import com.project.tracking_system.entity.BuyerBotScreenState;
import com.project.tracking_system.entity.BuyerChatState;
import com.project.tracking_system.repository.BuyerAnnouncementStateRepository;
import com.project.tracking_system.repository.BuyerBotScreenStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Реализация репозитория сессий, использующая базу данных для долговременного хранения.
 */
@Repository
@RequiredArgsConstructor
public class DatabaseChatSessionRepository implements ChatSessionRepository {

    private final BuyerBotScreenStateRepository repository;
    private final BuyerAnnouncementStateRepository announcementRepository;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<ChatSession> find(Long chatId) {
        if (chatId == null) {
            return Optional.empty();
        }
        Optional<BuyerBotScreenState> screenState = repository.findById(chatId);
        Optional<BuyerAnnouncementState> announcementState = announcementRepository.findById(chatId);

        if (screenState.isEmpty() && announcementState.isEmpty()) {
            return Optional.empty();
        }

        ChatSession session = screenState
                .map(this::toSession)
                .orElseGet(() -> new ChatSession(chatId, BuyerChatState.IDLE, null, null));
        announcementState.ifPresent(state -> populateAnnouncement(session, state));
        return Optional.of(session);
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
        Long chatId = session.getChatId();
        BuyerBotScreenState entity = getOrCreateEntity(chatId);
        entity.setChatState(session.getState());
        entity.setAnchorMessageId(session.getAnchorMessageId());
        entity.setLastScreen(session.getLastScreen());
        entity.setKeyboardHidden(session.isPersistentKeyboardHidden());
        entity.setContactRequestSent(session.isContactRequestSent());
        entity.setNavigationPath(serializeNavigationPath(session.getNavigationPath()));
        entity.setReturnRequestType(session.getReturnRequestType());
        entity.setReturnStoreName(session.getReturnStoreName());
        entity.setReturnParcelId(session.getReturnParcelId());
        entity.setReturnParcelTrack(session.getReturnParcelTrackNumber());
        entity.setReturnReason(session.getReturnReason());
        entity.setReturnIdempotencyKey(session.getReturnIdempotencyKey());
        BuyerBotScreenState saved = repository.save(entity);

        BuyerAnnouncementState announcement = getOrCreateAnnouncementEntity(chatId);
        announcement.setCurrentNotificationId(session.getCurrentNotificationId());
        announcement.setAnchorMessageId(session.getAnnouncementAnchorMessageId());
        announcement.setAnnouncementSeen(session.isAnnouncementSeen());
        announcement.setNotificationUpdatedAt(session.getAnnouncementUpdatedAt());
        BuyerAnnouncementState savedAnnouncement = announcementRepository.save(announcement);

        ChatSession result = toSession(saved);
        populateAnnouncement(result, savedAnnouncement);
        return result;
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
    public void updateAnchorAndScreen(Long chatId,
                                      Integer anchorMessageId,
                                      BuyerBotScreen screen,
                                      List<BuyerBotScreen> navigationPath) {
        if (chatId == null) {
            return;
        }
        BuyerBotScreenState entity = getOrCreateEntity(chatId);
        entity.setAnchorMessageId(anchorMessageId);
        entity.setLastScreen(screen);
        entity.setNavigationPath(serializeNavigationPath(navigationPath));
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
            entity.setKeyboardHidden(true);
            repository.save(entity);
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void deactivateAnchor(Long chatId) {
        if (chatId == null) {
            return;
        }
        repository.findById(chatId).ifPresent(entity -> {
            entity.setAnchorMessageId(null);
            repository.save(entity);
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public boolean isKeyboardHidden(Long chatId) {
        if (chatId == null) {
            return false;
        }
        return repository.findById(chatId)
                .map(BuyerBotScreenState::getKeyboardHidden)
                .map(Boolean::booleanValue)
                .orElse(true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void markKeyboardHidden(Long chatId) {
        if (chatId == null) {
            return;
        }
        BuyerBotScreenState entity = getOrCreateEntity(chatId);
        entity.setKeyboardHidden(true);
        repository.save(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void markKeyboardVisible(Long chatId) {
        if (chatId == null) {
            return;
        }
        BuyerBotScreenState entity = getOrCreateEntity(chatId);
        entity.setKeyboardHidden(false);
        repository.save(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public boolean isContactRequestSent(Long chatId) {
        if (chatId == null) {
            return false;
        }
        return repository.findById(chatId)
                .map(BuyerBotScreenState::getContactRequestSent)
                .map(Boolean::booleanValue)
                .orElse(false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void markContactRequestSent(Long chatId) {
        if (chatId == null) {
            return;
        }
        BuyerBotScreenState entity = getOrCreateEntity(chatId);
        entity.setContactRequestSent(true);
        repository.save(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void clearContactRequestSent(Long chatId) {
        if (chatId == null) {
            return;
        }
        BuyerBotScreenState entity = getOrCreateEntity(chatId);
        entity.setContactRequestSent(false);
        repository.save(entity);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(readOnly = true)
    public boolean isAnnouncementSeen(Long chatId) {
        if (chatId == null) {
            return false;
        }
        return announcementRepository.findById(chatId)
                .map(BuyerAnnouncementState::getAnnouncementSeen)
                .map(Boolean::booleanValue)
                .orElse(false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void markAnnouncementSeen(Long chatId) {
        if (chatId == null) {
            return;
        }
        announcementRepository.findById(chatId).ifPresent(state -> {
            if (!Boolean.TRUE.equals(state.getAnnouncementSeen())) {
                state.setAnnouncementSeen(true);
                announcementRepository.save(state);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void updateAnnouncement(Long chatId,
                                   Long notificationId,
                                   Integer anchorMessageId,
                                   ZonedDateTime notificationUpdatedAt) {
        if (chatId == null) {
            return;
        }
        BuyerAnnouncementState state = getOrCreateAnnouncementEntity(chatId);
        state.setCurrentNotificationId(notificationId);
        state.setAnchorMessageId(anchorMessageId);
        state.setAnnouncementSeen(false);
        state.setNotificationUpdatedAt(notificationUpdatedAt);
        announcementRepository.save(state);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void setAnnouncementAsSeen(Long chatId, Long notificationId, ZonedDateTime updatedAt) {
        if (chatId == null) {
            return;
        }
        BuyerAnnouncementState state = getOrCreateAnnouncementEntity(chatId);
        state.setCurrentNotificationId(notificationId);
        state.setAnnouncementSeen(true);
        state.setNotificationUpdatedAt(updatedAt);
        announcementRepository.save(state);
    }

    /**
     * Возвращает сущность состояния, создавая новую запись с настройками по умолчанию.
     *
     * @param chatId идентификатор чата Telegram
     * @return сущность для модификации
     */
    private BuyerBotScreenState getOrCreateEntity(Long chatId) {
        return repository.findById(chatId)
                .orElseGet(() -> new BuyerBotScreenState(chatId,
                        null,
                        null,
                        BuyerChatState.IDLE,
                        Boolean.TRUE,
                        Boolean.FALSE,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null));
    }

    /**
     * Возвращает состояние объявлений, создавая запись с настройками по умолчанию.
     *
     * @param chatId идентификатор чата Telegram
     * @return сущность состояния объявлений
     */
    private BuyerAnnouncementState getOrCreateAnnouncementEntity(Long chatId) {
        return announcementRepository.findById(chatId)
                .orElseGet(() -> {
                    BuyerAnnouncementState state = new BuyerAnnouncementState();
                    state.setChatId(chatId);
                    state.setAnnouncementSeen(false);
                    return state;
                });
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
        ChatSession session = new ChatSession(
                entity.getChatId(),
                entity.getChatState(),
                entity.getAnchorMessageId(),
                entity.getLastScreen(),
                Boolean.TRUE.equals(entity.getKeyboardHidden()),
                Boolean.TRUE.equals(entity.getContactRequestSent())
        );
        session.setNavigationPath(deserializeNavigationPath(entity.getNavigationPath()));
        session.setReturnRequestType(entity.getReturnRequestType());
        session.setReturnStoreName(entity.getReturnStoreName());
        session.setReturnParcelId(entity.getReturnParcelId());
        session.setReturnParcelTrackNumber(entity.getReturnParcelTrack());
        session.setReturnReason(entity.getReturnReason());
        session.setReturnIdempotencyKey(entity.getReturnIdempotencyKey());
        return session;
    }

    /**
     * Переносит данные об объявлении в объект сессии.
     *
     * @param session     доменная модель сессии
     * @param announcement состояние объявлений, загруженное из базы
     */
    private void populateAnnouncement(ChatSession session, BuyerAnnouncementState announcement) {
        if (session == null || announcement == null) {
            return;
        }
        session.setCurrentNotificationId(announcement.getCurrentNotificationId());
        session.setAnnouncementAnchorMessageId(announcement.getAnchorMessageId());
        session.setAnnouncementSeen(Boolean.TRUE.equals(announcement.getAnnouncementSeen()));
        session.setAnnouncementUpdatedAt(announcement.getNotificationUpdatedAt());
    }

    /**
     * Преобразует путь навигации в строку для хранения в базе данных.
     *
     * @param navigationPath последовательность экранов
     * @return сериализованное представление или {@code null}, если путь пуст
     */
    private String serializeNavigationPath(List<BuyerBotScreen> navigationPath) {
        if (navigationPath == null || navigationPath.isEmpty()) {
            return null;
        }
        return navigationPath.stream()
                .filter(Objects::nonNull)
                .map(Enum::name)
                .collect(Collectors.joining(","));
    }

    /**
     * Восстанавливает путь навигации из строки, сохранённой в базе данных.
     *
     * @param serialized сохранённое представление пути
     * @return список экранов с учётом возможных ошибок формата
     */
    private List<BuyerBotScreen> deserializeNavigationPath(String serialized) {
        List<BuyerBotScreen> result = new ArrayList<>();
        if (serialized == null || serialized.isBlank()) {
            return result;
        }
        String[] parts = serialized.split(",");
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            try {
                result.add(BuyerBotScreen.valueOf(part.trim()));
            } catch (IllegalArgumentException ignored) {
                // Игнорируем устаревшие или некорректные значения
            }
        }
        return result;
    }
}
