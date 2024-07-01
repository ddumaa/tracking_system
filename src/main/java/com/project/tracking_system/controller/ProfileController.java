package com.project.tracking_system.controller;

import com.project.tracking_system.dto.UserSettingsDTO;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
        user.ifPresent(user1 -> model.addAttribute("username", user1.getEmail()));
        return "profile";
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("userSettingsDTO", new UserSettingsDTO());
        return "settings";
    }

    @PostMapping("/settings")
    public String settings(Model model, @Valid @ModelAttribute("userSettingsDTO") UserSettingsDTO userSettingsDTO, BindingResult result) {
        if (result.hasErrors()) {
            return "settings";
        }
        if (!userSettingsDTO.getNewPassword().equals(userSettingsDTO.getConfirmPassword())) {
            result.rejectValue("confirmPassword", "password.mismatch", "Пароли не совпадают");
            return "settings";
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        try {
            userService.changePassword(email, userSettingsDTO);
            model.addAttribute("notification", "Пароль успешно изменен");
            return "settings";
        } catch (IllegalArgumentException e) {
            result.rejectValue("currentPassword", "password.incorrect", e.getMessage());
        }
        return "settings";
    }

}
