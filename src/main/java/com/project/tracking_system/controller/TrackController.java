package com.project.tracking_system.controller;

import com.project.tracking_system.dto.TrackDetailsDto;
import com.project.tracking_system.dto.TrackNumberUpdateRequest;
import com.project.tracking_system.dto.TrackNumberUpdateResponse;
import com.project.tracking_system.dto.TrackParcelDTO;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.service.track.TrackViewService;
import com.project.tracking_system.service.track.TrackParcelService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.project.tracking_system.exception.TrackNumberAlreadyExistsException;

import java.util.Optional;

import jakarta.validation.Valid;

/**
 * REST-контроллер для выдачи сохранённой информации о треках.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tracks")
public class TrackController {

    private final TrackViewService trackViewService;
    private final TrackParcelService trackParcelService;

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

    /**
     * Обновляет трек-номер посылки в статусах PRE_REGISTERED или ERROR.
     *
     * @param id      идентификатор посылки
     * @param request данные с новым трек-номером
     * @param user    текущий пользователь
     * @return обновлённые данные для модалки и таблицы
     */
    @PatchMapping("/{id}/number")
    public TrackNumberUpdateResponse updateTrackNumber(@PathVariable Long id,
                                                       @RequestBody @Valid TrackNumberUpdateRequest request,
                                                       @AuthenticationPrincipal User user) {
        if (user == null) {
            throw new AccessDeniedException("Пользователь не авторизован");
        }
        Long userId = user.getId();

        TrackParcel parcel = resolveOwnedParcel(id, userId);
        GlobalStatus status = parcel.getStatus();
        if (status != GlobalStatus.PRE_REGISTERED && status != GlobalStatus.ERROR) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Редактирование недоступно для текущего статуса");
        }

        if (request == null || request.number() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не указан трек-номер");
        }

        TrackDetailsDto detailsBefore = trackViewService.getTrackDetails(id, userId);
        if (!detailsBefore.canEditTrack()) {
            throw new AccessDeniedException("Редактирование недоступно для текущего пользователя");
        }

        try {
            TrackParcel updated = trackParcelService.updateTrackNumber(id, userId, request.number());
            TrackDetailsDto details = trackViewService.getTrackDetails(id, userId);
            TrackParcelDTO summary = trackParcelService.mapToDto(updated, userId);
            return new TrackNumberUpdateResponse(details, summary);
        } catch (TrackNumberAlreadyExistsException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        }
    }

    /**
     * Загружает посылку и проверяет принадлежность пользователю.
     */
    private TrackParcel resolveOwnedParcel(Long id, Long userId) {
        Optional<TrackParcel> owned = trackParcelService.findOwnedById(id, userId);
        if (owned.isPresent()) {
            return owned.get();
        }
        if (trackParcelService.findById(id).isPresent()) {
            throw new AccessDeniedException("Посылка не принадлежит пользователю");
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Посылка не найдена");
    }
}

