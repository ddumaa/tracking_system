package com.project.tracking_system.customer;

import com.project.tracking_system.entity.BuyerReputation;
import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.repository.CustomerRepository;
import com.project.tracking_system.service.customer.CustomerTelegramService;
import com.project.tracking_system.service.customer.CustomerService;
import com.project.tracking_system.service.customer.CustomerTransactionalService;
import com.project.tracking_system.service.customer.CustomerStatsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционные тесты для {@link CustomerTelegramService}.
 */
@DataJpaTest
@Import({CustomerTelegramService.class, CustomerService.class, CustomerTransactionalService.class, CustomerStatsService.class})
class CustomerTelegramServiceTest {

    @Autowired
    private CustomerTelegramService telegramService;

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    void createsNewCustomerWhenNotExists() {
        Customer customer = telegramService.linkTelegramToCustomer("29 123-45-67", 111L);
        assertNotNull(customer.getId());
        assertEquals("375291234567", customer.getPhone());
        assertEquals(0, customer.getSentCount());
        assertEquals(0, customer.getPickedUpCount());
        assertEquals(BuyerReputation.NEW, customer.getReputation());
        Customer fromDb = customerRepository.findById(customer.getId()).orElseThrow();
        assertEquals(111L, fromDb.getTelegramChatId());
    }

    @Test
    void attachesChatToExistingCustomer() {
        Customer existing = new Customer();
        existing.setPhone("375291234567");
        customerRepository.save(existing);

        Customer customer = telegramService.linkTelegramToCustomer("291234567", 222L);
        assertEquals(existing.getId(), customer.getId());
        assertEquals(222L, customerRepository.findById(existing.getId()).orElseThrow().getTelegramChatId());
    }

    @Test
    void repeatedLinkDoesNothing() {
        Customer existing = new Customer();
        existing.setPhone("375291234567");
        existing.setTelegramChatId(333L);
        customerRepository.save(existing);

        Customer customer = telegramService.linkTelegramToCustomer("291234567", 444L);
        assertEquals(existing.getId(), customer.getId());
        Customer fromDb = customerRepository.findById(existing.getId()).orElseThrow();
        assertEquals(333L, fromDb.getTelegramChatId());
    }
}
