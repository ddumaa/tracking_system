package com.project.tracking_system.controller;

import com.project.tracking_system.dto.UserSettingsDTO;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.service.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Optional;


@Controller
@RequestMapping("/profile")
public class ProfileController {

    private final UserService userService;

    @Autowired
    public ProfileController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public String profile(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        Optional<User> user = userService.findByUser(username);
        user.ifPresent(u -> model.addAttribute("username", u.getEmail()));
        return "profile";
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("userSettingsDTO", new UserSettingsDTO());
        return "settings :: form";
    }

    @PostMapping("/settings")
    public String settings(Model model, @Valid @ModelAttribute("userSettingsDTO") UserSettingsDTO userSettingsDTO, BindingResult result) {
        if (result.hasErrors()) {
            return "settings :: form";
        }
        if (!userSettingsDTO.getNewPassword().equals(userSettingsDTO.getConfirmPassword())) {
            result.rejectValue("confirmPassword", "password.mismatch", "Пароли не совпадают");
            return "settings :: form";
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        try {
            userService.changePassword(email, userSettingsDTO);
            model.addAttribute("notification", "Пароль успешно изменен");
            return "settings :: form";
        } catch (IllegalArgumentException e) {
            result.rejectValue("currentPassword", "password.incorrect", e.getMessage());
        }
        return "settings :: form";
    }

    @PostMapping("/settings/delete")
    public String delete(HttpServletRequest request, HttpServletResponse response) {
        userService.deleteUser();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            new SecurityContextLogoutHandler().logout(request, response, authentication);
        }
        return "redirect:/";
    }

}