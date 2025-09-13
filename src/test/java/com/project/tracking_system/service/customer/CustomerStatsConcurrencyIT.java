package com.project.tracking_system.service.customer;

import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.repository.CustomerRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Интеграционный тест конкурентных обновлений счётчиков покупателя.
 */
@DataJpaTest
@Import(CustomerStatsService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CustomerStatsConcurrencyIT {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CustomerStatsService customerStatsService;

    @Autowired
    private EntityManager entityManager;

    /**
     * Проверяет, что параллельные вызовы методов increment* не приводят к
     * {@link OptimisticLockException} и корректно обновляют счётчики.
     */
    @Test
    void concurrentIncrementDoesNotThrowOptimisticLock() throws Exception {
        Customer customer = new Customer();
        customer.setPhone("375000000000");
        // Сохраняем клиента в отдельной транзакции, чтобы он был виден параллельным потокам
        customerRepository.saveAndFlush(customer);

        int iterations = 5;
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Void>> tasks = List.of(
                () -> { start.await(); for (int i = 0; i < iterations; i++) customerStatsService.incrementSent(customer); return null; },
                () -> { start.await(); for (int i = 0; i < iterations; i++) customerStatsService.incrementPickedUp(customer); return null; },
                () -> { start.await(); for (int i = 0; i < iterations; i++) customerStatsService.incrementReturned(customer); return null; }
        );
        List<Future<Void>> futures = new ArrayList<>();
        for (Callable<Void> task : tasks) {
            futures.add(executor.submit(task));
        }
        start.countDown();
        for (Future<Void> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        entityManager.clear();
        Customer refreshed = customerRepository.findById(customer.getId()).orElseThrow();
        assertEquals(iterations, refreshed.getSentCount());
        assertEquals(iterations, refreshed.getPickedUpCount());
        assertEquals(iterations, refreshed.getReturnedCount());
        assertEquals(iterations * 3L, refreshed.getVersion());
    }
}
