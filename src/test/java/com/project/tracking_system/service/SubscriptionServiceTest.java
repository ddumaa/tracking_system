package com.project.tracking_system.service;

import com.project.tracking_system.entity.SubscriptionFeature;
import com.project.tracking_system.entity.SubscriptionLimits;
import com.project.tracking_system.entity.SubscriptionPlan;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.entity.UserSubscription;
import com.project.tracking_system.model.subscription.FeatureKey;
import com.project.tracking_system.repository.SubscriptionPlanRepository;
import com.project.tracking_system.repository.UserSubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Тесты для {@link SubscriptionService}.
 */
@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;
    @Mock
    private SubscriptionPlanRepository subscriptionPlanRepository;
    @InjectMocks
    private SubscriptionService subscriptionService;

    @Test
    void canUpdateTracks_NoSubscription_ReturnsZero() {
        when(userSubscriptionRepository.findByUserId(1L))
                .thenReturn(Optional.empty());

        int allowed = subscriptionService.canUpdateTracks(1L, 5);

        assertEquals(0, allowed);
        verify(userSubscriptionRepository, never()).save(any());
    }

    @Test
    void canUpdateTracks_UnlimitedPlan_ReturnsRequested() {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setLimits(null); // отсутствие лимитов
        UserSubscription sub = new UserSubscription();
        sub.setSubscriptionPlan(plan);
        sub.setResetDate(LocalDate.now());

        when(userSubscriptionRepository.findByUserId(1L))
                .thenReturn(Optional.of(sub));

        int allowed = subscriptionService.canUpdateTracks(1L, 3);

        assertEquals(3, allowed);
        verify(userSubscriptionRepository, never()).save(any());
    }

    @Test
    void canUpdateTracks_WithLimits_UpdatesCounter() {
        SubscriptionLimits limits = new SubscriptionLimits();
        limits.setMaxTrackUpdates(5);

        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setLimits(limits);

        UserSubscription sub = new UserSubscription();
        sub.setSubscriptionPlan(plan);
        sub.setResetDate(LocalDate.now());
        sub.setUpdateCount(3);

        when(userSubscriptionRepository.findByUserId(1L))
                .thenReturn(Optional.of(sub));

        int allowed = subscriptionService.canUpdateTracks(1L, 3);

        assertEquals(2, allowed);
        assertEquals(5, sub.getUpdateCount());
        verify(userSubscriptionRepository).save(sub);
    }

    @Test
    void upgradeOrExtendSubscription_ExtendPaidPlan() {
        SubscriptionPlan paidPlan = new SubscriptionPlan();
        paidPlan.setMonthlyPrice(new BigDecimal("10"));
        ZonedDateTime oldExpiry = ZonedDateTime.now(ZoneOffset.UTC).plusMonths(1);

        UserSubscription subscription = new UserSubscription();
        subscription.setUser(new User());
        subscription.setSubscriptionPlan(paidPlan);
        subscription.setSubscriptionEndDate(oldExpiry);

        when(userSubscriptionRepository.findByUserId(1L))
                .thenReturn(Optional.of(subscription));

        subscriptionService.upgradeOrExtendSubscription(1L, 2);

        assertNotNull(subscription.getSubscriptionEndDate());
        assertTrue(subscription.getSubscriptionEndDate().isAfter(oldExpiry));
        assertEquals(paidPlan, subscription.getSubscriptionPlan());
        verify(userSubscriptionRepository).save(subscription);
    }

    @Test
    void upgradeOrExtendSubscription_UpgradeFreePlan() {
        SubscriptionPlan freePlan = new SubscriptionPlan();
        freePlan.setMonthlyPrice(BigDecimal.ZERO);
        freePlan.setAnnualPrice(BigDecimal.ZERO);

        SubscriptionPlan paidPlan = new SubscriptionPlan();
        paidPlan.setMonthlyPrice(new BigDecimal("5"));

        UserSubscription subscription = new UserSubscription();
        subscription.setUser(new User());
        subscription.setSubscriptionPlan(freePlan);
        subscription.setResetDate(LocalDate.now());

        when(userSubscriptionRepository.findByUserId(1L))
                .thenReturn(Optional.of(subscription));
        when(subscriptionPlanRepository.findFirstByMonthlyPriceGreaterThanOrAnnualPriceGreaterThan(BigDecimal.ZERO, BigDecimal.ZERO))
                .thenReturn(Optional.of(paidPlan));

        subscriptionService.upgradeOrExtendSubscription(1L, 3);

        assertEquals(paidPlan, subscription.getSubscriptionPlan());
        assertNotNull(subscription.getSubscriptionEndDate());
        verify(userSubscriptionRepository).save(subscription);
    }

    @Test
    void upgradeOrExtendSubscription_SubscriptionMissing_Throws() {
        when(userSubscriptionRepository.findByUserId(2L))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> subscriptionService.upgradeOrExtendSubscription(2L, 1));
    }

    @Test
    void isFeatureEnabled_ReturnsTrueWhenEnabled() {
        when(userSubscriptionRepository.getSubscriptionPlanCode(1L))
                .thenReturn("PREMIUM");

        SubscriptionFeature feature = new SubscriptionFeature();
        feature.setFeatureKey(FeatureKey.BULK_UPDATE);
        feature.setEnabled(true);

        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setCode("PREMIUM");
        plan.setFeatures(List.of(feature));

        when(subscriptionPlanRepository.findByCode("PREMIUM"))
                .thenReturn(Optional.of(plan));

        assertTrue(subscriptionService.isFeatureEnabled(1L, FeatureKey.BULK_UPDATE));
    }

    @Test
    void isFeatureEnabled_ReturnsFalseWhenNoCode() {
        when(userSubscriptionRepository.getSubscriptionPlanCode(3L))
                .thenReturn(null);

        assertFalse(subscriptionService.isFeatureEnabled(3L, FeatureKey.BULK_UPDATE));
    }

    @Test
    void changeSubscription_ToPaidPlan_SetsEndDate() {
        UserSubscription subscription = new UserSubscription();
        subscription.setUser(new User());
        SubscriptionPlan currentPlan = new SubscriptionPlan();
        currentPlan.setMonthlyPrice(BigDecimal.ZERO);
        currentPlan.setAnnualPrice(BigDecimal.ZERO);
        subscription.setSubscriptionPlan(currentPlan);

        SubscriptionPlan paidPlan = new SubscriptionPlan();
        paidPlan.setMonthlyPrice(new BigDecimal("7"));
        paidPlan.setCode("PREMIUM");

        when(userSubscriptionRepository.findByUserId(1L)).thenReturn(Optional.of(subscription));
        when(subscriptionPlanRepository.findByCode("PREMIUM")).thenReturn(Optional.of(paidPlan));

        subscriptionService.changeSubscription(1L, "PREMIUM", 1);

        assertEquals(paidPlan, subscription.getSubscriptionPlan());
        assertNotNull(subscription.getSubscriptionEndDate());
        verify(userSubscriptionRepository).save(subscription);
    }
}
