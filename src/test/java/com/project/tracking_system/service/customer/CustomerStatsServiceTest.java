package com.project.tracking_system.service.customer;

import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Тесты для {@link CustomerStatsService}.
 */
@ExtendWith(MockitoExtension.class)
class CustomerStatsServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerStatsService service;

    private Customer customer;

    @BeforeEach
    void setUp() {
        customer = new Customer();
        customer.setId(1L);
        customer.setVersion(0);
    }

    /**
     * Успешное атомарное увеличение отправленных посылок.
     */
    @Test
    void incrementSent_AtomicSuccess() {
        when(customerRepository.incrementSentCount(1L, 0L)).thenReturn(1);
        Customer fresh = new Customer();
        fresh.setId(1L);
        fresh.setVersion(1);
        fresh.setSentCount(1);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(fresh));
        when(customerRepository.updateReputation(anyLong(), anyLong(), any())).thenReturn(1);

        Customer result = service.incrementSent(customer);

        assertEquals(0, customer.getSentCount());
        assertSame(fresh, result);
        verify(customerRepository).incrementSentCount(1L, 0L);
        verify(customerRepository).findById(1L);
        verify(customerRepository).updateReputation(1L, 1L, fresh.getReputation());
        verify(customerRepository, never()).save(any());
    }

    /**
     * Падение атомарного обновления приводит к ручному увеличению.
     */
    @Test
    void incrementSent_FallbackManual() {
        when(customerRepository.incrementSentCount(1L, 0L)).thenReturn(0);
        Customer fresh = new Customer();
        fresh.setId(1L);
        fresh.setVersion(1);
        fresh.setSentCount(1);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(fresh));

        Customer result = service.incrementSent(customer);

        assertEquals(0, customer.getSentCount());
        assertSame(fresh, result);
        verify(customerRepository).findById(1L);
        verify(customerRepository).save(fresh);
        verify(customerRepository, never()).updateReputation(anyLong(), anyLong(), any());
    }

    /**
     * Успешное атомарное увеличение забранных посылок.
     */
    @Test
    void incrementPickedUp_AtomicSuccess() {
        when(customerRepository.incrementPickedUpCount(1L, 0L)).thenReturn(1);
        Customer fresh = new Customer();
        fresh.setId(1L);
        fresh.setVersion(1);
        fresh.setPickedUpCount(1);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(fresh));
        when(customerRepository.updateReputation(anyLong(), anyLong(), any())).thenReturn(1);

        Customer result = service.incrementPickedUp(customer);

        assertEquals(0, customer.getPickedUpCount());
        assertSame(fresh, result);
        verify(customerRepository).incrementPickedUpCount(1L, 0L);
        verify(customerRepository).findById(1L);
        verify(customerRepository).updateReputation(1L, 1L, fresh.getReputation());
        verify(customerRepository, never()).save(any());
    }

    /**
     * Ошибка атомарного обновления забранных переключает на ручной режим.
     */
    @Test
    void incrementPickedUp_FallbackManual() {
        when(customerRepository.incrementPickedUpCount(1L, 0L)).thenReturn(0);
        Customer fresh = new Customer();
        fresh.setId(1L);
        fresh.setVersion(1);
        fresh.setPickedUpCount(1);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(fresh));

        Customer result = service.incrementPickedUp(customer);

        assertEquals(0, customer.getPickedUpCount());
        assertSame(fresh, result);
        verify(customerRepository).findById(1L);
        verify(customerRepository).save(fresh);
        verify(customerRepository, never()).updateReputation(anyLong(), anyLong(), any());
    }

    /**
     * Успешное атомарное увеличение возвратов.
     */
    @Test
    void incrementReturned_AtomicSuccess() {
        when(customerRepository.incrementReturnedCount(1L, 0L)).thenReturn(1);
        Customer fresh = new Customer();
        fresh.setId(1L);
        fresh.setVersion(1);
        fresh.setReturnedCount(1);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(fresh));
        when(customerRepository.updateReputation(anyLong(), anyLong(), any())).thenReturn(1);

        Customer result = service.incrementReturned(customer);

        assertEquals(0, customer.getReturnedCount());
        assertSame(fresh, result);
        verify(customerRepository).incrementReturnedCount(1L, 0L);
        verify(customerRepository).findById(1L);
        verify(customerRepository).updateReputation(1L, 1L, fresh.getReputation());
        verify(customerRepository, never()).save(any());
    }

    /**
     * Ошибка атомарного увеличения возвратов обрабатывается вручную.
     */
    @Test
    void incrementReturned_FallbackManual() {
        when(customerRepository.incrementReturnedCount(1L, 0L)).thenReturn(0);
        Customer fresh = new Customer();
        fresh.setId(1L);
        fresh.setVersion(1);
        fresh.setReturnedCount(1);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(fresh));

        Customer result = service.incrementReturned(customer);

        assertEquals(0, customer.getReturnedCount());
        assertSame(fresh, result);
        verify(customerRepository).findById(1L);
        verify(customerRepository).save(fresh);
        verify(customerRepository, never()).updateReputation(anyLong(), anyLong(), any());
    }
}
