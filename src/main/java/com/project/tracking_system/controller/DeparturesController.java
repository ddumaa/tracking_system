package com.project.tracking_system.controller;

import com.project.tracking_system.dto.TrackViewResult;
import com.project.tracking_system.dto.TrackParcelDTO;
import com.project.tracking_system.dto.BulkUpdateButtonDTO;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.dto.TrackUpdateResponse;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.service.track.StatusTrackService;
import com.project.tracking_system.service.track.TrackParcelService;
import com.project.tracking_system.service.track.TrackFacade;

import com.project.tracking_system.service.track.TrackViewService;
import com.project.tracking_system.service.store.StoreService;
import com.project.tracking_system.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.project.tracking_system.utils.ResponseBuilder;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

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
@RequestMapping("/app/departures")
public class DeparturesController {

    private final TrackParcelService trackParcelService;
    private final TrackFacade trackFacade;
    private final StatusTrackService statusTrackService;
    private final StoreService storeService;
    private final WebSocketController webSocketController;
    private final UserService userService;
    private final TrackViewService trackViewService;

    /**
     * Метод для отображения списка отслеживаемых посылок пользователя с возможностью фильтрации по магазину и статусу.
     *
     * @param storeId      (опционально) ID магазина, если нужно показать посылки только из одного магазина.
     * @param statusString строковое представление статуса для фильтрации.
     * @param query        строка поиска по номеру посылки или телефону.
     * @param page         номер страницы для пагинации.
     * @param size         размер страницы.
     * @param model        модель для передачи данных на представление.
     * @param user         текущий пользователь.
     * @return имя представления для отображения истории.
     */
    @GetMapping()
    public String departures(
            @RequestParam(required = false) Long storeId,  // Фильтр по магазину
            @RequestParam(value = "status", required = false) String statusString, // Фильтр по статусу
            @RequestParam(value = "query", required = false) String query, // Поиск по номеру или телефону
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            Model model,
            @AuthenticationPrincipal User user) {

        Long userId = user.getId();
        List<Store> stores = storeService.getUserStores(userId); // Загружаем магазины с именами
        List<Long> storeIds = storeService.getUserStoreIds(userId); // Все id магазины пользователя

        // Если у пользователя **только 1 магазин**, но он явно выбрал "Все магазины", не заменяем storeId
        if (storeIds.size() == 1 && storeId == null) {
            storeId = storeIds.get(0);
        }

        // Если **фильтр магазина НЕ установлен**, загружаем все магазины пользователя
        List<Long> filteredStoreIds = (storeId != null) ? List.of(storeId) : storeIds;

        log.debug("📦 Запрос на отображение отправлений: userId={}, storeId={}, storeIds={}", userId, storeId, filteredStoreIds);

        // Определяем статус посылки (если передан)
        GlobalStatus status = null;
        if (statusString != null && !statusString.isEmpty()) {
            try {
                status = GlobalStatus.valueOf(statusString);
            } catch (IllegalArgumentException e) {
                log.warn("Некорректный статус посылки: {}", statusString);
                model.addAttribute("trackParcelNotification", "Неверный статус посылки");
                return "app/departures";
            }
        }

        // Определяем начальную страницу (избегаем выхода за границы)
        page = Math.max(page, 0);

        // Загружаем посылки с учётом статуса, магазина и параметра поиска
        Page<TrackParcelDTO> trackParcelPage;
        if (query != null && !query.isBlank()) {
            trackParcelPage = trackParcelService.searchByNumberOrPhone(
                    filteredStoreIds, status, query.trim(), page, size, userId);
        } else if (status != null) {
            trackParcelPage = trackParcelService.findByStoreTracksAndStatus(
                    filteredStoreIds, status, page, size, userId);
        } else {
            trackParcelPage = trackParcelService.findByStoreTracks(
                    filteredStoreIds, page, size, userId);
        }

        // Если запрошенная страница больше допустимой, загружаем первую страницу
        if (page >= trackParcelPage.getTotalPages() && trackParcelPage.getTotalPages() > 0) {
            log.warn("⚠ Выход за пределы страниц, сброс страницы на 0 для userId={} и storeId={}", userId, storeId);

            // Повторный запрос только если нужно сбросить страницу
            page = 0;
            if (query != null && !query.isBlank()) {
                trackParcelPage = trackParcelService.searchByNumberOrPhone(
                        filteredStoreIds, status, query.trim(), page, size, userId);
            } else if (status != null) {
                trackParcelPage = trackParcelService.findByStoreTracksAndStatus(
                        filteredStoreIds, status, page, size, userId);
            } else {
                trackParcelPage = trackParcelService.findByStoreTracks(
                        filteredStoreIds, page, size, userId);
            }
        }

        // Добавляем иконки в DTO перед передачей в шаблон
        trackParcelPage.forEach(dto -> {
            GlobalStatus statusEnum = GlobalStatus.fromDescription(dto.getStatus()); // Конвертация строки в Enum
            dto.setIconHtml(statusTrackService.getIcon(statusEnum)); // Передаем Enum в сервис для получения иконки
        });

        log.debug("Передача атрибутов в модель: stores={}, storeId={}, trackParcelDTO={}, currentPage={}, totalPages={}, size={}", stores, storeId, trackParcelPage.getContent(), trackParcelPage.getNumber(), trackParcelPage.getTotalPages(), size);

        // Добавляем атрибуты в модель
        model.addAttribute("stores", stores);
        model.addAttribute("storeId", storeId != null ? storeId : ""); // Если null, передаем пустую строку
        model.addAttribute("size", size);
        model.addAttribute("trackParcelDTO", trackParcelPage.getContent());
        model.addAttribute("statusString", statusString);
        model.addAttribute("query", query);
        model.addAttribute("currentPage", trackParcelPage.getNumber());
        model.addAttribute("totalPages", trackParcelPage.getTotalPages());
        model.addAttribute("trackParcelNotification", trackParcelPage.isEmpty() ? "Отслеживаемых посылок нет" : null);
        model.addAttribute("bulkUpdateButtonDTO",
                new BulkUpdateButtonDTO(userService.isShowBulkUpdateButton(user.getId())));

        return "app/departures";
    }

    /**
     * Метод для отображения подробной информации о посылке.
     *
     * @param model      модель для передачи данных на представление.
     * @param itemNumber номер отслеживаемой посылки.
     * @param user       текущий пользователь.
     * @return имя частичного представления с информацией о посылке.
     */
    @GetMapping("/{itemNumber}")
    public String departures(
            Model model,
            @PathVariable("itemNumber") String itemNumber,
            @AuthenticationPrincipal User user) {

        Long userId = user.getId();
        log.info("🔍 Запрос информации о посылке {} для пользователя ID={}", itemNumber, userId);

        TrackViewResult result = trackViewService.getTrackDetails(itemNumber, userId);
        model.addAttribute("trackInfo", result.trackInfo());
        model.addAttribute("itemNumber", itemNumber);
        if (result.nextUpdateTime() != null) {
            model.addAttribute("nextUpdateTime", result.nextUpdateTime());
        }

        return "partials/track-info-departures";
    }

    /**
     * Метод для обновления истории отслеживания посылок пользователя.
     *
     * @return Перенаправление на страницу истории.
     */
    @PostMapping("/track-update")
    public ResponseEntity<?> updateDepartures(
            @RequestParam(required = false) List<String> selectedNumbers,
            @AuthenticationPrincipal User user
    ) {
        Long userId = user.getId();
        log.info("🔄 Запрос на обновление посылок: userId={}", userId);

        TrackUpdateResponse result;
        try {
            if (selectedNumbers != null && !selectedNumbers.isEmpty()) {
                result = trackFacade.updateSelectedParcels(userId, selectedNumbers);
            } else {
                result = trackFacade.updateAllParcels(userId);
            }

            // Отправляем WebSocket-уведомление
            webSocketController.sendUpdateStatus(userId, result.message(), result.readyToUpdate() > 0);
            return ResponseBuilder.ok(result);

        } catch (Exception e) {
            log.error("❌ Ошибка при обновлении посылок: userId={}, ошибка={}", userId, e.getMessage(), e);
            webSocketController.sendUpdateStatus(userId, "Произошла ошибка обновления.", false);
            return ResponseBuilder.error(HttpStatus.INTERNAL_SERVER_ERROR, "Ошибка обновления посылок");
        }
    }

    /**
     * Метод для удаления выбранных посылок.
     * <p>
     * Удаляются посылки, выбранные пользователем в интерфейсе. В случае успеха отображается сообщение об успешном удалении.
     * </p>
     *
     * @param selectedNumbers список номеров посылок, которые нужно удалить.
     * @param user            текущий пользователь
     * @return перенаправление на страницу истории.
     */
    @PostMapping("/delete-selected")
    public ResponseEntity<?> deleteSelected(
            @RequestParam List<String> selectedNumbers,
            @AuthenticationPrincipal User user) {
        Long userId = user.getId();
        log.info("Запрос на удаление посылок {} для пользователя с ID: {}", selectedNumbers, userId);

        if (selectedNumbers == null || selectedNumbers.isEmpty()) {
            log.warn("Попытка удаления без выбранных посылок пользователем с ID: {}", userId);
            return ResponseBuilder.error(HttpStatus.BAD_REQUEST, "Ошибка: Не выбраны посылки для удаления.");
        }

        try {
            trackFacade.deleteByNumbersAndUserId(selectedNumbers, userId);
            log.info("Выбранные посылки {} удалены пользователем с ID: {}", selectedNumbers, userId);
            webSocketController.sendUpdateStatus(userId, "Выбранные посылки успешно удалены.", true);
            return ResponseBuilder.ok("Выбранные посылки успешно удалены.");
        } catch (EntityNotFoundException ex) {
            log.warn("Попытка удалить несуществующие посылки пользователем {}", userId);
            webSocketController.sendUpdateStatus(userId, ex.getMessage(), false);
            return ResponseBuilder.error(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (Exception e) {
            log.error("Ошибка при удалении посылок {} пользователем с ID: {}: {}", selectedNumbers, userId, e.getMessage(), e);
            webSocketController.sendUpdateStatus(userId, "Ошибка при удалении посылок.", false);
            return ResponseBuilder.error(HttpStatus.INTERNAL_SERVER_ERROR, "Ошибка при удалении посылок.");
        }
    }

}
