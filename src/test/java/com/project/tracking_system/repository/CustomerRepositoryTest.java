package com.project.tracking_system.repository;

import com.project.tracking_system.entity.Customer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.OptimisticLockingFailureException;

import static org.junit.jupiter.api.Assertions.*;

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

}
