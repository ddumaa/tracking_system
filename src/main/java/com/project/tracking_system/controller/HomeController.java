package com.project.tracking_system.controller;

import com.project.tracking_system.dto.EvroTrackInfoListDTO;
import com.project.tracking_system.dto.TrackParcelDTO;
import com.project.tracking_system.dto.UserRegistrationDTO;
import com.project.tracking_system.exception.UserAlreadyExistsException;
import com.project.tracking_system.maper.JsonEvroTrackingResponseMapper;
import com.project.tracking_system.model.jsonResponseModel.JsonEvroTrackingResponse;
import com.project.tracking_system.service.JsonService.JsonEvroTrackingService;
import com.project.tracking_system.service.StatusIconService;
import com.project.tracking_system.service.TrackParcelService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import com.project.tracking_system.service.UserService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@Controller
@RequestMapping("/")
public class HomeController {

    private final UserService userService;
    private final JsonEvroTrackingService jsonEvroTrackingService;
    private final TrackParcelService trackParcelService;
    private final StatusIconService statusIconService;
    private final JsonEvroTrackingResponseMapper jsonEvroTrackingResponseMapper;

    @Autowired
    public HomeController(UserService userService, JsonEvroTrackingService jsonEvroTrackingService,
                          TrackParcelService trackParcelService, StatusIconService statusIconService,
                          JsonEvroTrackingResponseMapper jsonEvroTrackingResponseMapper) {
        this.userService = userService;
        this.jsonEvroTrackingService = jsonEvroTrackingService;
        this.trackParcelService = trackParcelService;
        this.statusIconService = statusIconService;
        this.jsonEvroTrackingResponseMapper = jsonEvroTrackingResponseMapper;

    }

    @GetMapping
    public String home() {
        return "home";
    }

    @PostMapping
    public String home(@ModelAttribute("number") String number, Model model, HttpServletRequest request) {

        HttpSession session = request.getSession();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        model.addAttribute("number", number);
        try {
            EvroTrackInfoListDTO evroTrackInfoListDTO = jsonEvroTrackingResponseMapper.mapJsonEvroTrackingResponseToDTO(jsonEvroTrackingService.getJson(number));
            model.addAttribute("jsonEvroTrackingResponse", evroTrackInfoListDTO);
            if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
                model.addAttribute("authenticatedUser", auth.getName());
                session.setAttribute("userSession", auth.getName());
                trackParcelService.save(number, evroTrackInfoListDTO, auth.getName());
            } else {
                session.removeAttribute("userSession");
                model.addAttribute("authenticatedUser", null);
            }
            return "home";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "home";
        }
    }

    @GetMapping("/history")
    public String history(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        trackParcelService.updateHistory(auth.getName());
        List<TrackParcelDTO> byUserTrack = trackParcelService.findByUserTracks(auth.getName());
        if (byUserTrack.isEmpty()) {
            model.addAttribute("trackParcelNotification", "Отслеживаемых посылок нет");
        } else {
            model.addAttribute("trackParcelDTO", byUserTrack);
            model.addAttribute("statusIconService", statusIconService);
        }
        return "history";
    }

    @GetMapping("/history/{itemNumber}")
    public String history(Model model, @PathVariable("itemNumber") String itemNumber) {
        EvroTrackInfoListDTO evroTrackInfoListDTO = jsonEvroTrackingResponseMapper.mapJsonEvroTrackingResponseToDTO(jsonEvroTrackingService.getJson(itemNumber));
        model.addAttribute("jsonEvroTracking", evroTrackInfoListDTO);
        model.addAttribute("itemNumber", itemNumber);
        return "partials/history-info";
    }

    @PostMapping("/history-update")
    public String history(){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        trackParcelService.updateHistory(auth.getName());
        return "redirect:/history";
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
            userService.save(userDTO);
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