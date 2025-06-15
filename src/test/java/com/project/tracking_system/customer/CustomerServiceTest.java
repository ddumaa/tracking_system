package com.project.tracking_system.customer;

import com.project.tracking_system.entity.BuyerReputation;
import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.repository.CustomerRepository;
import com.project.tracking_system.service.customer.CustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционные тесты для {@link CustomerService}.
 */
@DataJpaTest
@Import(CustomerService.class)
class CustomerServiceTest {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private CustomerRepository customerRepository;

    @Test
    void registerOrGetByPhoneCreatesCustomer() {
        Customer created = customerService.registerOrGetByPhone("29 123-45-67");
        assertNotNull(created.getId());
        assertEquals("375291234567", created.getPhone());
        Customer fromDb = customerRepository.findById(created.getId()).orElseThrow();
        assertEquals(created.getId(), fromDb.getId());
    }

    @Test
    void statsUpdatedOnAddDeliverAndDelete() {
        Customer customer = customerService.registerOrGetByPhone("291234567");
        TrackParcel track = new TrackParcel();
        track.setCustomer(customer);

        // Добавляем посылку
        customerService.updateStatsOnTrackAdd(track);
        Customer afterAdd = customerRepository.findById(customer.getId()).orElseThrow();
        assertEquals(1, afterAdd.getSentCount());
        assertEquals(0, afterAdd.getPickedUpCount());
        assertEquals(BuyerReputation.NEUTRAL, afterAdd.getReputation());

        // Доставляем посылку
        track.setStatus(GlobalStatus.DELIVERED);
        customerService.updateStatsOnTrackDelivered(track);
        Customer afterDeliver = customerRepository.findById(customer.getId()).orElseThrow();
        assertEquals(1, afterDeliver.getSentCount());
        assertEquals(1, afterDeliver.getPickedUpCount());
        assertEquals(BuyerReputation.RELIABLE, afterDeliver.getReputation());

        // Удаляем посылку
        customerService.rollbackStatsOnTrackDelete(track);
        Customer afterDelete = customerRepository.findById(customer.getId()).orElseThrow();
        assertEquals(0, afterDelete.getSentCount());
        assertEquals(0, afterDelete.getPickedUpCount());
        assertEquals(BuyerReputation.NEUTRAL, afterDelete.getReputation());
    }
}
