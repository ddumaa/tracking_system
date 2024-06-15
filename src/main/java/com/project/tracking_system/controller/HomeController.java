package com.project.tracking_system.controller;

import com.project.tracking_system.dto.UserRegistrationDTO;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.exception.UsernameAlreadyExistsException;
import jakarta.validation.Valid;
import com.project.tracking_system.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Optional;


@Controller
@RequestMapping("/")
public class HomeController {

    private final UserService userService;

    @Autowired
    public HomeController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public String home() {
        return "home";
    }

    @GetMapping("/registration")
    public String registration(Model model) {
        model.addAttribute("userDTO", new UserRegistrationDTO());
        return "registration";
    }

    @PostMapping("/registration")
    public String registration(@Valid @ModelAttribute("userDTO") UserRegistrationDTO userDTO, BindingResult result, Model model) {
        if (result.hasErrors()) {
            return "registration";
        }
        if (!userDTO.getPassword().equals(userDTO.getConfirmPassword())) {
            result.rejectValue("confirmPassword", "password.mismatch", "Пароли не совпадают");
            return "registration";
        }
        try {
            Optional<User> newUser = userService.add(userDTO);
            String username = newUser.get().getUsername();
            userService.autoLogin(username);
            model.addAttribute("successMessage", "Регистрация успешна. Пожалуйста, войдите в систему.");
            return "redirect:/";
        }
        catch (UsernameAlreadyExistsException e) {
            model.addAttribute("errorMessage", "Имя пользователя уже существует, пожалуйста, выберите другое");
            return "registration";
        }
        catch (Exception e) {
            model.addAttribute("errorMessage", "Ошибка регистрации пользователя: " + e.getMessage());
            return "registration";
        }
    }

    @GetMapping("/login")
    public String login(){
        return "login";
    }

}
