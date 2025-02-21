package com.project.tracking_system.controller;

import com.project.tracking_system.entity.User;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.service.TrackParcelService;
import com.project.tracking_system.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
        model.addAttribute("users", users);
        return "admin/user-list";
    }

    @GetMapping("/users/{usersId}")
    public String getUserDetails(@PathVariable Long usersId, Model model) {
        User user = userService.findUserById(usersId);
        var parcels = trackParcelService.findAllByUserTracks(usersId);

        model.addAttribute("user", user);
        model.addAttribute("parcels", parcels);
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

}