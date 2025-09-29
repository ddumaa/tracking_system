package com.project.tracking_system.controller;

import com.project.tracking_system.dto.TrackDetailsDto;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.service.track.TrackViewService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST-контроллер для выдачи сохранённой информации о треках.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tracks")
public class TrackController {

    private final TrackViewService trackViewService;

    /**
     * Возвращает информацию о треке по его идентификатору.
     *
     * @param id   идентификатор посылки
     * @param user текущий пользователь
     * @return DTO с подробностями трека
     */
    @GetMapping("/{id}")
    public TrackDetailsDto getTrack(@PathVariable Long id, @AuthenticationPrincipal User user) {
        if (user == null) {
            throw new AccessDeniedException("Пользователь не авторизован");
        }
        return trackViewService.getTrackDetails(id, user.getId());
    }
}

