package com.project.tracking_system.controller;

import com.project.tracking_system.dto.TrackParcelDTO;
import com.project.tracking_system.dto.UserDetailsAdminInfoDTO;
import com.project.tracking_system.dto.UserListAdminInfoDTO;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.entity.UserSubscription;
import com.project.tracking_system.repository.StoreRepository;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.service.analytics.StatsAggregationService;
import com.project.tracking_system.service.track.TrackParcelService;
import com.project.tracking_system.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Административный контроллер
 *
 * @author Dmitriy Anisimov
 * @date 07.02.2025
 */
@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class AdminController {

    private final UserService userService;
    private final TrackParcelService trackParcelService;
    private final SubscriptionService subscriptionService;
    private final StoreRepository storeRepository;
    private final StatsAggregationService statsAggregationService;

    @GetMapping()
    public String adminDashboard(Model model) {
        long totalUsers = userService.countUsers();
        long paidUsers = userService.countUsersBySubscriptionPlan("PREMIUM");
        long totalParcels = trackParcelService.countAllParcels();

        // Добавляем статистику в модель
        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("paidUsers", paidUsers);
        model.addAttribute("totalParcels", totalParcels);

        return "admin/dashboard";
    }

    @GetMapping("/users")
    public String getAllUsers(Model model) {
        List<User> users = userService.findAll();
        List<UserListAdminInfoDTO> userListAdminInfoDTOS = new ArrayList<>();

        for (User user : users) {
            // Получаем подписку пользователя (если есть)
            String subscriptionName = user.getSubscription() != null
                    ? user.getSubscription().getSubscriptionPlan().getName()
                    : "NONE"; // Если подписки нет, ставим "NONE" или "FREE"

            UserListAdminInfoDTO userListAdminInfoDTO = new UserListAdminInfoDTO(
                    user.getId(),
                    user.getEmail(),
                    user.getRole(),
                    subscriptionName
            );

            userListAdminInfoDTOS.add(userListAdminInfoDTO);
        }

        model.addAttribute("users", userListAdminInfoDTOS);

        return "admin/user-list";
    }

    @GetMapping("/users/{userId}")
    public String getUserDetails(@PathVariable Long userId, Model model) {
        User user = userService.findUserById(userId);

        // Загружаем магазины пользователя
        List<Store> stores = storeRepository.findByOwnerId(userId);
        Map<Long, List<TrackParcelDTO>> storeParcels = new HashMap<>();

        for (Store store : stores) {
            Long storeId = store.getId();
            List<TrackParcelDTO> parcels = trackParcelService.findAllByStoreTracks(storeId, userId);
            storeParcels.put(storeId, parcels);
        }

        // Получаем подписку пользователя (если есть)
        UserSubscription subscription = user.getSubscription();
        String subscriptionName = (subscription != null)
                ? subscription.getSubscriptionPlan().getName()
                : "NONE"; // Если подписки нет, указываем "NONE" или "FREE"

        String subscriptionEndDate = null;
        if (subscription != null && subscription.getSubscriptionEndDate() != null) {
            subscriptionEndDate = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .format(subscription.getSubscriptionEndDate());
        }

        UserDetailsAdminInfoDTO adminInfoDTO = new UserDetailsAdminInfoDTO(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                subscriptionName,
                subscriptionEndDate
        );

        model.addAttribute("user", adminInfoDTO);
        model.addAttribute("stores", stores); // Передаём магазины
        model.addAttribute("storeParcels", storeParcels); // Передаём посылки по магазинам
        return "admin/user-details";
    }


    @PostMapping("/users/{usersId}/role-update")
    public String updateUserRole(@PathVariable Long usersId,
                                 @RequestParam("role") String role) {
        userService.updateUserRole(usersId, role);
        return "redirect:/admin/users/" + usersId;
    }

    @PostMapping("/users/{userId}/change-subscription")
    public String changeUserSubscription(@PathVariable Long userId,
                                         @RequestParam("subscriptionPlan") String subscriptionPlan,
                                         @RequestParam(value = "months", required = false) Integer months) {
        // Проверяем, если месяц не передан, ставим значение по умолчанию 1
        if ("PREMIUM".equalsIgnoreCase(subscriptionPlan)) {
            if (months == null) {
                months = 1;
            }
            subscriptionService.upgradeOrExtendSubscription(userId, months); // Продление платной подписки
        } else {
            subscriptionService.changeSubscription(userId, subscriptionPlan, null);  // Смена подписки
        }
        return "redirect:/admin/users/" + userId;
    }

    /**
     * Triggers aggregation of weekly, monthly and yearly statistics for yesterday.
     */
    @PostMapping("/aggregate-stats")
    public String triggerAggregation() {
        statsAggregationService.aggregateYesterday();
        return "redirect:/admin";
    }

}