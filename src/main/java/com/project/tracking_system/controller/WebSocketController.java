package com.project.tracking_system.controller;

import com.project.tracking_system.entity.UpdateResult;
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

    // Отправить только текстовое уведомление
    public void sendUpdateStatus(Long userId, String message, boolean completed) {
        UpdateResult updateResult = new UpdateResult(completed, message);
        log.info("📡 WebSocket отправка сообщения на /topic/status/{}: {}", userId, updateResult);
        messagingTemplate.convertAndSend("/topic/status/" + userId, updateResult);
    }

    // Отправить детализированное уведомление с обновлёнными данными
    public void sendDetailUpdateStatus(Long userId, UpdateResult updateResult) {
        log.info("📡 WebSocket отправка сообщения на /topic/status/{}: {}", userId, updateResult);
        messagingTemplate.convertAndSend("/topic/status/" + userId, updateResult);
    }

}