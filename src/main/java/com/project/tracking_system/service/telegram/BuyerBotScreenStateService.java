package com.project.tracking_system.service.telegram;

import com.project.tracking_system.entity.BuyerBotScreen;
import com.project.tracking_system.entity.BuyerBotScreenState;
import com.project.tracking_system.repository.BuyerBotScreenStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Сервис управления состоянием якорных сообщений покупателей.
 * <p>
 * Позволяет считывать и обновлять идентификатор последнего сообщения и экран,
 * чтобы бот мог возобновлять сценарий после рестарта приложения.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class BuyerBotScreenStateService {

    private final BuyerBotScreenStateRepository repository;

    /**
     * Возвращает сохранённое состояние для указанного чата Telegram.
     *
     * @param chatId идентификатор чата
     * @return сохранённое состояние или {@link Optional#empty()}, если запись отсутствует
     */
    @Transactional(readOnly = true)
    public Optional<BuyerBotScreenState> findState(Long chatId) {
        if (chatId == null) {
            return Optional.empty();
        }
        return repository.findById(chatId);
    }

    /**
     * Сохраняет идентификатор якорного сообщения и последний экран.
     * <p>
     * Если запись отсутствует, она будет создана.
     * </p>
     *
     * @param chatId          идентификатор чата Telegram
     * @param anchorMessageId идентификатор сообщения или {@code null}, если якорь отсутствует
     * @param screen          экран, который отображается пользователю
     */
    @Transactional
    public void saveState(Long chatId, Integer anchorMessageId, BuyerBotScreen screen) {
        if (chatId == null) {
            return;
        }

        BuyerBotScreenState state = repository.findById(chatId)
                .orElseGet(() -> new BuyerBotScreenState(chatId, null, null));
        state.setAnchorMessageId(anchorMessageId);
        state.setLastScreen(screen);
        repository.save(state);
    }

    /**
     * Обновляет только идентификатор якорного сообщения, сохраняя последний экран.
     *
     * @param chatId          идентификатор чата Telegram
     * @param anchorMessageId идентификатор сообщения, который нужно зафиксировать
     */
    @Transactional
    public void updateAnchor(Long chatId, Integer anchorMessageId) {
        if (chatId == null || anchorMessageId == null) {
            return;
        }

        BuyerBotScreenState state = repository.findById(chatId)
                .orElseGet(() -> new BuyerBotScreenState(chatId, null, null));
        state.setAnchorMessageId(anchorMessageId);
        repository.save(state);
    }

    /**
     * Сбрасывает идентификатор якорного сообщения, оставляя сведения об экране.
     *
     * @param chatId идентификатор чата Telegram
     */
    @Transactional
    public void clearAnchor(Long chatId) {
        if (chatId == null) {
            return;
        }

        repository.findById(chatId).ifPresent(state -> {
            state.setAnchorMessageId(null);
            repository.save(state);
        });
    }
}

