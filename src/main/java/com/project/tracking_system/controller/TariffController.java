package com.project.tracking_system.controller;

import com.project.tracking_system.dto.SubscriptionPlanViewDTO;
import com.project.tracking_system.dto.UserProfileDTO;
import com.project.tracking_system.service.tariff.TariffService;
import com.project.tracking_system.service.user.UserService;
import com.project.tracking_system.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequestMapping("/app/tariffs")
public class TariffController {

    private final TariffService tariffService;
    private final UserService userService;

    /**
     * Отображает страницу с тарифными планами.
     *
     * @param model модель представления
     * @param user  текущий пользователь (может быть {@code null})
     * @return имя шаблона страницы тарифов
     */
    @GetMapping
    public String tariffs(Model model, @AuthenticationPrincipal User user) {
        Long userId = user != null ? user.getId() : null;
        Integer userPlanPosition = null;
        if (userId != null) {
            // пользователь авторизован
            // загружаем профиль пользователя для отображения тарифа
            UserProfileDTO profile = userService.getUserProfile(userId);
            model.addAttribute("userProfile", profile);

            if (profile.getSubscriptionCode() != null) {
                userPlanPosition = tariffService.getPlanPositionByCode(profile.getSubscriptionCode());
            }
        }
        List<SubscriptionPlanViewDTO> plans = tariffService.getAllPlans();
        model.addAttribute("plans", plans);
        model.addAttribute("userPlanPosition", userPlanPosition);
        return "app/tariffs";
    }

    /**
     * Выполняет апгрейд подписки пользователя до премиум-тарифа.
     *
     * @param months количество месяцев продления
     * @param user   текущий пользователь
     * @return редирект на страницу профиля
     */
    @PostMapping("/upgrade")
    public String upgrade(@RequestParam(value = "months", defaultValue = "1") int months,
                          @AuthenticationPrincipal User user) {
        Long userId = user.getId();
        if (userId == null) {
            return "redirect:/login";
        }
        if (months <= 0) {
            months = 1;
        }
        tariffService.upgradeUser(userId, months);
        return "redirect:/app/profile";
    }

    /**
     * Покупает выбранный тарифный план для пользователя.
     *
     * @param planCode код плана
     * @param months   срок в месяцах
     * @param user     текущий пользователь
     * @return редирект на страницу профиля
     */
    @PostMapping("/buy")
    public String buy(@RequestParam("plan") String planCode,
                      @RequestParam(value = "months", defaultValue = "1") int months,
                      @AuthenticationPrincipal User user) {
        Long userId = user.getId();
        if (userId == null) {
            return "redirect:/login";
        }
        if (months <= 0) {
            months = 1;
        }
        tariffService.buyPlan(userId, planCode, months);
        return "redirect:/app/profile";
    }
}
