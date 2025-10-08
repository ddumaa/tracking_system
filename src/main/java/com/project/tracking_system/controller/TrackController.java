package com.project.tracking_system.controller;

import com.project.tracking_system.dto.ActionRequiredReturnRequestDto;
import com.project.tracking_system.dto.ExchangeApprovalResponse;
import com.project.tracking_system.dto.ReturnRegistrationRequest;
import com.project.tracking_system.dto.ReturnRequestReverseTrackUpdateRequest;
import com.project.tracking_system.dto.ReturnRequestActionResponse;
import com.project.tracking_system.dto.TrackChainItemDto;
import com.project.tracking_system.dto.TrackDetailsDto;
import com.project.tracking_system.dto.TrackNumberUpdateRequest;
import com.project.tracking_system.dto.TrackNumberUpdateResponse;
import com.project.tracking_system.dto.TrackParcelDTO;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.OrderReturnRequest;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.service.order.ExchangeApprovalResult;
import com.project.tracking_system.service.order.OrderReturnRequestService;
import com.project.tracking_system.service.order.ReturnRequestActionMapper;
import com.project.tracking_system.service.track.TrackParcelService;
import com.project.tracking_system.service.track.TrackViewService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.project.tracking_system.exception.TrackNumberAlreadyExistsException;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
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
    private final OrderReturnRequestService orderReturnRequestService;
    private final ReturnRequestActionMapper returnRequestActionMapper;

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

    /**
     * Регистрирует заявку на возврат/обмен для посылки.
     */
    @PostMapping("/{id}/returns")
    public TrackDetailsDto registerReturn(@PathVariable Long id,
                                          @RequestBody @Valid ReturnRegistrationRequest request,
                                          @AuthenticationPrincipal User user) {
        if (user == null) {
            throw new AccessDeniedException("Пользователь не авторизован");
        }
        try {
            ZonedDateTime requestedAt = request.requestedAt().atZoneSameInstant(ZoneOffset.UTC);
            orderReturnRequestService.registerReturn(
                    id,
                    user,
                    request.idempotencyKey(),
                    request.reason(),
                    request.comment(),
                    requestedAt,
                    request.reverseTrackNumber(),
                    request.isExchange()
            );
            return trackViewService.getTrackDetails(id, user.getId());
        } catch (AccessDeniedException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        }
    }

    /**
     * Одобряет запуск обмена по зарегистрированной заявке.
     */
    @PostMapping("/{id}/returns/{requestId}/exchange")
    public ExchangeApprovalResponse approveExchange(@PathVariable Long id,
                                                    @PathVariable Long requestId,
                                                    @AuthenticationPrincipal User user) {
        if (user == null) {
            throw new AccessDeniedException("Пользователь не авторизован");
        }
        try {
            ExchangeApprovalResult result = orderReturnRequestService.approveExchange(requestId, id, user);
            TrackDetailsDto details = trackViewService.getTrackDetails(id, user.getId());
            TrackChainItemDto replacement = trackViewService.toChainItem(result.exchangeParcel(), id);
            return new ExchangeApprovalResponse(details, replacement);
        } catch (AccessDeniedException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        }
    }

    /**
     * Закрывает заявку без запуска обмена.
     */
    @PostMapping("/{id}/returns/{requestId}/close")
    public TrackDetailsDto closeWithoutExchange(@PathVariable Long id,
                                                @PathVariable Long requestId,
                                                @AuthenticationPrincipal User user) {
        if (user == null) {
            throw new AccessDeniedException("Пользователь не авторизован");
        }
        try {
            orderReturnRequestService.closeWithoutExchange(requestId, id, user);
            return trackViewService.getTrackDetails(id, user.getId());
        } catch (AccessDeniedException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        }
    }

    /**
     * Обновляет обратный трек и комментарий активной заявки на возврат.
     * <p>
     * Метод проверяет авторизацию пользователя, делегирует бизнес-проверки
     * сервису заявок, а затем возвращает свежие детали трека для перерисовки модалки.
     * </p>
     *
     * @param id        идентификатор посылки
     * @param requestId идентификатор заявки на возврат
     * @param request   тело запроса с изменёнными полями
     * @param user      текущий пользователь
     * @return обновлённые данные трека с заявкой
     */
    @RequestMapping(path = "/{id}/returns/{requestId}/reverse-track", method = {
            RequestMethod.PATCH,
            RequestMethod.PUT
    })
    public TrackDetailsDto updateReverseTrack(@PathVariable Long id,
                                              @PathVariable Long requestId,
                                              @RequestBody @Valid ReturnRequestReverseTrackUpdateRequest request,
                                              @AuthenticationPrincipal User user) {
        if (user == null) {
            throw new AccessDeniedException("Пользователь не авторизован");
        }
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не переданы данные для обновления");
        }
        try {
            orderReturnRequestService.updateReverseTrackAndComment(
                    requestId,
                    id,
                    user,
                    request.reverseTrackNumber(),
                    request.comment()
            );
            return trackViewService.getTrackDetails(id, user.getId());
        } catch (AccessDeniedException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        }
    }

    /**
     * Переводит одобренный обмен обратно в статус возврата.
     */
    @PostMapping("/{id}/returns/{requestId}/reopen")
    public ReturnRequestActionResponse reopenExchange(@PathVariable Long id,
                                                      @PathVariable Long requestId,
                                                      @AuthenticationPrincipal User user) {
        if (user == null) {
            throw new AccessDeniedException("Пользователь не авторизован");
        }
        try {
            OrderReturnRequest updated = orderReturnRequestService.reopenAsReturn(requestId, id, user);
            TrackDetailsDto details = trackViewService.getTrackDetails(id, user.getId());
            ZoneId zone = resolveZone(details);
            ActionRequiredReturnRequestDto action = returnRequestActionMapper.toDto(updated, zone);
            return new ReturnRequestActionResponse(details, action);
        } catch (AccessDeniedException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        }
    }

    /**
     * Отменяет обменную заявку и закрывает её без запуска нового отправления.
     */
    @PostMapping("/{id}/returns/{requestId}/cancel")
    public ReturnRequestActionResponse cancelExchange(@PathVariable Long id,
                                                      @PathVariable Long requestId,
                                                      @AuthenticationPrincipal User user) {
        if (user == null) {
            throw new AccessDeniedException("Пользователь не авторизован");
        }
        try {
            OrderReturnRequest updated = orderReturnRequestService.cancelExchange(requestId, id, user);
            TrackDetailsDto details = trackViewService.getTrackDetails(id, user.getId());
            ZoneId zone = resolveZone(details);
            ActionRequiredReturnRequestDto action = updated.requiresAction()
                    ? returnRequestActionMapper.toDto(updated, zone)
                    : null;
            return new ReturnRequestActionResponse(details, action);
        } catch (AccessDeniedException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        }
    }

    /**
     * Возвращает временную зону пользователя из DTO модалки.
     */
    private ZoneId resolveZone(TrackDetailsDto details) {
        String timeZone = details != null ? details.timeZone() : null;
        if (timeZone == null || timeZone.isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(timeZone);
        } catch (Exception ex) {
            return ZoneOffset.UTC;
        }
    }
}

