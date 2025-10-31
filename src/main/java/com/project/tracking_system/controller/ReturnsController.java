package com.project.tracking_system.controller;

import com.project.tracking_system.dto.AvailableActionsDto;
import com.project.tracking_system.dto.CommandDto;
import com.project.tracking_system.dto.RequestDto;
import com.project.tracking_system.dto.ReturnRegistrationRequest;
import com.project.tracking_system.entity.OrderReturnRequest;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.service.order.OrderReturnRequestService;
import com.project.tracking_system.service.order.ReturnRequestCommandService;
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
    private final ReturnRequestCommandService returnRequestCommandService;

    /**
     * Возвращает карточку заявки на возврат.
     *
     * @param id   идентификатор заявки
     * @param user текущий пользователь
     * @return DTO заявки
     */
    @GetMapping("/{id}")
    public RequestDto getReturnRequest(@PathVariable Long id,
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
     * @return DTO с кодами доступных действий
     */
    @GetMapping("/{id}/available-actions")
    public AvailableActionsDto getAvailableActions(@PathVariable Long id,
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
    public RequestDto registerReturn(@RequestBody @Valid ReturnRegistrationRequest request,
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
    public RequestDto executeCommand(@PathVariable Long id,
                                      @RequestBody @Valid CommandDto command,
                                      @AuthenticationPrincipal User user) {
        ensureAuthenticated(user);
        ReturnRequestCommandType commandType = ReturnRequestCommandType.fromCode(command.action());
        if (commandType == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неизвестная команда управления заявкой");
        }
        ZoneId userZone = resolveUserZone(user);
        try {
            return returnRequestCommandService.executeCommand(id, commandType, command, user, userZone);
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
