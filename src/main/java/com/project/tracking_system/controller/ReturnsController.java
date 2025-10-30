package com.project.tracking_system.controller;

import com.project.tracking_system.dto.ReturnRegistrationRequest;
import com.project.tracking_system.dto.ReturnRequestAvailableActionsDto;
import com.project.tracking_system.dto.ReturnRequestCommandRequest;
import com.project.tracking_system.dto.ReturnRequestDto;
import com.project.tracking_system.entity.OrderReturnRequest;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.service.order.OrderReturnRequestService;
import com.project.tracking_system.service.order.ReturnRequestMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * REST-контроллер управления заявками на возврат и обмен.
 * <p>
 * Контроллер инкапсулирует работу с сервисом заявок, предоставляя единый API
 * для веб-клиента и Telegram без зависимости от DTO трека (SRP).
 * </p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/returns")
public class ReturnsController {

    private final OrderReturnRequestService orderReturnRequestService;
    private final ReturnRequestMapper returnRequestMapper;

    /**
     * Возвращает карточку заявки на возврат.
     *
     * @param id   идентификатор заявки
     * @param user текущий пользователь
     * @return DTO заявки
     */
    @GetMapping("/{id}")
    public ReturnRequestDto getReturnRequest(@PathVariable Long id,
                                             @AuthenticationPrincipal User user) {
        ensureAuthenticated(user);
        OrderReturnRequest request = loadOwnedRequest(id, user);
        return returnRequestMapper.toDto(request, resolveUserZone(user));
    }

    /**
     * Возвращает доступные действия по заявке.
     *
     * @param id   идентификатор заявки
     * @param user текущий пользователь
     * @return DTO с флагами доступных действий
     */
    @GetMapping("/{id}/available-actions")
    public ReturnRequestAvailableActionsDto getAvailableActions(@PathVariable Long id,
                                                                @AuthenticationPrincipal User user) {
        ensureAuthenticated(user);
        OrderReturnRequest request = loadOwnedRequest(id, user);
        return returnRequestMapper.toAvailableActions(request);
    }

    /**
     * Регистрирует новую заявку на возврат или обмен.
     *
     * @param request тело запроса
     * @param user    текущий пользователь
     * @return DTO созданной или ранее существующей заявки
     */
    @PostMapping
    public ReturnRequestDto registerReturn(@RequestBody @Valid ReturnRegistrationRequest request,
                                           @AuthenticationPrincipal User user) {
        ensureAuthenticated(user);
        try {
            ZonedDateTime requestedAtUtc = request.requestedAt().atZoneSameInstant(ZoneOffset.UTC);
            OrderReturnRequest saved = orderReturnRequestService.registerReturn(
                    request.parcelId(),
                    user,
                    request.idempotencyKey(),
                    request.reason(),
                    request.comment(),
                    requestedAtUtc,
                    request.reverseTrackNumber(),
                    request.isExchange()
            );
            return returnRequestMapper.toDto(saved, resolveUserZone(user));
        } catch (AccessDeniedException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        }
    }

    /**
     * Выполняет команду над заявкой и возвращает обновлённое состояние.
     *
     * @param id      идентификатор заявки
     * @param command тело запроса с кодом команды
     * @param user    текущий пользователь
     * @return обновлённый DTO заявки
     */
    @PostMapping("/{id}/commands")
    public ReturnRequestDto executeCommand(@PathVariable Long id,
                                           @RequestBody @Valid ReturnRequestCommandRequest command,
                                           @AuthenticationPrincipal User user) {
        ensureAuthenticated(user);
        OrderReturnRequest request = loadOwnedRequest(id, user);
        ReturnRequestCommandType commandType = ReturnRequestCommandType.fromCode(command.command());
        if (commandType == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неизвестная команда управления заявкой");
        }
        Long parcelId = resolveParcelId(request);
        ZoneId userZone = resolveUserZone(user);
        try {
            return switch (commandType) {
                case START_EXCHANGE -> map(orderReturnRequestService.approveExchange(id, parcelId, user), userZone);
                case CREATE_EXCHANGE_PARCEL -> {
                    orderReturnRequestService.createExchangeParcel(id, parcelId, user);
                    yield map(orderReturnRequestService.getOwnedRequest(id, user), userZone);
                }
                case CLOSE -> map(orderReturnRequestService.closeWithoutExchange(id, parcelId, user), userZone);
                case CONFIRM_RECEIPT -> map(orderReturnRequestService.confirmReturnProcessing(id, parcelId, user), userZone);
                case UPDATE_DETAILS -> {
                    orderReturnRequestService.updateReverseTrackAndComment(
                            id,
                            parcelId,
                            user,
                            command.reverseTrackNumber(),
                            command.comment()
                    );
                    yield map(orderReturnRequestService.getOwnedRequest(id, user), userZone);
                }
                case REOPEN -> map(orderReturnRequestService.reopenAsReturn(id, parcelId, user), userZone);
                case CANCEL_EXCHANGE -> map(orderReturnRequestService.cancelExchange(id, parcelId, user), userZone);
            };
        } catch (AccessDeniedException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        }
    }

    /**
     * Проверяет, что пользователь авторизован.
     */
    private void ensureAuthenticated(User user) {
        if (user == null) {
            throw new AccessDeniedException("Пользователь не авторизован");
        }
    }

    /**
     * Загружает заявку с проверкой принадлежности пользователю.
     */
    private OrderReturnRequest loadOwnedRequest(Long id, User user) {
        try {
            return orderReturnRequestService.getOwnedRequest(id, user);
        } catch (IllegalArgumentException ex) {
            String message = ex.getMessage();
            if (message != null && message.toLowerCase().contains("не найд")) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, message, ex);
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message, ex);
        }
    }

    /**
     * Преобразует сущность заявки в DTO.
     */
    private ReturnRequestDto map(OrderReturnRequest request, ZoneId userZone) {
        return returnRequestMapper.toDto(request, userZone);
    }

    /**
     * Определяет идентификатор посылки, связанной с заявкой.
     */
    private Long resolveParcelId(OrderReturnRequest request) {
        TrackParcel parcel = request.getParcel();
        if (parcel == null || parcel.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Заявка не привязана к посылке");
        }
        return parcel.getId();
    }

    /**
     * Возвращает временную зону пользователя с безопасным дефолтом.
     */
    private ZoneId resolveUserZone(User user) {
        String timeZone = user != null ? user.getTimeZone() : null;
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
