package com.project.tracking_system.service.tariff;

import com.project.tracking_system.dto.SubscriptionPlanViewDTO;
import com.project.tracking_system.entity.SubscriptionFeature;
import com.project.tracking_system.entity.SubscriptionLimits;
import com.project.tracking_system.entity.SubscriptionPlan;
import com.project.tracking_system.model.subscription.FeatureKey;
import com.project.tracking_system.repository.SubscriptionPlanRepository;
import com.project.tracking_system.service.SubscriptionService;
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
    @Mock
    private SubscriptionService subscriptionService;
    @InjectMocks
    private TariffService tariffService;

    private SubscriptionPlan plan;

    @BeforeEach
    void initPlan() {
        plan = new SubscriptionPlan();
        plan.setCode("PREMIUM");
        plan.setName("Premium");
        plan.setDescription("Premium plan");
        plan.setMonthlyPrice(new BigDecimal("15"));
        plan.setAnnualPrice(new BigDecimal("150"));

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
        SubscriptionFeature telegram = new SubscriptionFeature();
        telegram.setFeatureKey(FeatureKey.TELEGRAM_NOTIFICATIONS);
        telegram.setEnabled(true);
        telegram.setSubscriptionPlan(plan);
        plan.setFeatures(List.of(bulk, telegram));
    }

    @Test
    void getAllPlans_ReturnsDtosWithCalculatedLabels() {
        when(planRepository.findAll()).thenReturn(List.of(plan));

        List<SubscriptionPlanViewDTO> dtos = tariffService.getAllPlans();

        assertEquals(1, dtos.size());
        SubscriptionPlanViewDTO dto = dtos.get(0);
        assertEquals("Premium", dto.getName());
        assertEquals("Premium plan", dto.getDescription());
        assertEquals("15.00 BYN/мес", dto.getMonthlyPriceLabel());
        assertEquals("150.00 BYN/год", dto.getAnnualPriceLabel());
        assertEquals("180.00 BYN", dto.getAnnualFullPriceLabel());
        assertEquals("выгода −17%", dto.getAnnualDiscountLabel());
    }

    @Test
    void toViewDto_CalculatesDiscountAndLabels() {
        SubscriptionPlanViewDTO dto = tariffService.toViewDto(plan);

        assertEquals("PREMIUM", dto.getCode());
        assertEquals("Premium", dto.getName());
        assertEquals("Premium plan", dto.getDescription());
        assertEquals(5, dto.getMaxTracksPerFile());
        assertEquals(100, dto.getMaxSavedTracks());
        assertTrue(dto.isAllowBulkUpdate());
        assertTrue(dto.isAllowTelegramNotifications());
        assertEquals("15.00 BYN/мес", dto.getMonthlyPriceLabel());
        assertEquals("150.00 BYN/год", dto.getAnnualPriceLabel());
        assertEquals("180.00 BYN", dto.getAnnualFullPriceLabel());
        assertEquals("выгода −17%", dto.getAnnualDiscountLabel());
    }
}
