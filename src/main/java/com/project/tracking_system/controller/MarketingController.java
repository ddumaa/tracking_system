package com.project.tracking_system.controller;

import com.project.tracking_system.dto.SubscriptionPlanViewDTO;
import com.project.tracking_system.dto.UserProfileDTO;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.service.tariff.TariffService;
import com.project.tracking_system.service.user.UserService;
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
 * Контроллер маркетинговых страниц сайта.
 */
@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("/")
public class MarketingController {

    /** Сервис управления тарифами. */
    private final TariffService tariffService;

    /** Сервис пользователей для получения информации о текущем плане. */
    private final UserService userService;

    /**
     * Отображает публичную домашнюю страницу.
     *
     * @return имя представления маркетинговой домашней страницы
     */
    @GetMapping
    public String home() {
        return "marketing/home";
    }

    /**
     * Отображает страницу с возможностями сервиса.
     *
     * @return имя представления страницы с описанием функций
     */
    @GetMapping("features")
    public String features() {
        return "marketing/features";
    }

    /**
     * Отображает страницу с тарифами.
     *
     * @param model модель представления
     * @param user  текущий пользователь (может быть {@code null})
     * @return имя представления маркетинговой страницы тарифов
     */
    @GetMapping("pricing")
    public String pricing(Model model, @AuthenticationPrincipal User user) {
        Long userId = user != null ? user.getId() : null;
        Integer userPlanPosition = null;
        if (userId != null) {
            // Пользователь авторизован, получаем информацию о его подписке
            UserProfileDTO profile = userService.getUserProfile(userId);
            model.addAttribute("userProfile", profile);

            if (profile.getSubscriptionCode() != null) {
                userPlanPosition = tariffService.getPlanPositionByCode(profile.getSubscriptionCode());
            }
        }

        List<SubscriptionPlanViewDTO> plans = tariffService.getAllPlans();
        model.addAttribute("plans", plans);
        model.addAttribute("userPlanPosition", userPlanPosition);
        return "marketing/pricing";
    }

    /**
     * Выполняет апгрейд подписки пользователя до премиум-плана.
     *
     * @param months срок продления в месяцах
     * @param user   текущий пользователь
     * @return редирект на страницу профиля
     */
    @PostMapping("pricing/upgrade")
    public String upgrade(@RequestParam(value = "months", defaultValue = "1") int months,
                          @AuthenticationPrincipal User user) {
        Long userId = user.getId();
        if (userId == null) {
            return "redirect:/auth/login";
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
     * @param planCode код тарифа
     * @param months   срок в месяцах
     * @param user     текущий пользователь
     * @return редирект на страницу профиля
     */
    @PostMapping("pricing/buy")
    public String buy(@RequestParam("plan") String planCode,
                      @RequestParam(value = "months", defaultValue = "1") int months,
                      @AuthenticationPrincipal User user) {
        Long userId = user.getId();
        if (userId == null) {
            return "redirect:/auth/login";
        }
        if (months <= 0) {
            months = 1;
        }
        tariffService.buyPlan(userId, planCode, months);
        return "redirect:/app/profile";
    }

    /**
     * Отображает страницу с пользовательским соглашением.
     *
     * @return имя представления с условиями использования
     */
    @GetMapping("terms")
    public String terms() {
        return "marketing/terms";
    }

    /**
     * Отображает страницу политики конфиденциальности.
     *
     * @return имя представления политики конфиденциальности
     */
    @GetMapping("privacy")
    public String privacy() {
        return "marketing/privacy";
    }

    /**
     * Отображает страницу с часто задаваемыми вопросами.
     *
     * @return имя представления страницы с ответами на вопросы
     */
    @GetMapping("faq")
    public String faq() {
        return "marketing/faq";
    }

    /**
     * Отображает страницу с информацией о компании.
     *
     * @return имя представления страницы "О нас"
     */
    @GetMapping("about")
    public String about() {
        return "marketing/about";
    }

    /**
     * Отображает страницу с контактной информацией.
     *
     * @return имя представления страницы с контактами
     */
    @GetMapping("contacts")
    public String contacts() {
        return "marketing/contacts";
    }
}
