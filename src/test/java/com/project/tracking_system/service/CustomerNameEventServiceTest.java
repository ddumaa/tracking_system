package com.project.tracking_system.service;

import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.entity.CustomerNameEvent;
import com.project.tracking_system.entity.CustomerNameEventStatus;
import com.project.tracking_system.repository.CustomerNameEventRepository;
import com.project.tracking_system.repository.CustomerRepository;
import com.project.tracking_system.service.customer.CustomerNameEventService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Проверка фиксации событий смены ФИО покупателя.
 */
@DataJpaTest
class CustomerNameEventServiceTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CustomerNameEventRepository eventRepository;

    private CustomerNameEventService eventService;

    @BeforeEach
    void setUp() {
        eventService = new CustomerNameEventService(eventRepository);
    }

    /**
     * Новое событие должно помечать предыдущее как SUPERSEDED.
     */
    @Test
    void newEventSupersedesPrevious() {
        Customer customer = new Customer();
        customer.setPhone("375000000000");
        customerRepository.saveAndFlush(customer);

        eventService.recordEvent(customer, null, "Иван Иванов");
        eventService.recordEvent(customer, "Иван Иванов", "Пётр Петров");

        List<CustomerNameEvent> events = eventRepository.findByCustomerOrderByCreatedAtAsc(customer);
        assertEquals(2, events.size());
        assertEquals(CustomerNameEventStatus.SUPERSEDED, events.get(0).getStatus());
        assertEquals(CustomerNameEventStatus.ACTIVE, events.get(1).getStatus());
    }
}
