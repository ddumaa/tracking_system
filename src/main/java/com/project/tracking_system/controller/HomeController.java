package com.project.tracking_system.controller;

import com.project.tracking_system.dto.TrackingResultAdd;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.service.store.StoreService;
import com.project.tracking_system.service.track.TrackConstants;
import com.project.tracking_system.service.track.TrackMeta;
import com.project.tracking_system.service.track.TrackUpdateCoordinatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Контроллер для отображения главной страницы и отслеживания посылок.
 * <p>
 * Обрабатывает домашнюю страницу и взаимодействует с сервисами отслеживания.
 * </p>
 *
 * @author Dmitriy Anisimov
 * @date 07.01.2025
 */
@RequiredArgsConstructor
@Slf4j
@Controller
@RequestMapping("/app")
public class HomeController {

    private final TrackUpdateCoordinatorService trackUpdateCoordinatorService;
    private final StoreService storeService;

    /**
     * Обрабатывает запросы на главной странице. Отображает домашнюю страницу.
     *
     * @return имя представления домашней страницы
     */
    @GetMapping
    public String home(@AuthenticationPrincipal User user, Model model) {
        if (user != null) {
            // Получаем магазины пользователя
            List<Store> stores = storeService.getUserStores(user.getId());
            model.addAttribute("stores", stores);
        }
        return "app/home";
    }

    /**
     * Обрабатывает POST-запрос от формы отслеживания.
     * <p>
     * На основе введённых данных формирует {@link TrackMeta} и передаёт его
     * в {@link TrackUpdateCoordinatorService}, что обеспечивает единый механизм
     * детектирования почтовой службы и сохранения трека.
     * </p>
     *
     * @param number  номер посылки
     * @param storeId идентификатор магазина
     * @param phone   телефон покупателя
     * @param model   модель представления
     * @param user    текущий пользователь
     * @return имя представления домашней страницы
     */
    @PostMapping
    public String home(@ModelAttribute("number") String number,
                       @RequestParam(value = "storeId", required = false) Long storeId,
                       @RequestParam(value = "phone", required = false) String phone,
                       Model model,
                       @AuthenticationPrincipal User user) {
        Long userId = user != null ? user.getId() : null;

        boolean canSave = userId != null;

        model.addAttribute("number", number);

        // Получаем магазины пользователя и определяем ID магазина
        List<Store> stores = userId != null ? storeService.getUserStores(userId) : List.of();
        storeId = storeService.resolveStoreId(storeId, stores);
        model.addAttribute("stores", stores);

        try {
            // Используем общий механизм обновления, как и при пакетной загрузке
            TrackMeta meta = new TrackMeta(number.toUpperCase(), storeId, phone, canSave);
            List<TrackingResultAdd> results =
                    trackUpdateCoordinatorService.process(List.of(meta), userId);

            if (results.isEmpty() ||
                    results.get(0).getStatus().equals(TrackConstants.NO_DATA_STATUS)) {
                model.addAttribute("customError", "Нет данных для указанного номера посылки.");
                log.warn("Нет данных для номера: {}", number);
                return "app/home";
            }

            model.addAttribute("trackingResults", results);
        } catch (IllegalArgumentException e) {
            model.addAttribute("customError", e.getMessage());
            log.warn("Ошибка: {}", e.getMessage());
        } catch (Exception e) {
            model.addAttribute("generalError", "Произошла ошибка при обработке запроса.");
            log.error("Общая ошибка: {}", e.getMessage(), e);
        }

        return "app/home";
    }
}
