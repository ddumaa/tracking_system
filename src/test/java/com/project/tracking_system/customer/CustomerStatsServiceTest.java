package com.project.tracking_system.customer;

import com.project.tracking_system.entity.*;
import com.project.tracking_system.repository.*;
import com.project.tracking_system.service.customer.CustomerStatsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Проверка инкрементов статистики покупателя.
 */
@DataJpaTest
@Import(CustomerStatsService.class)
class CustomerStatsServiceTest {

    @Autowired
    private CustomerStatsService customerStatsService;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private TrackParcelRepository trackParcelRepository;
    @Autowired
    private StoreRepository storeRepository;
    @Autowired
    private UserRepository userRepository;

    @Test
    void countersIncrementOncePerCall() {
        // создаём покупателя и две посылки
        Customer customer = new Customer();
        customer.setPhone("375291234567");
        customerRepository.save(customer);

        User user = new User();
        user.setEmail("stats@example.com");
        user.setPassword("pass");
        user.setRole(Role.ROLE_USER);
        userRepository.save(user);

        Store store = new Store();
        store.setName("test");
        store.setDefault(false);
        store.setOwner(user);
        storeRepository.save(store);

        TrackParcel first = new TrackParcel();
        first.setNumber("TR1");
        first.setStatus(GlobalStatus.IN_TRANSIT);
        first.setData(ZonedDateTime.now());
        first.setStore(store);
        first.setUser(user);
        first.setCustomer(customer);
        trackParcelRepository.save(first);

        TrackParcel second = new TrackParcel();
        second.setNumber("TR2");
        second.setStatus(GlobalStatus.IN_TRANSIT);
        second.setData(ZonedDateTime.now());
        second.setStore(store);
        second.setUser(user);
        second.setCustomer(customer);
        trackParcelRepository.save(second);

        // инкрементируем отправленные два раза
        customerStatsService.incrementSent(customer);
        customerStatsService.incrementSent(customer);
        Customer afterSent = customerRepository.findById(customer.getId()).orElseThrow();
        assertEquals(2, afterSent.getSentCount());

        // инкрементируем забранные два раза
        customerStatsService.incrementPickedUp(customer);
        customerStatsService.incrementPickedUp(customer);
        Customer afterPickup = customerRepository.findById(customer.getId()).orElseThrow();
        assertEquals(2, afterPickup.getPickedUpCount());
    }
}
