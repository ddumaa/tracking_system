package com.project.tracking_system.controller;

import com.project.tracking_system.entity.User;
import com.project.tracking_system.service.TrackParcelService;
import com.project.tracking_system.service.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;

/**
 * Административный контроллер
 *
 * @author Dmitriy Anisimov
 * @date 07.02.2025
 */
@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class AdminController {

    private final static Logger logger = LoggerFactory.getLogger(AdminController.class);

    private final UserService userService;
    private final TrackParcelService trackParcelService;

    @Autowired
    public AdminController(UserService userService, TrackParcelService trackParcelService) {
        this.userService = userService;
        this.trackParcelService = trackParcelService;
    }

    @GetMapping()
    public String adminDashboard(Model model) {
        long totalUsers = userService.countUsers();
        long paidUsers = userService.countPaidUsers();
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

    @GetMapping("/users/{usersEmail}")
    public String getUserDetails(@PathVariable String usersEmail, Model model) {
        User user = userService.findByUser(usersEmail)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        var parcels = trackParcelService.findAllByUserTracks(usersEmail);

        model.addAttribute("user", user);
        model.addAttribute("parcels", parcels);
        return "admin/user-details";
    }

    @PostMapping("/users/{usersEmail}/role-update")
    public String updateUserRole(@PathVariable String usersEmail,
                                 @RequestParam("role") String role) {
        userService.updateUserRole(usersEmail, role);
        return "redirect:/admin/users/" + usersEmail;
    }

    @PostMapping("/users/{usersEmail}/extend-role")
    public String extendUserRole(@PathVariable String usersEmail,
                                 @RequestParam int months) {
        userService.upgradeOrExtendRole(usersEmail, months);
        return "redirect:/admin/users/" + usersEmail;
    }
}