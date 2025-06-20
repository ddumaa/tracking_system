package com.project.tracking_system.repository;

import com.project.tracking_system.entity.SubscriptionCode;
import com.project.tracking_system.entity.SubscriptionPlan;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тестирование репозитория тарифных планов.
 */
@DataJpaTest
class SubscriptionPlanRepositoryTest {

    @Autowired
    private SubscriptionPlanRepository repository;

    /**
     * Проверяет сохранение и получение плана с ценами.
     */
    @Test
    void createAndFindWithPrices() {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setCode(SubscriptionCode.PREMIUM);
        plan.setMaxTracksPerFile(1);
        plan.setMaxSavedTracks(1);
        plan.setMaxTrackUpdates(1);
        plan.setAllowBulkUpdate(true);
        plan.setMaxStores(1);
        plan.setAllowTelegramNotifications(false);
        plan.setMonthlyPrice(new BigDecimal("9.99"));
        plan.setAnnualPrice(new BigDecimal("99.99"));

        SubscriptionPlan saved = repository.save(plan);
        SubscriptionPlan found = repository.findById(saved.getId()).orElseThrow();

        assertEquals(new BigDecimal("9.99"), found.getMonthlyPrice());
        assertEquals(new BigDecimal("99.99"), found.getAnnualPrice());
    }
}
