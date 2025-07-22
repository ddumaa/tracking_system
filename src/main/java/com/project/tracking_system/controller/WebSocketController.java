package com.project.tracking_system.controller;

import com.project.tracking_system.entity.UpdateResult;
import com.project.tracking_system.dto.TrackProcessingStartedDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/**
 * @author Dmitriy Anisimov
 * @date 19.02.2025
 */
@Slf4j
@RequiredArgsConstructor
@Controller
public class WebSocketController {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Отправляет текстовое уведомление пользователю через WebSocket.
     *
     * @param userId   идентификатор пользователя
     * @param message  текст сообщения
     * @param completed признак успешности операции
     *                 <p>
     *                 Создаётся {@link UpdateResult} и отправляется на канал
     *                 <code>/topic/status/{userId}</code>.
     */
    public void sendUpdateStatus(Long userId, String message, boolean completed) {
        UpdateResult updateResult = new UpdateResult(completed, message);
        getDebug(userId, updateResult);
        messagingTemplate.convertAndSend("/topic/status/" + userId, updateResult);
    }

    /**
     * Отправляет детализированное уведомление пользователю.
     *
     * @param userId       идентификатор пользователя
     * @param updateResult данные об обновлении
     *                     <p>
     *                     Сообщение сразу публикуется в канал
     *                     <code>/topic/status/{userId}</code>.
     */
    public void sendDetailUpdateStatus(Long userId, UpdateResult updateResult) {
        getDebug(userId, updateResult);
        messagingTemplate.convertAndSend("/topic/status/" + userId, updateResult);
    }

    /**
     * Уведомляет пользователя о начале пакетной обработки треков.
     *
     * @param userId     идентификатор пользователя
     * @param startedDto информация о количестве треков и ориентировочном ETA
     */
    public void sendTrackProcessingStarted(Long userId, TrackProcessingStartedDTO startedDto) {
        log.debug("\uD83D\uDCE1 WebSocket старт обработки для {}: {}", userId, startedDto);
        messagingTemplate.convertAndSend("/topic/track-processing-started/" + userId, startedDto);
    }

    private static void getDebug(Long userId, UpdateResult updateResult) {
        log.debug("📡 WebSocket отправка сообщения на /topic/status/{}: {}", userId, updateResult);
    }

}