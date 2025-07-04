package com.project.tracking_system.controller;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.service.store.StoreService;
import com.project.tracking_system.service.track.TrackFacade;
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
@RequestMapping("/")
public class HomeController {

    private final TrackFacade trackFacade;
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
        return "home";
    }

    /**
     * Обрабатывает POST-запросы на главной странице.
     * <p>
     * Выполняет отслеживание посылки по номеру, а также связывает её с покупателем при указании телефона.
     * </p>
     *
     * @param number  номер посылки
     * @param storeId идентификатор магазина
     * @param phone   телефон покупателя
     * @param model   модель представления
     * @param user    аутентифицированный пользователь
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
            // trackParcelService реализует логику с посылкой!
            TrackInfoListDTO trackInfo = trackFacade.processTrack(number, storeId, userId, canSave, phone);

            if (trackInfo == null || trackInfo.getList().isEmpty()) {
                model.addAttribute("customError", "Нет данных для указанного номера посылки.");
                log.warn("Нет данных для номера: {}", number);
                return "home";
            }

            model.addAttribute("trackInfo", trackInfo);
        } catch (IllegalArgumentException e) {
            model.addAttribute("customError", e.getMessage());
            log.warn("Ошибка: {}", e.getMessage());
        } catch (Exception e) {
            model.addAttribute("generalError", "Произошла ошибка при обработке запроса.");
            log.error("Общая ошибка: {}", e.getMessage(), e);
        }

        return "home";
    }


    @GetMapping("/privacy-policy")
    public String privacyPolicy() {
        return "privacy-policy";
    }

    @GetMapping("/terms-of-use")
    public String termsOfUse() {
        return "terms-of-use";
    }

}
