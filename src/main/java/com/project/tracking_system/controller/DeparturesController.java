package com.project.tracking_system.controller;

import com.project.tracking_system.dto.BulkUpdateButtonDTO;
import com.project.tracking_system.dto.TrackParcelDTO;
import com.project.tracking_system.dto.ActionRequiredReturnRequestDto;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.dto.TrackUpdateResponse;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.OrderReturnRequest;
import com.project.tracking_system.entity.OrderReturnRequestStatus;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.service.track.StatusTrackService;
import com.project.tracking_system.service.track.TrackParcelService;
import com.project.tracking_system.service.track.TrackFacade;

import com.project.tracking_system.service.store.StoreService;
import com.project.tracking_system.service.user.UserService;
import com.project.tracking_system.service.order.OrderReturnRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.project.tracking_system.utils.ResponseBuilder;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.project.tracking_system.exception.TrackNumberAlreadyExistsException;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.Objects;
import java.util.stream.Collectors;

import com.project.tracking_system.utils.PaginationItem;
import com.project.tracking_system.utils.PaginationUtils;
import com.project.tracking_system.utils.PaginationWindow;

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
    private final OrderReturnRequestService orderReturnRequestService;

    /**
     * Максимальное количество ссылок пагинации, отображаемых одновременно.
     * Помогает избежать длинных списков страниц на экране.
     */
    private static final int PAGE_WINDOW = 5;

    /**
     * Форматер для отображения дат заявок на вкладке «Требуют действия».
     */
    private static final DateTimeFormatter REQUEST_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    /**
     * Метод для отображения списка отслеживаемых посылок пользователя с
     * возможностью фильтрации по магазину, статусу и сортировки по дате.
     *
     * @param storeId      (опционально) ID магазина, если нужно показать посылки только из одного магазина.
     * @param statusString строковое представление статуса для фильтрации.
     * @param query        строка поиска по номеру посылки или телефону.
     * @param page         номер страницы для пагинации.
     * @param size         размер страницы.
     * @param sortOrder    порядок сортировки по дате (asc/desc).
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
            @RequestParam(value = "sortOrder", defaultValue = "desc") String sortOrder,
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

        int requestedPage = Math.max(page, 0);
        String normalizedQuery = query != null ? query.trim() : null;

        // Загружаем посылки с учётом статуса, магазина и параметра поиска
        Page<TrackParcelDTO> trackParcelPage = loadTrackParcelPage(
                filteredStoreIds, status, normalizedQuery, requestedPage, size, userId, sortOrder);

        int totalPages = trackParcelPage.getTotalPages();
        PaginationWindow paginationWindow = PaginationUtils.calculateWindow(requestedPage, totalPages, PAGE_WINDOW);

        if (totalPages > 0 && paginationWindow.currentPage() != trackParcelPage.getNumber()) {
            log.warn("⚠ Запрошена недоступная страница {}, переключаемся на {} для userId={} и storeId={}",
                    requestedPage, paginationWindow.currentPage(), userId, storeId);

            trackParcelPage = loadTrackParcelPage(
                    filteredStoreIds, status, normalizedQuery, paginationWindow.currentPage(), size, userId, sortOrder);
            totalPages = trackParcelPage.getTotalPages();
            paginationWindow = PaginationUtils.calculateWindow(paginationWindow.currentPage(), totalPages, PAGE_WINDOW);
        }

        ZoneId userZone = userService.getUserZone(userId);
        List<OrderReturnRequest> activeReturnRequests = orderReturnRequestService.findActiveRequestsWithDetails(userId);
        List<ActionRequiredReturnRequestDto> actionRequiredRequests = activeReturnRequests.stream()
                .map(request -> mapActionRequiredRequest(request, userZone))
                .toList();

        // Отмечаем посылки, требующие действий по возвратам/обменам
        Set<Long> actionRequired = activeReturnRequests.stream()
                .map(OrderReturnRequest::getParcel)
                .filter(Objects::nonNull)
                .map(TrackParcel::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Добавляем иконки в DTO перед передачей в шаблон
        trackParcelPage.forEach(dto -> {
            GlobalStatus statusEnum = GlobalStatus.fromDescription(dto.getStatus()); // Конвертация строки в Enum
            dto.setIconHtml(statusTrackService.getIcon(statusEnum)); // Передаем Enum в сервис для получения иконки
            dto.setRequiresAction(actionRequired.contains(dto.getId()));
        });

        log.debug("Передача атрибутов в модель: stores={}, storeId={}, trackParcelDTO={}, currentPage={}, totalPages={}, size={}",
                stores, storeId, trackParcelPage.getContent(), paginationWindow.currentPage(), totalPages, size);

        List<PaginationItem> paginationItems = paginationWindow.paginationItems();

        // Добавляем атрибуты в модель
        model.addAttribute("stores", stores);
        model.addAttribute("storeId", storeId != null ? storeId : ""); // Если null, передаем пустую строку
        model.addAttribute("size", size);
        model.addAttribute("trackParcelDTO", trackParcelPage.getContent());
        model.addAttribute("statusString", statusString);
        model.addAttribute("query", query);
        model.addAttribute("currentPage", paginationWindow.currentPage());
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("startPage", paginationWindow.startPage());
        model.addAttribute("endPage", paginationWindow.endPage());
        model.addAttribute("paginationItems", paginationItems);
        model.addAttribute("trackParcelNotification", trackParcelPage.isEmpty() ? "Отслеживаемых посылок нет" : null);
        model.addAttribute("bulkUpdateButtonDTO",
                new BulkUpdateButtonDTO(userService.isShowBulkUpdateButton(user.getId())));
        // Передаём текущий порядок сортировки во вью, чтобы отобразить правильную стрелку на кнопке
        model.addAttribute("sortOrder", sortOrder);
        model.addAttribute("actionRequiredCount", actionRequiredRequests.size());
        model.addAttribute("actionRequiredRequests", actionRequiredRequests);

        return "app/departures";
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
     * Сохраняет трек-номер для предварительно зарегистрированной посылки.
     *
     * @param id     идентификатор посылки
     * @param number новый трек-номер
     * @param user   текущий пользователь
     * @return результат операции
     */
    @PostMapping("/set-number")
    public ResponseEntity<?> setNumber(
            @RequestParam Long id,
            @RequestParam String number,
            @AuthenticationPrincipal User user) {
        try {
            trackParcelService.assignTrackNumber(id, number, user.getId());
            return ResponseBuilder.ok("Трек-номер добавлен");
        } catch (TrackNumberAlreadyExistsException e) {
            log.warn("Попытка добавить уже существующий трек-номер: {} для пользователя {}", number, user.getId());
            return ResponseBuilder.error(HttpStatus.CONFLICT, e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("Некорректный трек-номер: {} для пользователя {}", number, user.getId());
            return ResponseBuilder.error(HttpStatus.BAD_REQUEST, e.getMessage());
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
    /**
     * Удаляет выбранные пользователем посылки.
     * <p>
     * Удаление может происходить как по трек-номерам, так и по идентификаторам
     * (для предрегистрационных посылок без номера).
     * </p>
     *
     * @param selectedNumbers список трек-номеров
     * @param selectedIds     список идентификаторов посылок
     * @param user            текущий пользователь
     * @return результат операции удаления
     */
    @PostMapping("/delete-selected")
    public ResponseEntity<?> deleteSelected(
            @RequestParam(value = "selectedNumbers", required = false) List<String> selectedNumbers,
            @RequestParam(value = "selectedIds", required = false) List<Long> selectedIds,
            @AuthenticationPrincipal User user) {
        Long userId = user.getId();
        log.info("Запрос на удаление посылок {} и ID {} для пользователя с ID: {}", selectedNumbers, selectedIds, userId);

        boolean hasNumbers = selectedNumbers != null && selectedNumbers.stream().anyMatch(num -> num != null && !num.isBlank());
        boolean hasIds = selectedIds != null && !selectedIds.isEmpty();

        if (!hasNumbers && !hasIds) {
            log.warn("Попытка удаления без выбранных посылок пользователем с ID: {}", userId);
            return ResponseBuilder.error(HttpStatus.BAD_REQUEST, "Ошибка: Не выбраны посылки для удаления.");
        }

        try {
            if (hasNumbers) {
                List<String> filteredNumbers = selectedNumbers.stream()
                        .filter(num -> num != null && !num.isBlank())
                        .toList();
                if (!filteredNumbers.isEmpty()) {
                    trackFacade.deleteByNumbersAndUserId(filteredNumbers, userId);
                }
            }
            if (hasIds) {
                trackFacade.deleteByIdsAndUserId(selectedIds, userId);
            }
            log.info("Выбранные посылки удалены пользователем с ID: {}", userId);
            webSocketController.sendUpdateStatus(userId, "Выбранные посылки успешно удалены.", true);
            return ResponseBuilder.ok("Выбранные посылки успешно удалены.");
        } catch (EntityNotFoundException ex) {
            log.warn("Попытка удалить несуществующие посылки пользователем {}", userId);
            webSocketController.sendUpdateStatus(userId, ex.getMessage(), false);
            return ResponseBuilder.error(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (Exception e) {
            log.error("Ошибка при удалении посылок пользователем с ID: {}: {}", userId, e.getMessage(), e);
            webSocketController.sendUpdateStatus(userId, "Ошибка при удалении посылок.", false);
            return ResponseBuilder.error(HttpStatus.INTERNAL_SERVER_ERROR, "Ошибка при удалении посылок.");
        }
    }

    /**
     * Загружает страницу отправлений с учётом фильтров, сортировки и поиска.
     *
     * @param storeIds  идентификаторы магазинов для фильтрации
     * @param status    глобальный статус посылки
     * @param query     поисковый запрос (может быть {@code null})
     * @param pageIndex индекс страницы, которую требуется получить
     * @param size      размер страницы
     * @param userId    идентификатор пользователя
     * @param sortOrder порядок сортировки (asc/desc)
     * @return страница с результатами выборки
     */
    private Page<TrackParcelDTO> loadTrackParcelPage(List<Long> storeIds,
                                                     GlobalStatus status,
                                                     String query,
                                                     int pageIndex,
                                                     int size,
                                                     Long userId,
                                                     String sortOrder) {
        if (query != null && !query.isBlank()) {
            return trackParcelService.searchByNumberPhoneOrName(storeIds, status, query, pageIndex, size, userId, sortOrder);
        }

        if (status != null) {
            if (status == GlobalStatus.PRE_REGISTERED) {
                return trackParcelService.findByStoreTracksWithPreRegistered(storeIds, pageIndex, size, userId, sortOrder);
            }
            return trackParcelService.findByStoreTracksAndStatus(storeIds, status, pageIndex, size, userId, sortOrder);
        }

        return trackParcelService.findByStoreTracks(storeIds, pageIndex, size, userId, sortOrder);
    }

    /**
     * Преобразует заявку на возврат в DTO для вкладки «Требуют действия».
     *
     * @param request исходная заявка
     * @param userZone часовой пояс пользователя для форматирования дат
     * @return DTO с подготовленными строками для отображения
     */
    private ActionRequiredReturnRequestDto mapActionRequiredRequest(OrderReturnRequest request, ZoneId userZone) {
        TrackParcel parcel = request.getParcel();
        Long parcelId = parcel != null ? parcel.getId() : null;
        String trackNumber = parcel != null ? parcel.getNumber() : null;
        String storeName = parcel != null && parcel.getStore() != null ? parcel.getStore().getName() : null;
        GlobalStatus parcelStatus = parcel != null ? parcel.getStatus() : null;
        OrderReturnRequestStatus status = request.getStatus();

        String requestedAt = formatRequestMoment(request.getRequestedAt(), userZone);
        String createdAt = formatRequestMoment(request.getCreatedAt(), userZone);

        boolean canStartExchange = orderReturnRequestService.canStartExchange(request);
        boolean canCloseWithoutExchange = status == OrderReturnRequestStatus.REGISTERED;
        String cancelExchangeReason = orderReturnRequestService
                .getExchangeCancellationBlockReason(request)
                .orElse(null);
        boolean exchangeShipmentDispatched = orderReturnRequestService.isExchangeShipmentDispatched(request);

        return new ActionRequiredReturnRequestDto(
                request.getId(),
                parcelId,
                trackNumber,
                storeName,
                parcelStatus != null ? parcelStatus.getDescription() : null,
                status,
                status != null ? status.getDisplayName() : null,
                requestedAt,
                createdAt,
                request.getReason(),
                request.getComment(),
                request.getReverseTrackNumber(),
                request.isExchangeRequested(),
                canStartExchange,
                canCloseWithoutExchange,
                exchangeShipmentDispatched,
                cancelExchangeReason
        );
    }

    /**
     * Форматирует момент времени заявки в пользовательской временной зоне.
     *
     * @param moment исходное значение в UTC
     * @param userZone часовой пояс пользователя
     * @return отформатированная строка или {@code null}, если момент отсутствует
     */
    private String formatRequestMoment(ZonedDateTime moment, ZoneId userZone) {
        if (moment == null || userZone == null) {
            return null;
        }
        return REQUEST_DATE_FORMATTER.format(moment.withZoneSameInstant(userZone));
    }
}
