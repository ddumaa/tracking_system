package com.project.tracking_system.service.admin;

import com.project.tracking_system.entity.SubscriptionPlan;
import com.project.tracking_system.repository.SubscriptionPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Проверка перестановки тарифных планов.
 */
@ExtendWith(MockitoExtension.class)
class SubscriptionPlanServicePositionTest {

    @Mock
    private SubscriptionPlanRepository repository;

    @InjectMocks
    private SubscriptionPlanService service;

    private SubscriptionPlan first;
    private SubscriptionPlan second;

    @BeforeEach
    void setUp() {
        first = new SubscriptionPlan();
        first.setId(1L);
        first.setPosition(1);

        second = new SubscriptionPlan();
        second.setId(2L);
        second.setPosition(2);
    }

    @Test
    void movePlanUp_SwapsWithPrevious() {
        when(repository.findAllByOrderByPositionAsc()).thenReturn(List.of(first, second));

        service.movePlanUp(2L);

        assertEquals(2, first.getPosition());
        assertEquals(1, second.getPosition());
        verify(repository).save(first);
        verify(repository).save(second);
    }

    @Test
    void movePlanDown_SwapsWithNext() {
        when(repository.findAllByOrderByPositionAsc()).thenReturn(List.of(first, second));

        service.movePlanDown(1L);

        assertEquals(2, first.getPosition());
        assertEquals(1, second.getPosition());
        verify(repository).save(first);
        verify(repository).save(second);
    }
}
