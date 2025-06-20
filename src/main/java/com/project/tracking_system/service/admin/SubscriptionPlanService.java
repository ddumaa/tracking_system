package com.project.tracking_system.service.admin;

import com.project.tracking_system.dto.SubscriptionPlanDTO;
import com.project.tracking_system.entity.SubscriptionPlan;
import com.project.tracking_system.repository.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Сервис управления тарифными планами.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionPlanService {

    private final SubscriptionPlanRepository planRepository;

    /**
     * Получить список всех тарифных планов.
     *
     * @return список планов
     */
    public List<SubscriptionPlan> getAllPlans() {
        return planRepository.findAll();
    }

    /**
     * Создать новый тарифный план.
     *
     * @param dto DTO с параметрами плана
     * @return созданный план
     */
    public SubscriptionPlan createPlan(SubscriptionPlanDTO dto) {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setCode(dto.getCode());
        plan.setMaxTracksPerFile(dto.getMaxTracksPerFile());
        plan.setMaxSavedTracks(dto.getMaxSavedTracks());
        plan.setMaxTrackUpdates(dto.getMaxTrackUpdates());
        plan.setAllowBulkUpdate(dto.isAllowBulkUpdate());
        plan.setMaxStores(dto.getMaxStores());
        plan.setAllowTelegramNotifications(dto.isAllowTelegramNotifications());
        log.info("Создан тарифный план {}", dto.getCode());
        return planRepository.save(plan);
    }

    /**
     * Обновить существующий тарифный план.
     *
     * @param id  идентификатор плана
     * @param dto новые параметры
     * @return обновлённый план
     */
    public SubscriptionPlan updatePlan(Long id, SubscriptionPlanDTO dto) {
        SubscriptionPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("План не найден"));
        plan.setCode(dto.getCode());
        plan.setMaxTracksPerFile(dto.getMaxTracksPerFile());
        plan.setMaxSavedTracks(dto.getMaxSavedTracks());
        plan.setMaxTrackUpdates(dto.getMaxTrackUpdates());
        plan.setAllowBulkUpdate(dto.isAllowBulkUpdate());
        plan.setMaxStores(dto.getMaxStores());
        plan.setAllowTelegramNotifications(dto.isAllowTelegramNotifications());
        log.info("Обновлен тарифный план {}", id);
        return planRepository.save(plan);
    }
}
