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
import static org.mockito.ArgumentMatchers.anyLong;
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
    }

    /**
     * Успешное атомарное увеличение отправленных посылок.
     */
    @Test
    void incrementSent_AtomicSuccess() {
        when(customerRepository.incrementSentCount(1L)).thenReturn(1);

        service.incrementSent(customer);

        assertEquals(1, customer.getSentCount());
        verify(customerRepository).incrementSentCount(1L);
        verify(customerRepository).save(customer);
    }

    /**
     * Падение атомарного обновления приводит к ручному увеличению.
     */
    @Test
    void incrementSent_FallbackManual() {
        when(customerRepository.incrementSentCount(1L)).thenReturn(0);
        Customer fresh = new Customer();
        fresh.setId(1L);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(fresh));

        service.incrementSent(customer);

        assertEquals(1, customer.getSentCount());
        verify(customerRepository).save(fresh);
    }

    /**
     * Успешное атомарное увеличение забранных посылок.
     */
    @Test
    void incrementPickedUp_AtomicSuccess() {
        when(customerRepository.incrementPickedUpCount(1L)).thenReturn(1);

        service.incrementPickedUp(customer);

        assertEquals(1, customer.getPickedUpCount());
        verify(customerRepository).incrementPickedUpCount(1L);
        verify(customerRepository).save(customer);
    }

    /**
     * Ошибка атомарного обновления забранных переключает на ручной режим.
     */
    @Test
    void incrementPickedUp_FallbackManual() {
        when(customerRepository.incrementPickedUpCount(1L)).thenReturn(0);
        Customer fresh = new Customer();
        fresh.setId(1L);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(fresh));

        service.incrementPickedUp(customer);

        assertEquals(1, customer.getPickedUpCount());
        verify(customerRepository).save(fresh);
    }

    /**
     * Успешное атомарное увеличение возвратов.
     */
    @Test
    void incrementReturned_AtomicSuccess() {
        when(customerRepository.incrementReturnedCount(1L)).thenReturn(1);

        service.incrementReturned(customer);

        assertEquals(1, customer.getReturnedCount());
        verify(customerRepository).incrementReturnedCount(1L);
        verify(customerRepository).save(customer);
    }

    /**
     * Ошибка атомарного увеличения возвратов обрабатывается вручную.
     */
    @Test
    void incrementReturned_FallbackManual() {
        when(customerRepository.incrementReturnedCount(1L)).thenReturn(0);
        Customer fresh = new Customer();
        fresh.setId(1L);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(fresh));

        service.incrementReturned(customer);

        assertEquals(1, customer.getReturnedCount());
        verify(customerRepository).save(fresh);
    }
}
