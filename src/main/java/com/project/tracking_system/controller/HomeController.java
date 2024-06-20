package com.project.tracking_system.controller;

import com.project.tracking_system.dto.UserRegistrationDTO;
import com.project.tracking_system.exception.UserAlreadyExistsException;
import com.project.tracking_system.model.jsonResponseModel.JsonEvroTrackingResponse;
import com.project.tracking_system.service.JsonService.JsonEvroTrackingService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import com.project.tracking_system.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;


@Controller
@RequestMapping("/")
public class HomeController {

    private final UserService userService;
    private final JsonEvroTrackingService jsonEvroTrackingService;

    @Autowired
    public HomeController(UserService userService, JsonEvroTrackingService jsonEvroTrackingService) {
        this.userService = userService;
        this.jsonEvroTrackingService = jsonEvroTrackingService;
    }

    @GetMapping
    public String home() {
        return "home";
    }

    @PostMapping
    public String home(@RequestParam("number") String number, Model model, HttpServletRequest request) {

        HttpSession session = request.getSession();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        model.addAttribute("number", number);
        System.out.println(number);

        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            model.addAttribute("authenticatedUser", auth.getName());
            session.setAttribute("userSession", auth.getName());
        } else {
            session.removeAttribute("userSession");
            model.addAttribute("authenticatedUser", null);
        }
        try {
            JsonEvroTrackingResponse jsonEvroTrackingResponse = jsonEvroTrackingService.getJson(number);
            model.addAttribute("jsonEvroTrackingResponse", jsonEvroTrackingResponse);
            return "home";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "home";
        }
    }


    @GetMapping("/registration")
    public String registration(@ModelAttribute("userDTO") UserRegistrationDTO userRegistrationDTO, Model model) {
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
            userService.add(userDTO);
            //userService.autoLogin(userDTO.getUsername());
            model.addAttribute("successMessage", "Регистрация успешна. Пожалуйста, войдите в систему.");
            return "redirect:/";
        }
        catch (UserAlreadyExistsException e) {
            model.addAttribute("errorMessage", "Данная почта уже используется, авторизуйтесь или используйте другую почту");
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
