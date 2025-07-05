package com.project.tracking_system.service.tariff;

import com.project.tracking_system.dto.SubscriptionPlanViewDTO;
import com.project.tracking_system.entity.SubscriptionFeature;
import com.project.tracking_system.entity.SubscriptionLimits;
import com.project.tracking_system.entity.SubscriptionPlan;
import com.project.tracking_system.model.subscription.FeatureKey;
import com.project.tracking_system.repository.SubscriptionPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Тесты для {@link TariffService}.
 */
@ExtendWith(MockitoExtension.class)
class TariffServiceTest {

    @Mock
    private SubscriptionPlanRepository planRepository;
    @InjectMocks
    private TariffService tariffService;

    private SubscriptionPlan plan;

    @BeforeEach
    void initPlan() {
        plan = new SubscriptionPlan();
        plan.setCode("PREMIUM");
        plan.setName("Premium");
        plan.setMonthlyPrice(new BigDecimal("15"));
        plan.setAnnualPrice(new BigDecimal("150"));
        plan.setPosition(2);

        SubscriptionLimits limits = new SubscriptionLimits();
        limits.setMaxTracksPerFile(5);
        limits.setMaxSavedTracks(100);
        limits.setMaxTrackUpdates(50);
        limits.setMaxStores(3);
        limits.setSubscriptionPlan(plan);
        plan.setLimits(limits);

        SubscriptionFeature bulk = new SubscriptionFeature();
        bulk.setFeatureKey(FeatureKey.BULK_UPDATE);
        bulk.setEnabled(true);
        bulk.setSubscriptionPlan(plan);
        SubscriptionFeature auto = new SubscriptionFeature();
        auto.setFeatureKey(FeatureKey.AUTO_UPDATE);
        auto.setEnabled(true);
        auto.setSubscriptionPlan(plan);
        SubscriptionFeature telegram = new SubscriptionFeature();
        telegram.setFeatureKey(FeatureKey.TELEGRAM_NOTIFICATIONS);
        telegram.setEnabled(true);
        telegram.setSubscriptionPlan(plan);
        SubscriptionFeature custom = new SubscriptionFeature();
        custom.setFeatureKey(FeatureKey.CUSTOM_NOTIFICATIONS);
        custom.setEnabled(true);
        custom.setSubscriptionPlan(plan);
        plan.setFeatures(List.of(bulk, auto, telegram, custom));
    }

    @Test
    void getAllPlans_ReturnsDtosWithCalculatedLabels() {
        when(planRepository.findAllByOrderByPositionAsc()).thenReturn(List.of(plan));

        List<SubscriptionPlanViewDTO> dtos = tariffService.getAllPlans();

        assertEquals(1, dtos.size());
        SubscriptionPlanViewDTO dto = dtos.get(0);
        assertEquals("Premium", dto.getName());
        assertEquals(2, dto.getPosition());
        assertEquals("15.00 BYN/мес", dto.getMonthlyPriceLabel());
        assertEquals("150.00 BYN/год", dto.getAnnualPriceLabel());
        assertEquals("180.00 BYN", dto.getAnnualFullPriceLabel());
        assertEquals("выгода −17%", dto.getAnnualDiscountLabel());
    }

    @Test
    void getPlanPositionByCode_ReturnsPosition() {
        when(planRepository.findByCode("PREMIUM")).thenReturn(java.util.Optional.of(plan));

        int pos = tariffService.getPlanPositionByCode("PREMIUM");

        assertEquals(2, pos);
    }

    @Test
    void toViewDto_CalculatesDiscountAndLabels() {
        SubscriptionPlanViewDTO dto = tariffService.toViewDto(plan);

        assertEquals("PREMIUM", dto.getCode());
        assertEquals("Premium", dto.getName());
        assertEquals(2, dto.getPosition());
        assertEquals(5, dto.getMaxTracksPerFile());
        assertEquals(100, dto.getMaxSavedTracks());
        assertTrue(dto.isAllowBulkUpdate());
        assertTrue(dto.isAllowAutoUpdate());
        assertTrue(dto.isAllowCustomNotifications());
        assertTrue(dto.isAllowTelegramNotifications());
        assertEquals("15.00 BYN/мес", dto.getMonthlyPriceLabel());
        assertEquals("150.00 BYN/год", dto.getAnnualPriceLabel());
        assertEquals("180.00 BYN", dto.getAnnualFullPriceLabel());
        assertEquals("выгода −17%", dto.getAnnualDiscountLabel());
        assertFalse(dto.isRecommended());
    }

    @Test
    void toViewDto_WithoutLimits_DoesNotThrow() {
        plan.setLimits(null);

        assertDoesNotThrow(() -> tariffService.toViewDto(plan));
    }

    @Test
    void getPlanInfoByCode_ReturnsDto() {
        when(planRepository.findByCode("PREMIUM")).thenReturn(java.util.Optional.of(plan));

        SubscriptionPlanViewDTO dto = tariffService.getPlanInfoByCode("PREMIUM");

        assertNotNull(dto);
        assertEquals("PREMIUM", dto.getCode());
    }

    @Test
    void getPlanInfoByCode_NotFound_ReturnsNull() {
        when(planRepository.findByCode("NONE")).thenReturn(java.util.Optional.empty());

        SubscriptionPlanViewDTO dto = tariffService.getPlanInfoByCode("NONE");

        assertNull(dto);

    }

    @Test
    void recommendedFlagForBusinessPlan() {
        plan.setCode("BUSINESS");

        SubscriptionPlanViewDTO dto = tariffService.toViewDto(plan);

        assertTrue(dto.isRecommended());
    }
  
}