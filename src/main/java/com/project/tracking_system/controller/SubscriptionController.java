package com.project.tracking_system.controller;

import com.project.tracking_system.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Контроллер операций с подпиской пользователя.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    /**
     * Продлевает текущую подписку пользователя.
     *
     * @param userDetails данные аутентифицированного пользователя
     * @return редирект на страницу профиля
     */
    @PostMapping("/subscription/renew")
    public String renewSubscription(@AuthenticationPrincipal UserDetails userDetails) {
        subscriptionService.renewCurrentSubscription(userDetails.getUsername());
        return "redirect:/app/profile?renewed";
    }
}
