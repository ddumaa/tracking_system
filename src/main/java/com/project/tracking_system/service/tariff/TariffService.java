package com.project.tracking_system.service.tariff;

import com.project.tracking_system.dto.SubscriptionPlanDTO;
import com.project.tracking_system.entity.SubscriptionPlan;
import com.project.tracking_system.repository.SubscriptionPlanRepository;
import com.project.tracking_system.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Сервис работы с тарифами пользователей.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TariffService {

    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionService subscriptionService;

    /**
     * Возвращает список тарифных планов.
     *
     * @return список планов в виде DTO
     */
    public List<SubscriptionPlanDTO> getAllPlans() {
        return planRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Выполняет апгрейд пользователя до премиум-тарифа или продлевает его.
     *
     * @param userId идентификатор пользователя
     * @param months количество месяцев продления
     */
    @Transactional
    public void upgradeUser(Long userId, int months) {
        subscriptionService.upgradeOrExtendSubscription(userId, months);
    }

    private SubscriptionPlanDTO toDto(SubscriptionPlan plan) {
        SubscriptionPlanDTO dto = new SubscriptionPlanDTO();
        dto.setCode(plan.getCode());
        dto.setMaxTracksPerFile(plan.getMaxTracksPerFile());
        dto.setMaxSavedTracks(plan.getMaxSavedTracks());
        dto.setMaxTrackUpdates(plan.getMaxTrackUpdates());
        dto.setAllowBulkUpdate(plan.isAllowBulkUpdate());
        dto.setMaxStores(plan.getMaxStores());
        dto.setAllowTelegramNotifications(Boolean.TRUE.equals(plan.getAllowTelegramNotifications()));
        return dto;
    }
}
