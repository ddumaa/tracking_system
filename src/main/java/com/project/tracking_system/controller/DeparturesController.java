package com.project.tracking_system.controller;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.dto.TrackParcelDTO;
import com.project.tracking_system.entity.UpdateResult;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.model.GlobalStatus;
import com.project.tracking_system.service.StatusTrackService;
import com.project.tracking_system.service.TypeDefinitionTrackPostService;
import com.project.tracking_system.service.TrackParcelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Контроллер для отображения и управления историей отслеживания посылок пользователя.
 * <p>
 * Этот контроллер предоставляет методы для отображения списка отслеживаемых посылок, отображения подробной информации о посылке,
 * обновления статусов посылок, а также удаления выбранных посылок.
 * </p>
 *
 * @author Dmitriy Anisimov
 * @date 07.01.2025
 */
@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("/departures")
public class DeparturesController {

    private final TrackParcelService trackParcelService;
    private final StatusTrackService statusTrackService;
    private final TypeDefinitionTrackPostService typeDefinitionTrackPostService;
    private final WebSocketController webSocketController;

    /**
     * Метод для отображения списка отслеживаемых посылок пользователя с возможностью фильтрации по статусу.
     * <p>
     * Если статус посылки передан в запросе, выполняется фильтрация по этому статусу.
     * </p>
     *
     * @param statusString строковое представление статуса для фильтрации.
     * @param page номер страницы для пагинации.
     * @param size размер страницы.
     * @param model модель для передачи данных на представление.
     * @return имя представления для отображения истории.
     */
    @GetMapping()
    public String departures(
            @RequestParam(value = "status", required = false) String statusString,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            Model model,
            Authentication authentication) {

        if (!(authentication instanceof UsernamePasswordAuthenticationToken auth) || !(auth.getPrincipal() instanceof User user)) {
            log.debug("Попытка доступа к странице 'Отправления' без аутентификации.");
            return "redirect:/login";
        }

        Long userId = user.getId();
        log.debug("Запрос на отображение отправлений для пользователя с ID: {}", userId);

        // Определяем статус посылки (если передан)
        GlobalStatus status = null;
        if (statusString != null && !statusString.isEmpty()) {
            try {
                status = GlobalStatus.valueOf(statusString);
            } catch (IllegalArgumentException e) {
                log.warn("Некорректный статус посылки: {}", statusString);
                model.addAttribute("trackParcelNotification", "Неверный статус посылки");
                return "departures";
            }
        }

        // Загружаем посылки с учетом статуса
        page = Math.max(page, 0);
        Page<TrackParcelDTO> trackParcelPage = (status != null)
                ? trackParcelService.findByUserTracksAndStatus(userId, status, page, size)
                : trackParcelService.findByUserTracks(userId, page, size);

        // Если текущая страница выходит за пределы, загружаем с первой страницы
        if (page >= trackParcelPage.getTotalPages() && trackParcelPage.getTotalPages() > 0) {
            log.warn("Выход за пределы страниц, сброс страницы на 0 для пользователя с ID: {}", userId);
            page = 0;
            trackParcelPage = (status != null)
                    ? trackParcelService.findByUserTracksAndStatus(userId, status, page, size)
                    : trackParcelService.findByUserTracks(userId, page, size);
        }

        // Добавляем атрибуты в модель
        model.addAttribute("size", size);
        model.addAttribute("trackParcelDTO", trackParcelPage.getContent());
        model.addAttribute("statusString", statusString);
        model.addAttribute("currentPage", trackParcelPage.getNumber());
        model.addAttribute("totalPages", trackParcelPage.getTotalPages());
        model.addAttribute("trackParcelNotification", trackParcelPage.isEmpty() ? "Отслеживаемых посылок нет" : null);
        model.addAttribute("statusTrackService", statusTrackService);

        return "departures";
    }

    /**
     * Метод для отображения подробной информации о посылке.
     *
     * @param model модель для передачи данных на представление.
     * @param itemNumber номер отслеживаемой посылки.
     * @return имя частичного представления с информацией о посылке.
     */
    @GetMapping("/{itemNumber}")
    public String departures(Model model, @PathVariable("itemNumber") String itemNumber, Authentication authentication) {
        if (!(authentication instanceof UsernamePasswordAuthenticationToken auth) || !(auth.getPrincipal() instanceof User user)) {
            throw new RuntimeException("Пользователь не аутентифицирован.");
        }

        Long userId = user.getId();
        TrackInfoListDTO trackInfo = typeDefinitionTrackPostService.getTypeDefinitionTrackPostService(userId, itemNumber);
        log.info("🎯 Передача в шаблон: {} записей для трека {}", trackInfo.getList().size(), itemNumber);

        model.addAttribute("trackInfo", trackInfo);
        model.addAttribute("itemNumber", itemNumber);

        return "partials/track-info-departures";
    }

    /**
     * Метод для обновления истории отслеживания посылок пользователя.
     *
     * @return перенаправление на страницу истории.
     */
    @PostMapping("/track-update")
    public ResponseEntity<UpdateResult> updateDepartures(
            @RequestParam(required = false) List<String> selectedNumbers,
            Authentication authentication
    ) {
        if (!(authentication instanceof UsernamePasswordAuthenticationToken auth)
                || !(auth.getPrincipal() instanceof User user)) {
            log.warn("Попытка обновления посылок без аутентификации.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Long userId = user.getId();
        log.info("Запрос на обновление посылок для пользователя с ID: {}", userId);

        UpdateResult result;
        try {
            if (selectedNumbers != null && !selectedNumbers.isEmpty()) {
                result = trackParcelService.updateSelectedParcels(userId, selectedNumbers);
            } else {
                result = trackParcelService.updateAllParcels(userId);
            }

            // Отправляем обновление через WebSocket
            webSocketController.sendDetailUpdateStatus(userId, result);

            return ResponseEntity.ok().build();

        } catch (Exception e) {
            log.error(" Непредвиденная ошибка для пользователя {}: {}", userId, e.getMessage(), e);

            // Отправляем уведомление через WebSocket
            webSocketController.sendUpdateStatus(userId, "Произошла ошибка обновления.", false);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


//    @GetMapping("/update-status")
//    @ResponseBody
//    public ResponseEntity<Map<String, Object>> checkUpdateStatus(Authentication authentication) {
//        if (!(authentication instanceof UsernamePasswordAuthenticationToken auth) || !(auth.getPrincipal() instanceof User user)) {
//            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
//                    "completed", true,
//                    "errorMessage", "Ошибка: Необходимо войти в систему."
//            ));
//        }
//
//        Long userId = user.getId();
//        boolean isCompleted = trackParcelService.isUpdateCompleted(userId);
//        log.debug("Проверка статуса обновления для пользователя {}: completed={}, errorMessage={}", userId, isCompleted);
//
//        Map<String, Object> response = new HashMap<>();
//        response.put("completed", isCompleted);
//
//        String errorMessage = trackParcelService.getLastErrorMessage(userId);
//        if (errorMessage == null) {
//            errorMessage = "";
//        }
//        return ResponseEntity.ok(Map.of(
//                "completed", isCompleted,
//                "errorMessage", errorMessage
//        ));
//    }

    /**
     * Метод для удаления выбранных посылок.
     * <p>
     * Удаляются посылки, выбранные пользователем в интерфейсе. В случае успеха отображается сообщение об успешном удалении.
     * </p>
     *
     * @param selectedNumbers список номеров посылок, которые нужно удалить.
     * @param redirectAttributes атрибуты для передачи сообщений о результате операции.
     * @return перенаправление на страницу истории.
     */
    @PostMapping("/delete-selected")
    public ResponseEntity<String> deleteSelected(
            @RequestParam List<String> selectedNumbers,
            Authentication authentication) {

        if (!(authentication instanceof UsernamePasswordAuthenticationToken auth) || !(auth.getPrincipal() instanceof User user)) {
            log.warn("Попытка удаления посылок без аутентификации.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Ошибка: Необходимо войти в систему.");
        }

        Long userId = user.getId();
        log.info("Запрос на удаление посылок {} для пользователя с ID: {}", selectedNumbers, userId);

        if (selectedNumbers == null || selectedNumbers.isEmpty()) {
            log.warn("Попытка удаления без выбранных посылок пользователем с ID: {}", userId);
            return ResponseEntity.badRequest().body("Ошибка: Не выбраны посылки для удаления.");
        }

        try {
            trackParcelService.deleteByNumbersAndUserId(selectedNumbers, userId);
            log.info("Выбранные посылки {} удалены пользователем с ID: {}", selectedNumbers, userId);
            webSocketController.sendUpdateStatus(userId, "Выбранные посылки успешно удалены.", true);
            return ResponseEntity.ok("Выбранные посылки успешно удалены.");
        } catch (Exception e) {
            log.error("Ошибка при удалении посылок {} пользователем с ID: {}: {}", selectedNumbers, userId, e.getMessage(), e);
            webSocketController.sendUpdateStatus(userId, "Ошибка при удалении посылок.", false);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Ошибка при удалении посылок.");
        }
    }

}