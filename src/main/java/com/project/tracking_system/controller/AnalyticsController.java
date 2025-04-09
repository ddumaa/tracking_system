package com.project.tracking_system.controller;

import com.project.tracking_system.dto.PostalServiceStatsDTO;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.StoreStatistics;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.service.analytics.DeliveryHistoryService;
import com.project.tracking_system.service.analytics.StoreAnalyticsService;
import com.project.tracking_system.service.analytics.StoreDashboardDataService;
import com.project.tracking_system.service.store.StoreService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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
public class AnalyticsController {

    private final DeliveryHistoryService deliveryHistoryService;
    private final StoreAnalyticsService storeAnalyticsService;
    private final StoreService storeService;
    private final StoreDashboardDataService storeDashboardDataService;
    private final WebSocketController webSocketController;

    /**
     * Отображает дашборд с аналитикой пользователя.
     * Если передан storeId — показываем аналитику конкретного магазина,
     * иначе — аналитику по всем магазинам пользователя.
     */
    @GetMapping
    public String getAnalyticsDashboard(@RequestParam(name = "storeId", required = false) String rawStoreId,
                                        @RequestParam(defaultValue = "WEEKS") String interval,
                                        Model model,
                                        Authentication authentication,
                                        HttpServletRequest request) {
        if (!(authentication.getPrincipal() instanceof User user)) {
            log.debug("Попытка доступа к аналитике без аутентификации.");
            return "redirect:/login";
        }

        Long storeId = null;
        if (rawStoreId != null && !rawStoreId.isBlank() && !rawStoreId.equalsIgnoreCase("all")) {
            try {
                storeId = Long.parseLong(rawStoreId);
            } catch (NumberFormatException e) {
                log.warn("Некорректный storeId: '{}'", rawStoreId);
            }
        }

        Long userId = user.getId();
        ZoneId userZone = ZoneId.of(user.getTimeZone());

        List<Store> stores = storeService.getUserStores(userId);
        model.addAttribute("stores", stores);
        model.addAttribute("isMultiStore", stores.size() > 1);
        model.addAttribute("selectedInterval", interval);

        List<StoreStatistics> visibleStats;
        List<Long> storeIds;

        if (storeId != null) {
            log.info("Запрос аналитики для магазина ID: {}", storeId);

            StoreStatistics statistics = storeAnalyticsService.getStoreStatistics(storeId)
                    .orElseThrow(() -> new IllegalArgumentException("Статистика для магазина не найдена"));

            List<PostalServiceStatsDTO> postalStats = deliveryHistoryService.getStatsByPostalService(storeId);
            model.addAttribute("postalStats", postalStats);
            model.addAttribute("statistics", List.of(statistics));
            model.addAttribute("storeStatistics", statistics);

            visibleStats = List.of(statistics);
            storeIds = List.of(storeId);
        } else {
            log.info("Запрос аналитики по всем магазинам пользователя ID: {}", userId);

            List<StoreStatistics> statistics = storeAnalyticsService.getUserStatistics(userId);
            List<Long> storeIdList = stores.stream().map(Store::getId).toList();

            List<PostalServiceStatsDTO> postalStats = deliveryHistoryService.getStatsByPostalServiceForStores(storeIdList);

            StoreStatistics summary = storeAnalyticsService.aggregateStatistics(statistics);
            model.addAttribute("storeStatistics", summary);

            model.addAttribute("postalStats", postalStats);
            model.addAttribute("statistics", statistics);

            visibleStats = statistics;
            storeIds = storeIdList;
        }

        log.debug("selectedStoreId = {}", storeId);
        model.addAttribute("selectedStoreId", storeId);

        // Данные для pie-графика
        Map<String, Object> pie = storeDashboardDataService.calculatePieData(visibleStats);
        model.addAttribute("chartDelivered", pie.get("delivered"));
        model.addAttribute("chartReturned", pie.get("returned"));
        model.addAttribute("chartInTransit", pie.get("inTransit"));

        // Данные для bar-графика
        ChronoUnit selectedInterval = ChronoUnit.valueOf(interval.toUpperCase());
        Map<String, Object> periodStats = storeDashboardDataService.getFullPeriodStatsChart(storeIds, selectedInterval, userZone);
        model.addAttribute("periodLabels", periodStats.get("labels"));
        model.addAttribute("periodSent", periodStats.get("sent"));
        model.addAttribute("periodDelivered", periodStats.get("delivered"));
        model.addAttribute("periodReturned", periodStats.get("returned"));

        model.addAttribute("nonce", request.getAttribute("nonce"));
        return "analytics/dashboard";
    }

    @PostMapping("/update")
    public ResponseEntity<?> updateAnalytics(@RequestParam(required = false) Long storeId,
                                             Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof User user)) {
            log.warn("Попытка обновления аналитики без аутентификации.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Вы не авторизованы"));
        }

        Long userId = user.getId();
        log.info("Запрос на обновление аналитики: userId={}, storeId={}", userId, storeId);

        if (storeId == null) {
            List<Long> userStoreIds = storeService.getUserStoreIds(userId);
            userStoreIds.forEach(storeAnalyticsService::updateStoreAnalytics);

            webSocketController.sendUpdateStatus(userId, "Обновлена аналитика по всем вашим магазинам!", true);
            return ResponseEntity.ok(Map.of("message", "Аналитика обновлена по всем магазинам!"));
        }

        Store store = storeService.getStore(storeId, userId);
        storeAnalyticsService.updateStoreAnalytics(storeId);
        webSocketController.sendUpdateStatus(userId, "Аналитика обновлена для магазина: " + store.getName(), true);

        return ResponseEntity.ok(Map.of("message", "Аналитика обновлена для магазина: " + store.getName()));
    }

    @GetMapping("/json")
    @ResponseBody
    public Map<String, Object> getAnalyticsJson(@RequestParam(required = false) Long storeId,
                                                @RequestParam(defaultValue = "WEEKS") String interval,
                                                Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof User user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        Long userId = user.getId();
        List<Store> stores = storeService.getUserStores(userId);
        List<StoreStatistics> visibleStats;
        List<Long> storeIds;
        if (storeId != null) {
            visibleStats = List.of(storeAnalyticsService.getStoreStatistics(storeId)
                    .orElseThrow(() -> new IllegalArgumentException("Нет статистики")));
            storeIds = List.of(storeId);
        } else {
            visibleStats = storeAnalyticsService.getUserStatistics(userId);
            storeIds = stores.stream().map(Store::getId).toList();
        }

        ZoneId userZone = ZoneId.of(user.getTimeZone());
        ChronoUnit chrono = ChronoUnit.valueOf(interval.toUpperCase());

        Map<String, Object> periodStats = storeDashboardDataService.getFullPeriodStatsChart(storeIds, chrono, userZone);

        return Map.of(
                "pieData", storeDashboardDataService.calculatePieData(visibleStats),
                "periodStats", periodStats
        );
    }

}