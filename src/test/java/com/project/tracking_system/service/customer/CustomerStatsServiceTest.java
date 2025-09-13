package com.project.tracking_system.service.customer;

import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.repository.CustomerRepository;
import jakarta.persistence.EntityManager;
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

    @Mock
    private EntityManager entityManager;

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
     * Конфликт версий при обновлении репутации приводит к повторной попытке.
     */
    @Test
    void incrementSent_ReputationConflictRetries() {
        when(customerRepository.incrementSentCount(1L, 0L)).thenReturn(1);

        Customer ver1 = new Customer();
        ver1.setId(1L);
        ver1.setVersion(1);
        ver1.setSentCount(1);

        Customer ver2 = new Customer();
        ver2.setId(1L);
        ver2.setVersion(2);
        ver2.setSentCount(1);

        when(customerRepository.findById(1L)).thenReturn(Optional.of(ver1), Optional.of(ver2));
        when(customerRepository.updateReputation(1L, 1L, ver1.getReputation())).thenReturn(0);
        when(customerRepository.updateReputation(1L, 2L, ver2.getReputation())).thenReturn(1);

        Customer result = service.incrementSent(customer);

        assertSame(ver2, result);
        verify(customerRepository).incrementSentCount(1L, 0L);
        verify(customerRepository, times(2)).findById(1L);
        verify(customerRepository).updateReputation(1L, 1L, ver1.getReputation());
        verify(customerRepository).updateReputation(1L, 2L, ver2.getReputation());
        verify(entityManager).detach(ver1);
        verify(customerRepository, never()).save(any());
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
