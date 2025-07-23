package com.project.tracking_system.controller;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.entity.PostalServiceType;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.service.store.StoreService;
import com.project.tracking_system.service.track.TrackFacade;
import com.project.tracking_system.service.track.TrackMeta;
import com.project.tracking_system.service.track.TrackUpdateDispatcherService;
import com.project.tracking_system.service.track.TrackServiceClassifier;
import com.project.tracking_system.service.track.BelPostManualService;
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

    private final TrackFacade trackFacade;
    private final StoreService storeService;
    private final TrackUpdateDispatcherService trackUpdateDispatcherService;
    /** Сервис классификации трек-номеров по типу почтовой службы. */
    private final TrackServiceClassifier trackServiceClassifier;
    /** Сервис постановки в очередь треков Белпочты. */
    private final BelPostManualService belPostManualService;

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
     * Обрабатывает POST-запросы на главной странице.
     * <p>
     * Выполняет отслеживание посылки по номеру.
     * <p>
     * Номера Белпочты помещаются в очередь через {@link BelPostManualService}
     * и обрабатываются асинхронно. Для остальных номеров информация
     * загружается синхронно через {@link TrackUpdateDispatcherService}.
     * При указании телефона трек связывается с покупателем.
     * </p>
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
            PostalServiceType type = trackServiceClassifier.detect(number);

            if (type == PostalServiceType.BELPOST && userId != null) {
                boolean queued = belPostManualService.enqueueIfAllowed(number, storeId, userId);
                if (queued) {
                    model.addAttribute("successMessage", "Номер добавлен в очередь обработки.");
                } else {
                    model.addAttribute("customError", "Трек уже обновлялся недавно или имеет финальный статус.");
                }
                return "app/home";
            }

            TrackMeta meta = new TrackMeta(number, storeId, phone, canSave);
            TrackInfoListDTO trackInfo = trackUpdateDispatcherService.dispatch(meta).getTrackInfo();

            if (trackInfo == null || trackInfo.getList().isEmpty()) {
                model.addAttribute("customError", "Нет данных для указанного номера посылки.");
                log.warn("Нет данных для номера: {}", number);
                return "app/home";
            }

            model.addAttribute("trackInfo", trackInfo);
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
