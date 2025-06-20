package com.project.tracking_system.controller;

import com.project.tracking_system.dto.BreadcrumbItemDTO;
import com.project.tracking_system.dto.SubscriptionPlanDTO;
import com.project.tracking_system.service.tariff.TariffService;
import com.project.tracking_system.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Контроллер отображения тарифов и управления апгрейдом подписки.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/tariffs")
public class TariffController {

    private final TariffService tariffService;
    private final UserService userService;

    /**
     * Отображает страницу с тарифными планами.
     *
     * @param model          модель представления
     * @param authentication текущая аутентификация
     * @return имя шаблона страницы тарифов
     */
    @GetMapping
    public String tariffs(Model model, Authentication authentication) {
        Long userId = userService.extractUserId(authentication);
        if (userId != null) {
            model.addAttribute("authenticatedUser", userId);
        }
        List<SubscriptionPlanDTO> plans = tariffService.getAllPlans();
        model.addAttribute("plans", plans);
        return "tariffs";
    }

    /**
     * Выполняет апгрейд подписки пользователя до премиум-тарифа.
     *
     * @param months         количество месяцев продления
     * @param authentication текущая аутентификация
     * @return редирект на страницу профиля
     */
    @PostMapping("/upgrade")
    public String upgrade(@RequestParam(value = "months", defaultValue = "1") int months,
                          Authentication authentication) {
        Long userId = userService.extractUserId(authentication);
        if (userId == null) {
            return "redirect:/login";
        }
        if (months <= 0) {
            months = 1;
        }
        tariffService.upgradeUser(userId, months);
        return "redirect:/profile";
    }
}
