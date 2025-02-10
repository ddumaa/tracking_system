package com.project.tracking_system.controller;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.dto.TrackParcelDTO;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.model.GlobalStatus;
import com.project.tracking_system.service.StatusTrackService;
import com.project.tracking_system.service.TypeDefinitionTrackPostService;
import com.project.tracking_system.service.TrackParcelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            return "redirect:/login"; // Перенаправление, если пользователь не аутентифицирован
        }

        Long userId = user.getId();
        log.info("Запрос на отображение отправлений для пользователя с ID: {}", userId);

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
    public String updateDepartures(
            @RequestParam(required = false) List<String> selectedNumbers,
            RedirectAttributes redirectAttributes,
            Authentication authentication) {

        if (!(authentication instanceof UsernamePasswordAuthenticationToken auth)
                || !(auth.getPrincipal() instanceof User user)) {
            log.warn("Попытка обновления посылок без аутентификации.");
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка: Необходимо войти в систему.");
            return "redirect:/login";
        }

        Long userId = user.getId();
        log.info("Запрос на обновление посылок для пользователя с ID: {}", userId);

        try {
            if (selectedNumbers != null && !selectedNumbers.isEmpty()) {
                trackParcelService.updateSelectedParcels(userId, selectedNumbers);
                log.info("Выбранные посылки {} обновлены для пользователя с ID: {}", selectedNumbers, userId);
                redirectAttributes.addFlashAttribute("successMessage", "Выбранные посылки успешно обновлены.");
            } else {
                // Здесь потенциально вылетает AccessDeniedException
                trackParcelService.updateHistory(userId);
                log.info("Обновлены все посылки для пользователя с ID: {}", userId);
                redirectAttributes.addFlashAttribute("successMessage", "Все посылки успешно обновлены.");
            }
        } catch (IllegalStateException e) {
            // Ловим исчерпанный лимит
            log.warn("Ошибка бизнес-логики (лимит) для пользователя {}: {}", userId, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (AccessDeniedException e) {
            // Ловим "Только для платных пользователей"
            log.warn("Отказано в доступе для пользователя {}: {}", userId, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            // Ловим все прочие неожиданности
            log.error("Непредвиденная ошибка для пользователя {}", userId, e);
            redirectAttributes.addFlashAttribute("errorMessage", "Произошла непредвиденная ошибка: " + e.getMessage());
        }

        return "redirect:/departures";
    }

    @GetMapping("/update-status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkUpdateStatus(Authentication authentication) {
        if (!(authentication instanceof UsernamePasswordAuthenticationToken auth) || !(auth.getPrincipal() instanceof User user)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "completed", true,
                    "errorMessage", "Ошибка: Необходимо войти в систему."
            ));
        }

        Long userId = user.getId();
        boolean isCompleted = trackParcelService.isUpdateCompleted(userId);
        String errorMessage = trackParcelService.getLastErrorMessage(userId);

        log.debug("Проверка статуса обновления для пользователя {}: completed={}, errorMessage={}", userId, isCompleted, errorMessage);

        Map<String, Object> response = new HashMap<>();
        response.put("completed", isCompleted);

        if (errorMessage != null) {
            response.put("errorMessage", errorMessage);
        }

        return ResponseEntity.ok(response);
    }

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
    public String deleteSelected(
            @RequestParam List<String> selectedNumbers,
            RedirectAttributes redirectAttributes,
            Authentication authentication) {

        if (!(authentication instanceof UsernamePasswordAuthenticationToken auth) || !(auth.getPrincipal() instanceof User user)) {
            log.warn("Попытка удаления посылок без аутентификации.");
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка: Необходимо войти в систему.");
            return "redirect:/login";
        }

        Long userId = user.getId();
        log.info("Запрос на удаление посылок {} для пользователя с ID: {}", selectedNumbers, userId);

        if (selectedNumbers == null || selectedNumbers.isEmpty()) {
            log.warn("Попытка удаления без выбранных посылок пользователем с ID: {}", userId);
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка: Не выбраны посылки для удаления.");
            return "redirect:/departures";
        }

        try {
            trackParcelService.deleteByNumbersAndUserId(selectedNumbers, userId);
            log.info("Выбранные посылки {} удалены пользователем с ID: {}", selectedNumbers, userId);
            redirectAttributes.addFlashAttribute("successMessage", "Выбранные посылки успешно удалены.");
        } catch (Exception e) {
            log.error("Ошибка при удалении посылок {} пользователем с ID: {}: {}", selectedNumbers, userId, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка при удалении посылок: " + e.getMessage());
        }

        return "redirect:/departures";
    }

}