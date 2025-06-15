package com.project.tracking_system.customer;

import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.repository.CustomerRepository;
import com.project.tracking_system.service.customer.CustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Проверка конкурентной регистрации одного и того же покупателя.
 */
@DataJpaTest
@Import(CustomerService.class)
class CustomerConcurrencyTest {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    void concurrentRegisterDoesNotDuplicateCustomer() throws Exception {
        String phone = "291234567";
        CountDownLatch latch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Callable<Customer> task = () -> {
            latch.await();
            return customerService.registerOrGetByPhone(phone);
        };

        Future<Customer> first = executor.submit(task);
        Future<Customer> second = executor.submit(task);
        // Одновременно запускаем оба потока
        latch.countDown();

        Customer c1 = first.get(5, TimeUnit.SECONDS);
        Customer c2 = second.get(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(c1.getId(), c2.getId());
        assertEquals(1, customerRepository.count());
    }
}
