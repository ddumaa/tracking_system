package com.project.tracking_system.service.track;

import com.project.tracking_system.controller.WebSocketController;
import com.project.tracking_system.entity.UpdateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Сервис для отправки уведомлений пользователям через WebSocket.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class TrackNotificationService {

    private final WebSocketController webSocketController;

    /**
     * Отправляет текстовое уведомление пользователю.
     *
     * @param userId    идентификатор пользователя
     * @param message   текст сообщения
     * @param completed флаг завершения операции
     */
    public void notifyStatus(Long userId, String message, boolean completed) {
        log.debug("Отправка уведомления пользователю {}: {}", userId, message);
        webSocketController.sendUpdateStatus(userId, message, completed);
    }

    /**
     * Отправляет детализированное уведомление.
     *
     * @param userId идентификатор пользователя
     * @param result результат обновления
     */
    public void notifyDetailed(Long userId, UpdateResult result) {
        log.debug("Отправка детализированного уведомления пользователю {}", userId);
        webSocketController.sendDetailUpdateStatus(userId, result);
    }
}
