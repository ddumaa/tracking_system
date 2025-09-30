package com.project.tracking_system.controller;

import com.project.tracking_system.dto.TrackDetailsDto;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.service.track.TrackRefreshService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST-контроллер для ручного обновления одного трека из модального окна.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tracks")
public class TrackRefreshController {

    private final TrackRefreshService trackRefreshService;

    /**
     * Запускает обновление трека и возвращает свежие данные для модального окна.
     *
     * @param id   идентификатор посылки
     * @param user текущий пользователь
     * @return DTO с обновлёнными данными
     */
    @PostMapping("/{id}/refresh")
    public TrackDetailsDto refresh(@PathVariable Long id, @AuthenticationPrincipal User user) {
        if (user == null) {
            throw new AccessDeniedException("Пользователь не авторизован");
        }
        return trackRefreshService.refreshTrack(id, user.getId());
    }
}
