package com.project.tracking_system.repository;

import com.project.tracking_system.entity.Customer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.OptimisticLockingFailureException;

import static org.junit.jupiter.api.Assertions.*;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Проверка оптимистичной блокировки для {@link CustomerRepository}.
 */
@DataJpaTest
class CustomerRepositoryTest {

    @Autowired
    private CustomerRepository customerRepository;

    /**
     * Попытка сохранить устаревшую версию сущности должна завершаться ошибкой.
     */
    @Test
    void savingStaleEntityThrowsOptimisticLockingFailure() {
        Customer customer = new Customer();
        customer.setPhone("375000000000");
        customerRepository.saveAndFlush(customer);

        Customer first = customerRepository.findById(customer.getId()).orElseThrow();
        Customer second = customerRepository.findById(customer.getId()).orElseThrow();

        first.setFullName("Первый");
        customerRepository.saveAndFlush(first);

        second.setFullName("Второй");
        assertThrows(OptimisticLockingFailureException.class,
                () -> customerRepository.saveAndFlush(second));
    }

    /**
     * Проверяет сохранение и загрузку отметки последней активности покупателя.
     */
    @Test
    void shouldPersistLastActiveAt() {
        Customer customer = new Customer();
        customer.setPhone("375000000001");
        ZonedDateTime expected = ZonedDateTime.of(2024, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC);
        customer.setLastActiveAt(expected);
        customerRepository.saveAndFlush(customer);

        Customer reloaded = customerRepository.findById(customer.getId()).orElseThrow();
        assertEquals(expected, reloaded.getLastActiveAt(),
                "Отметка активности должна считываться из базы данных");
    }
}
