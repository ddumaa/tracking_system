package com.project.tracking_system.controller;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.dto.TrackParcelDTO;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.model.GlobalStatus;
import com.project.tracking_system.service.StatusTrackService;
import com.project.tracking_system.service.TypeDefinitionTrackPostService;
import com.project.tracking_system.service.TrackParcelService;
import com.project.tracking_system.service.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
@Controller
@RequestMapping("/departures")
public class DeparturesController {

    private final TrackParcelService trackParcelService;
    private final StatusTrackService statusTrackService;
    private final TypeDefinitionTrackPostService typeDefinitionTrackPostService;
    private final UserService userService;

    /**
     * Конструктор для инициализации зависимостей контроллера.
     *
     * @param trackParcelService сервис для работы с посылками.
     * @param statusTrackService сервис для работы со статусами посылок.
     * @param typeDefinitionTrackPostService сервис для определения типа отслеживания.
     * @param userService сервис для работы с пользователями.
     */
    @Autowired
    public DeparturesController(TrackParcelService trackParcelService, StatusTrackService statusTrackService,
                                TypeDefinitionTrackPostService typeDefinitionTrackPostService, UserService userService) {
        this.trackParcelService = trackParcelService;
        this.typeDefinitionTrackPostService = typeDefinitionTrackPostService;
        this.statusTrackService = statusTrackService;
        this.userService = userService;
    }

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
            Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String authUserName = auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken) ? auth.getName() : null;

        Page<TrackParcelDTO> trackParcelPage;
        GlobalStatus status = null;
        page = Math.max(page, 0);
        if (statusString != null && !statusString.isEmpty()) {
            try {
                status = GlobalStatus.valueOf(statusString);
            } catch (IllegalArgumentException e) {
                model.addAttribute("trackParcelNotification", "Неверный статус посылки");
                return "departures";
            }
        }
        if (status != null) {
            trackParcelPage = trackParcelService.findByUserTracksAndStatus(authUserName, status, page, size);
        } else {
            trackParcelPage = trackParcelService.findByUserTracks(authUserName, page, size);
        }
        if (page >= trackParcelPage.getTotalPages() && trackParcelPage.getTotalPages() > 0) {
            page = 0;
            if (status != null) {
                trackParcelPage = trackParcelService.findByUserTracksAndStatus(authUserName, status, page, size);
            } else {
                trackParcelPage = trackParcelService.findByUserTracks(authUserName, page, size);
            }
        }
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
    public String departures(Model model, @PathVariable("itemNumber") String itemNumber) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new RuntimeException("Пользователь не аутентифицирован.");
        }

        User user = (User) auth.getPrincipal();
        TrackInfoListDTO trackInfo = typeDefinitionTrackPostService.getTypeDefinitionTrackPostService(user, itemNumber);

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
    public String updateDepartures(@RequestParam(required = false) List<String> selectedNumbers, RedirectAttributes redirectAttributes) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            User user = (User) auth.getPrincipal();

            if (selectedNumbers != null && !selectedNumbers.isEmpty()) {
                trackParcelService.updateSelectedParcels(user, selectedNumbers);
                redirectAttributes.addFlashAttribute("successMessage", "Выбранные посылки успешно обновлены.");
            } else {
                trackParcelService.updateHistory(user);
                redirectAttributes.addFlashAttribute("successMessage", "Все посылки успешно обновлены.");
            }
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка: Не удалось обновить посылки.");
        }
        return "redirect:/departures";
    }

    @GetMapping("/update-status")
    @ResponseBody
    public ResponseEntity<Map<String, Boolean>> checkUpdateStatus() {
        boolean isCompleted = trackParcelService.isUpdateCompleted();
        return ResponseEntity.ok(Collections.singletonMap("completed", isCompleted));
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
    public String deleteSelected(@RequestParam List<String> selectedNumbers, RedirectAttributes redirectAttributes) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String authUserName = (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken))
                    ? auth.getName() : null;

            Optional<User> byUser = userService.findByUser(authUserName);
            if (byUser.isPresent()) {
                Long userId = byUser.get().getId();
                trackParcelService.deleteByNumbersAndUserId(selectedNumbers, userId);
                redirectAttributes.addFlashAttribute("successMessage", "Выбранные посылки успешно удалены.");
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Пользователь не найден.");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Ошибка при удалении посылок: " + e.getMessage());
        }
        return "redirect:/departures";
    }

}