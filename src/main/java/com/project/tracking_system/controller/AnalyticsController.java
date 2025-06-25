package com.project.tracking_system.controller;

import com.project.tracking_system.dto.PostalServiceStatsDTO;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.StoreStatistics;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.service.analytics.PostalServiceStatisticsService;
import com.project.tracking_system.service.analytics.StoreAnalyticsService;
import com.project.tracking_system.service.analytics.StoreDashboardDataService;
import com.project.tracking_system.service.analytics.AnalyticsResetService;
import com.project.tracking_system.service.store.StoreService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.project.tracking_system.utils.ResponseBuilder;
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

    private final PostalServiceStatisticsService postalStatisticsService;
    private final StoreAnalyticsService storeAnalyticsService;
    private final StoreService storeService;
    private final StoreDashboardDataService storeDashboardDataService;
    private final AnalyticsResetService analyticsResetService;
    private final WebSocketController webSocketController;

    /**
     * Отображает дашборд с аналитикой пользователя.
     * Если передан storeId — показываем аналитику конкретного магазина,
     * иначе — аналитику по всем магазинам пользователя.
     */
    @GetMapping
    public String getAnalyticsDashboard(
            @RequestParam(name = "storeId", required = false) String rawStoreId,
            @RequestParam(defaultValue = "WEEKS") String interval,
            Model model,
            @AuthenticationPrincipal User user,
            HttpServletRequest request) {

        // 1) Проверяем аутентификацию
        Long userId = user.getId();
        ZoneId userZone = ZoneId.of(user.getTimeZone());
        List<Store> stores = storeService.getUserStores(userId);

        // 2) Разбираем параметр rawStoreId
        Long storeId = null;
        if (rawStoreId != null
                && !rawStoreId.isBlank()
                && !rawStoreId.equalsIgnoreCase("all")) {
            try {
                storeId = Long.parseLong(rawStoreId);
            } catch (NumberFormatException e) {
                log.warn("Некорректный storeId: '{}'", rawStoreId);
            }
        }

        // 3) Если магазин у пользователя один и storeId всё ещё null — выбираем его автоматически
        if (storeId == null && stores.size() == 1) {
            storeId = stores.get(0).getId();
            log.info("Автовыбор магазина, т.к. он один у пользователя: ID={}", storeId);
        }

        // 4) Собираем статистику в зависимости от выбранного storeId
        List<StoreStatistics>  statistics;
        StoreStatistics        storeStatistics;
        List<PostalServiceStatsDTO> postalStats;
        List<StoreStatistics>  visibleStats;
        List<Long>             storeIds;

        if (storeId != null) {
            log.info("Запрос аналитики для магазина ID: {}", storeId);

            try {
                // Проверяем, принадлежит ли магазин пользователю
                storeService.getStore(storeId, userId);
            } catch (SecurityException ex) {
                // Возвращаем 403, если пользователь не владеет магазином
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }

            StoreStatistics stat = storeAnalyticsService
                    .getStoreStatistics(storeId)
                    .orElseThrow(() -> new IllegalArgumentException("Статистика для магазина не найдена"));

            statistics     = List.of(stat);
            storeStatistics = stat;
            postalStats    = postalStatisticsService.getStatsByStore(storeId);
            visibleStats   = statistics;
            storeIds       = List.of(storeId);

        } else {
            log.info("Запрос аналитики по всем магазинам пользователя ID: {}", userId);

            statistics     = storeAnalyticsService.getUserStatistics(userId);
            storeStatistics = storeAnalyticsService.aggregateStatistics(statistics);
            postalStats    = postalStatisticsService.getStatsForStores(
                    stores.stream().map(Store::getId).toList());
            visibleStats   = statistics;
            storeIds       = stores.stream().map(Store::getId).toList();
        }

        log.debug("selectedStoreId = {}", storeId);

        // 5) Готовим данные для графиков
        Map<String, Object> pieStats    = storeDashboardDataService.calculatePieData(visibleStats);
        Map<String, Object> periodStats = storeDashboardDataService.getFullPeriodStatsChart(
                storeIds,
                ChronoUnit.valueOf(interval.toUpperCase()),
                userZone
        );

        // 6) Заполняем модель одним блоком
        model.addAttribute("stores",            stores);
        model.addAttribute("isMultiStore",      stores.size() > 1);
        model.addAttribute("selectedInterval",  interval);
        model.addAttribute("selectedStoreId",   storeId);

        model.addAttribute("statistics",        statistics);
        model.addAttribute("storeStatistics",   storeStatistics);
        model.addAttribute("postalStats",       postalStats);

        model.addAttribute("chartDelivered",    pieStats.get("delivered"));
        model.addAttribute("chartReturned",     pieStats.get("returned"));
        model.addAttribute("chartInTransit",    pieStats.get("inTransit"));

        model.addAttribute("periodLabels",      periodStats.get("labels"));
        model.addAttribute("periodSent",        periodStats.get("sent"));
        model.addAttribute("periodDelivered",   periodStats.get("delivered"));
        model.addAttribute("periodReturned",    periodStats.get("returned"));

        model.addAttribute("nonce", request.getAttribute("nonce"));

        return "analytics/dashboard";
    }

    /**
     * Обновляет таймстемп и собирает новую аналитику для магазина или всех магазинов
     * пользователя. Данные пересчитываются инкрементально.
     *
     * @param storeId        идентификатор магазина. Если не указан, обновляется
     *                       аналитика по всем магазинам пользователя
     * @param user           текущий пользователь
     * @return JSON-ответ с сообщением о результате обновления
     */
    @PostMapping("/update")
    public ResponseEntity<?> updateAnalytics(@RequestParam(required = false) Long storeId,
                                             @AuthenticationPrincipal User user) {
        Long userId = user.getId();
        log.info("Обновление timestamp аналитики (данные считаются инкрементально): userId={}, storeId={}", userId, storeId);

        if (storeId == null) {
            List<Long> userStoreIds = storeService.getUserStoreIds(userId);
            userStoreIds.forEach(storeAnalyticsService::updateStoreAnalytics);

            webSocketController.sendUpdateStatus(userId, "Обновлена аналитика по всем вашим магазинам!", true);
            return ResponseBuilder.ok(Map.of("message", "Аналитика обновлена по всем магазинам!"));
        }

        Store store = storeService.getStore(storeId, userId);
        storeAnalyticsService.updateStoreAnalytics(storeId);
        webSocketController.sendUpdateStatus(userId, "Аналитика обновлена для магазина: " + store.getName(), true);

        return ResponseBuilder.ok(Map.of("message", "Аналитика обновлена для магазина: " + store.getName()));
    }

    /**
     * Возвращает агрегированную аналитику в формате JSON. Используется для
     * построения графиков на клиенте.
     *
     * @param storeId        идентификатор магазина. Если null, данные собираются
     *                       по всем магазинам пользователя
     * @param interval       интервал агрегации (DAYS/WEEKS/MONTHS/YEARS)
     * @param user       текущий пользователь
     * @return карта с данными для круговой диаграммы и статистикой по периодам
     */
    @GetMapping("/json")
    @ResponseBody
    public Map<String, Object> getAnalyticsJson(@RequestParam(required = false) Long storeId,
                                                @RequestParam(defaultValue = "WEEKS") String interval,
                                                @AuthenticationPrincipal User user) {
        Long userId = user.getId();
        List<Store> stores = storeService.getUserStores(userId);
        List<StoreStatistics> visibleStats;
        List<Long>          storeIds;
        StoreStatistics     storeStatistics;

        if (storeId != null) {
            try {
                // Проверяем права доступа к магазину
                storeService.getStore(storeId, userId);
            } catch (SecurityException ex) {
                // Пользователь не владеет магазином
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }

            StoreStatistics stat = storeAnalyticsService.getStoreStatistics(storeId)
                    .orElseThrow(() -> new IllegalArgumentException("Нет статистики"));

            visibleStats    = List.of(stat);
            storeStatistics = stat;
            storeIds = List.of(storeId);
        } else {
            List<StoreStatistics> stats = storeAnalyticsService.getUserStatistics(userId);
            visibleStats    = stats;
            storeStatistics = storeAnalyticsService.aggregateStatistics(stats);
            storeIds = stores.stream().map(Store::getId).toList();
        }

        ZoneId userZone = ZoneId.of(user.getTimeZone());
        ChronoUnit chrono = ChronoUnit.valueOf(interval.toUpperCase());

        Map<String, Object> periodStats = storeDashboardDataService
                .getFullPeriodStatsChart(storeIds, chrono, userZone);

        Map<String, Object> storeStats = Map.of(
                "totalSent",          storeStatistics.getTotalSent(),
                "totalDelivered",     storeStatistics.getTotalDelivered(),
                "totalReturned",      storeStatistics.getTotalReturned(),
                "averageDeliveryDays", storeStatistics.getAverageDeliveryDays(),
                "averagePickupDays",  storeStatistics.getAveragePickupDays()
        );

        return Map.of(
                "pieData",       storeDashboardDataService.calculatePieData(visibleStats),
                "periodStats",   periodStats,
                "storeStatistics", storeStats
        );
    }

    /**
     * Удаляет всю аналитику пользователя.
     */
    @PostMapping("/reset/all")
    public ResponseEntity<Void> resetAllAnalyticsForUser(@AuthenticationPrincipal User user) {
        analyticsResetService.resetAllAnalytics(user.getId());
        webSocketController.sendUpdateStatus(user.getId(), "Аналитика удалена", true);
        return ResponseBuilder.ok(null);
    }

    /**
     * Удаляет аналитику конкретного магазина пользователя.
     */
    @PostMapping("/reset/store/{storeId}")
    public ResponseEntity<Void> resetAnalyticsForStore(@PathVariable Long storeId,
                                                       @AuthenticationPrincipal User user) {
        analyticsResetService.resetStoreAnalytics(user.getId(), storeId);
        webSocketController.sendUpdateStatus(user.getId(), "Аналитика магазина удалена", true);
        return ResponseBuilder.ok(null);
    }

}