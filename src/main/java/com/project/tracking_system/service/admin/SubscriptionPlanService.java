package com.project.tracking_system.service.admin;

import com.project.tracking_system.dto.SubscriptionPlanDTO;
import com.project.tracking_system.entity.SubscriptionPlan;
import com.project.tracking_system.repository.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

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
     * Заполнить план данными из DTO.
     *
     * @param plan план для заполнения
     * @param dto  исходные данные
     */
    private void fillFromDto(SubscriptionPlan plan, SubscriptionPlanDTO dto) {
        plan.setCode(dto.getCode());
        plan.setName(dto.getName());
        plan.setDescription(dto.getDescription());
        plan.setPrice(dto.getPrice());
        plan.setDurationDays(dto.getDurationDays());
        plan.setActive(dto.getActive());
        plan.setMaxTracksPerFile(dto.getMaxTracksPerFile());
        plan.setMaxSavedTracks(dto.getMaxSavedTracks());
        plan.setMaxTrackUpdates(dto.getMaxTrackUpdates());
        plan.setAllowBulkUpdate(dto.isAllowBulkUpdate());
        plan.setMaxStores(dto.getMaxStores());
        plan.setAllowTelegramNotifications(dto.isAllowTelegramNotifications());

        BigDecimal monthly = dto.getMonthlyPrice();
        if (monthly == null || monthly.compareTo(BigDecimal.ZERO) < 0) {
            monthly = BigDecimal.ZERO;
        }
        plan.setMonthlyPrice(monthly);

        BigDecimal annual = dto.getAnnualPrice();
        if (annual == null || annual.compareTo(BigDecimal.ZERO) < 0) {
            annual = BigDecimal.ZERO;
        }
        plan.setAnnualPrice(annual);
    }

    /**
     * Создать новый тарифный план.
     *
     * @param dto DTO с параметрами плана
     * @return созданный план
     */
    @Transactional
    public SubscriptionPlan createPlan(SubscriptionPlanDTO dto) {
        SubscriptionPlan plan = new SubscriptionPlan();
        fillFromDto(plan, dto);
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
    @Transactional
    public SubscriptionPlan updatePlan(Long id, SubscriptionPlanDTO dto) {
        SubscriptionPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("План не найден"));
        fillFromDto(plan, dto);
        log.info("Обновлен тарифный план {}", id);
        return planRepository.save(plan);
    }

    /**
     * Возвращает бесплатный тарифный план с нулевой стоимостью.
     *
     * @return бесплатный план
     * @throws IllegalStateException если план не найден в базе
     */
    public SubscriptionPlan getFreePlan() {
        return planRepository.findFirstByPrice(BigDecimal.ZERO)
                .orElseThrow(() -> new IllegalStateException("Бесплатный план не найден"));
    }

    /**
     * Проверяет, является ли указанный тариф платным.
     *
     * @param code код тарифа
     * @return {@code true}, если стоимость плана больше нуля
     */
    public boolean isPaidPlan(String code) {
        return planRepository.findByCode(code)
                .map(p -> p.getPrice().compareTo(BigDecimal.ZERO) > 0)
                .orElse(false);
    }
}
