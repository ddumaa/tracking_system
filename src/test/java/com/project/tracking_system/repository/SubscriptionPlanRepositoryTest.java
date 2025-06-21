package com.project.tracking_system.repository;

import com.project.tracking_system.entity.SubscriptionPlan;
import com.project.tracking_system.entity.SubscriptionLimits;
import com.project.tracking_system.entity.SubscriptionFeature;
import com.project.tracking_system.model.subscription.FeatureKey;
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
        plan.setCode("PREMIUM");

        SubscriptionLimits limits = new SubscriptionLimits();
        limits.setSubscriptionPlan(plan);
        limits.setMaxTracksPerFile(1);
        limits.setMaxSavedTracks(1);
        limits.setMaxTrackUpdates(1);
        limits.setMaxStores(1);
        plan.setLimits(limits);

        SubscriptionFeature feature = new SubscriptionFeature();
        feature.setFeatureKey(FeatureKey.BULK_UPDATE);
        feature.setEnabled(true);
        feature.setSubscriptionPlan(plan);
        plan.setFeatures(java.util.List.of(feature));

        plan.setMonthlyPrice(new BigDecimal("9.99"));
        plan.setAnnualPrice(new BigDecimal("99.99"));

        SubscriptionPlan saved = repository.save(plan);
        SubscriptionPlan found = repository.findById(saved.getId()).orElseThrow();

        assertEquals(new BigDecimal("9.99"), found.getMonthlyPrice());
        assertEquals(new BigDecimal("99.99"), found.getAnnualPrice());
    }

    /**
     * Проверяет поиск плана по его коду.
     */
    @Test
    void findByCodeReturnsPlan() {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setCode("FREE");

        SubscriptionLimits limits = new SubscriptionLimits();
        limits.setSubscriptionPlan(plan);
        limits.setMaxTracksPerFile(1);
        limits.setMaxSavedTracks(1);
        limits.setMaxTrackUpdates(1);
        limits.setMaxStores(1);
        plan.setLimits(limits);

        SubscriptionFeature feature = new SubscriptionFeature();
        feature.setFeatureKey(FeatureKey.BULK_UPDATE);
        feature.setEnabled(false);
        feature.setSubscriptionPlan(plan);
        plan.setFeatures(java.util.List.of(feature));

        repository.save(plan);

        SubscriptionPlan found = repository.findByCode("FREE").orElseThrow();
        assertEquals("FREE", found.getCode());
    }
}
