package com.project.tracking_system.controller;

import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.StoreStatistics;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.service.analytics.StoreAnalyticsService;
import com.project.tracking_system.service.store.StoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * @author Dmitriy Anisimov
 * @date 13.03.2025
 */
@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("/analytics")
public class StoreAnalyticsController {

    private final StoreAnalyticsService storeAnalyticsService;
    private final StoreService storeService;
    private final WebSocketController webSocketController;

    /**
     * Отображает дашборд с аналитикой пользователя.
     * Если передан storeId — показываем аналитику конкретного магазина,
     * иначе — аналитику по всем магазинам пользователя.
     */
    @GetMapping
    public String getAnalyticsDashboard(@RequestParam(required = false) Long storeId,
                                        Model model,
                                        Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof User user)) {
            log.debug("Попытка доступа к аналитике без аутентификации.");
            return "redirect:/login";
        }

        Long userId = user.getId();
        log.info("Запрос аналитики для пользователя ID: {}", userId);

        List<Store> stores = storeService.getUserStores(userId);
        model.addAttribute("stores", stores);
        model.addAttribute("isMultiStore", stores.size() > 1);

        // Если указан конкретный магазин - показываем его аналитику
        if (storeId != null) {
            log.info("Запрос аналитики для магазина ID: {}", storeId);
            StoreStatistics statistics = storeAnalyticsService.getStoreStatistics(storeId)
                    .orElseThrow(() -> new IllegalArgumentException("Статистика для магазина не найдена"));
            model.addAttribute("statistics", List.of(statistics));
        } else {
            // Если магазин не выбран — показываем аналитику по всем магазинам пользователя
            List<StoreStatistics> statistics = storeAnalyticsService.getUserStatistics(userId);
            model.addAttribute("statistics", statistics);
        }

        return "analytics/dashboard";
    }

    /**
     * Обновляет аналитику.
     * Если параметр storeId отсутствует — обновляем аналитику для всех магазинов пользователя,
     * иначе — обновляем аналитику только для выбранного магазина.
     */
    @PostMapping("/update")
    public ResponseEntity<?> updateAnalytics(@RequestParam(required = false) Long storeId, Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof User user)) {
            log.warn("Попытка обновления аналитики без аутентификации.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Вы не авторизованы"));
        }

        Long userId = user.getId();
        log.info("Запрос на обновление аналитики: userId={}, storeId={}", userId, storeId);

        // Если `storeId == null`, обновляем все магазины пользователя
        if (storeId == null) {
            List<Long> userStoreIds = storeService.getUserStoreIds(userId);
            log.info("Пользователь выбрал 'все магазины'. Обновляем все магазины: {}", userStoreIds);
            userStoreIds.forEach(storeAnalyticsService::updateStoreAnalytics);

            webSocketController.sendUpdateStatus(userId, "Обновлена аналитика по всем вашим магазинам!", true);
            return ResponseEntity.ok(Map.of("message", "Аналитика обновлена по всем магазинам!"));
        }

        // Получаем магазин и проверяем принадлежность
        Store store = storeService.getStore(storeId, userId);

        // Обновляем аналитику только для выбранного магазина
        storeAnalyticsService.updateStoreAnalytics(storeId);
        webSocketController.sendUpdateStatus(userId, "Аналитика обновлена для магазина: " + store.getName(), true);

        return ResponseEntity.ok(Map.of("message", "Аналитика обновлена для магазина: " + store.getName()));
    }

}