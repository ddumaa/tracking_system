package com.project.tracking_system.service.admin;

import com.project.tracking_system.repository.SubscriptionPlanRepository;
import com.project.tracking_system.entity.SubscriptionPlan;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Сервис предоставления системной информации для админ-панели.
 */
@Service
@Slf4j
@Getter
public class AppInfoService {

    private final SubscriptionPlanRepository planRepository;
    private final String applicationVersion;
    private final boolean telegramWebhookEnabled;

    /**
     * Создаёт сервис с внедрением настроек приложения.
     *
     * @param planRepository            репозиторий тарифов
     * @param version                   версия приложения
     * @param webhookEnabled            статус вебхука Telegram
     */
    public AppInfoService(SubscriptionPlanRepository planRepository,
                          @Value("${application.version:unknown}") String version,
                          @Value("${telegram.webhook.enabled:false}") boolean webhookEnabled) {
        this.planRepository = planRepository;
        this.applicationVersion = version;
        this.telegramWebhookEnabled = webhookEnabled;
    }


    /**
     * Получить список тарифных планов с лимитами.
     *
     * @return список тарифных планов
     */
    public List<SubscriptionPlan> getPlans() {
        return planRepository.findAllByOrderByPositionAsc();
    }
}
