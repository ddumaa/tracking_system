package com.project.tracking_system.service.admin;

import com.project.tracking_system.dto.SubscriptionPlanDTO;
import com.project.tracking_system.dto.SubscriptionLimitsDTO;
import com.project.tracking_system.entity.SubscriptionPlan;
import com.project.tracking_system.entity.SubscriptionLimits;
import com.project.tracking_system.entity.SubscriptionFeature;
import com.project.tracking_system.model.subscription.FeatureKey;
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
     * @return список планов в виде DTO
     */
    @Transactional(readOnly = true)
    public List<SubscriptionPlanDTO> getAllPlans() {
        return planRepository.findAllByOrderByPositionAsc().stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Преобразовать сущность плана в DTO.
     *
     * @param plan сущность плана
     * @return DTO плана
     */
    public SubscriptionPlanDTO toDto(SubscriptionPlan plan) {
        SubscriptionLimits limits = plan.getLimits();
        SubscriptionLimitsDTO limitsDto = new SubscriptionLimitsDTO();
        if (limits != null) {
            limitsDto.setMaxTracksPerFile(limits.getMaxTracksPerFile());
            limitsDto.setMaxSavedTracks(limits.getMaxSavedTracks());
            limitsDto.setMaxTrackUpdates(limits.getMaxTrackUpdates());
            limitsDto.setMaxStores(limits.getMaxStores());
        }
        // флаги функций определяем через список features
        limitsDto.setAllowBulkUpdate(plan.isFeatureEnabled(FeatureKey.BULK_UPDATE));
        limitsDto.setAllowAutoUpdate(plan.isFeatureEnabled(FeatureKey.AUTO_UPDATE));
        limitsDto.setAllowTelegramNotifications(plan.isFeatureEnabled(FeatureKey.TELEGRAM_NOTIFICATIONS));
        limitsDto.setAllowCustomBot(plan.isFeatureEnabled(FeatureKey.CUSTOM_BOT));

        SubscriptionPlanDTO dto = new SubscriptionPlanDTO();
        dto.setId(plan.getId());
        dto.setCode(plan.getCode());
        dto.setName(plan.getName());
        dto.setActive(plan.getActive());
        dto.setMonthlyPrice(plan.getMonthlyPrice());
        dto.setAnnualPrice(plan.getAnnualPrice());
        dto.setLimits(limitsDto);
        return dto;
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
        plan.setActive(dto.getActive());

        SubscriptionLimits limits = plan.getLimits();
        if (limits == null) {
            limits = new SubscriptionLimits();
            limits.setSubscriptionPlan(plan);
            plan.setLimits(limits);
        }

        SubscriptionLimitsDTO l = dto.getLimits();
        if (l != null) {
            limits.setMaxTracksPerFile(l.getMaxTracksPerFile());
            limits.setMaxSavedTracks(l.getMaxSavedTracks());
            limits.setMaxTrackUpdates(l.getMaxTrackUpdates());
            limits.setMaxStores(l.getMaxStores());

            // обновляем признаки доступности функций
            setFeature(plan, FeatureKey.BULK_UPDATE, l.isAllowBulkUpdate());
            setFeature(plan, FeatureKey.AUTO_UPDATE, l.isAllowAutoUpdate());
            setFeature(plan, FeatureKey.TELEGRAM_NOTIFICATIONS, l.isAllowTelegramNotifications());
            setFeature(plan, FeatureKey.CUSTOM_BOT, l.isAllowCustomBot());
        }

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
        // устанавливаем позицию в конец списка
        int nextPos = planRepository.findMaxPosition().orElse(0) + 1;
        plan.setPosition(nextPos);
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
     * Установить состояние функции в тарифном плане.
     *
     * @param plan    тарифный план
     * @param key     ключ функции
     * @param enabled состояние функции
     */
    private void setFeature(SubscriptionPlan plan, FeatureKey key, boolean enabled) {
        List<SubscriptionFeature> list = plan.getFeatures();
        if (list == null) {
            list = new java.util.ArrayList<>();
            plan.setFeatures(list);
        }
        for (SubscriptionFeature feature : list) {
            if (feature.getFeatureKey() == key) {
                feature.setEnabled(enabled);
                return;
            }
        }
        SubscriptionFeature feature = new SubscriptionFeature();
        feature.setFeatureKey(key);
        feature.setEnabled(enabled);
        feature.setSubscriptionPlan(plan);
        list.add(feature);
    }

    /**
     * Возвращает бесплатный тарифный план с нулевой стоимостью.
     *
     * @return бесплатный план
     * @throws IllegalStateException если план не найден в базе
     */
    @Transactional(readOnly = true)
    public SubscriptionPlan getFreePlan() {
        return planRepository.findByCode("FREE")
                .orElseGet(() -> planRepository.findAllByOrderByPositionAsc().stream()
                        .filter(p -> !p.isPaid())
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("Бесплатный план не найден")));
    }

    /**
     * Проверяет, является ли указанный тариф платным.
     *
     * @param code код тарифа
     * @return {@code true}, если стоимость плана больше нуля
     */
    @Transactional(readOnly = true)
    public boolean isPaidPlan(String code) {
        return planRepository.findByCode(code)
                .map(SubscriptionPlan::isPaid)
                .orElse(false);
    }

    /**
     * Изменить активность тарифного плана.
     *
     * @param id     идентификатор плана
     * @param active новый статус
     */
    @Transactional
    public void setPlanActive(Long id, boolean active) {
        SubscriptionPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("План не найден"));
        plan.setActive(active);
        log.info("Статус плана {} изменен на {}", id, active);
    }

    /**
     * Удалить тарифный план.
     *
     * @param id идентификатор плана
     */
    @Transactional
    public void deletePlan(Long id) {
        planRepository.deleteById(id);
        log.info("Удален тарифный план {}", id);
    }

    /**
     * Переместить тарифный план вверх по позиции.
     *
     * @param id идентификатор плана
     */
    @Transactional
    public void movePlanUp(Long id) {
        List<SubscriptionPlan> plans = planRepository.findAllByOrderByPositionAsc();

        for (int i = 1; i < plans.size(); i++) {
            SubscriptionPlan current = plans.get(i);
            if (current.getId().equals(id)) {
                SubscriptionPlan previous = plans.get(i - 1);
                // меняем позиции местами
                int pos = current.getPosition();
                current.setPosition(previous.getPosition());
                previous.setPosition(pos);
                planRepository.save(previous);
                planRepository.save(current);
                break;
            }
        }
    }

    /**
     * Переместить тарифный план вниз по позиции.
     *
     * @param id идентификатор плана
     */
    @Transactional
    public void movePlanDown(Long id) {
        List<SubscriptionPlan> plans = planRepository.findAllByOrderByPositionAsc();

        for (int i = 0; i < plans.size() - 1; i++) {
            SubscriptionPlan current = plans.get(i);
            if (current.getId().equals(id)) {
                SubscriptionPlan next = plans.get(i + 1);
                // меняем позиции местами
                int pos = current.getPosition();
                current.setPosition(next.getPosition());
                next.setPosition(pos);
                planRepository.save(current);
                planRepository.save(next);
                break;
            }
        }
    }
}
