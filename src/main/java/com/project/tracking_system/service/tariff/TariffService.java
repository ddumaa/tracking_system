package com.project.tracking_system.service.tariff;

import com.project.tracking_system.dto.SubscriptionPlanDTO;
import com.project.tracking_system.dto.SubscriptionPlanViewDTO;
import com.project.tracking_system.entity.SubscriptionPlan;
import com.project.tracking_system.repository.SubscriptionPlanRepository;
import com.project.tracking_system.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    public List<SubscriptionPlanViewDTO> getAllPlans() {
        return planRepository.findAll()
                .stream()
                .map(this::toViewDto)
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

    public SubscriptionPlanViewDTO toViewDto(SubscriptionPlan plan) {
        BigDecimal monthly = plan.getMonthlyPrice();
        BigDecimal annual = plan.getAnnualPrice();

        String monthlyLabel = (monthly != null && monthly.compareTo(BigDecimal.ZERO) > 0)
                ? monthly.setScale(2, RoundingMode.HALF_UP) + " BYN/мес"
                : null;

        String annualLabel = (annual != null && annual.compareTo(BigDecimal.ZERO) > 0)
                ? annual.setScale(2, RoundingMode.HALF_UP) + " BYN/год"
                : null;

        String fullAnnualPriceLabel = null;
        String discountLabel = null;

        if (monthly != null && annual != null
                && monthly.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal fullPrice = monthly.multiply(BigDecimal.valueOf(12));
            BigDecimal discount = fullPrice.subtract(annual);

            if (fullPrice.compareTo(BigDecimal.ZERO) > 0 && discount.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal discountPercent = discount.multiply(BigDecimal.valueOf(100))
                        .divide(fullPrice, 0, RoundingMode.HALF_UP);

                fullAnnualPriceLabel = fullPrice.setScale(2, RoundingMode.HALF_UP) + " BYN";
                discountLabel = "выгода −" + discountPercent.intValue() + "%";
            }
        }

        return new SubscriptionPlanViewDTO(
                plan.getCode(),
                plan.getMaxTracksPerFile(),
                plan.getMaxSavedTracks(),
                plan.getMaxTrackUpdates(),
                plan.isAllowBulkUpdate(),
                plan.getMaxStores(),
                Boolean.TRUE.equals(plan.getAllowTelegramNotifications()),
                monthlyLabel,
                annualLabel,
                fullAnnualPriceLabel,
                discountLabel
        );
    }

}