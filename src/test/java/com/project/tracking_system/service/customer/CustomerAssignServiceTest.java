package com.project.tracking_system.service.customer;

import com.project.tracking_system.entity.BuyerReputation;
import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.repository.CustomerRepository;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.service.user.UserSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link CustomerService#assignCustomerToParcel(Long, String)}.
 *
 * <p>Verifies statistics recalculation when reassigning a parcel with a
 * final status.</p>
 */
@ExtendWith(MockitoExtension.class)
class CustomerAssignServiceTest {

    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private TrackParcelRepository trackParcelRepository;
    @Mock
    private CustomerTransactionalService transactionalService;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private UserSettingsService userSettingsService;

    private CustomerStatsService customerStatsService;
    private CustomerService service;

    @BeforeEach
    void setUp() {
        customerStatsService = new CustomerStatsService(customerRepository);
        service = new CustomerService(
                customerRepository,
                trackParcelRepository,
                transactionalService,
                customerStatsService,
                subscriptionService,
                userSettingsService
        );

        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(trackParcelRepository.save(any(TrackParcel.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /**
     * Reassign parcel with {@link GlobalStatus#DELIVERED} and verify statistics.
     */
    @Test
    void assignCustomerToParcel_Delivered_RecalculatesStats() {
        Customer oldCustomer = buildCustomer(1L, 4, 3, 0);
        Customer newCustomer = buildCustomer(2L, 3, 2, 0);

        TrackParcel parcel = new TrackParcel();
        parcel.setId(10L);
        parcel.setStatus(GlobalStatus.DELIVERED);
        parcel.setCustomer(oldCustomer);

        when(trackParcelRepository.findById(10L)).thenReturn(Optional.of(parcel));
        when(transactionalService.findByPhone("375111111111")).thenReturn(Optional.of(newCustomer));
        when(customerRepository.incrementSentCount(2L)).thenReturn(1);
        when(customerRepository.incrementPickedUpCount(2L)).thenReturn(1);

        service.assignCustomerToParcel(10L, "375111111111");

        // old customer decreased
        assertEquals(3, oldCustomer.getSentCount());
        assertEquals(2, oldCustomer.getPickedUpCount());
        assertEquals(BuyerReputation.NEW, oldCustomer.getReputation());

        // new customer increased
        assertEquals(4, newCustomer.getSentCount());
        assertEquals(3, newCustomer.getPickedUpCount());
        assertEquals(BuyerReputation.RELIABLE, newCustomer.getReputation());

        assertSame(newCustomer, parcel.getCustomer());
    }

    /**
     * Reassign parcel with {@link GlobalStatus#RETURNED} and verify statistics.
     */
    @Test
    void assignCustomerToParcel_Returned_RecalculatesStats() {
        Customer oldCustomer = buildCustomer(1L, 3, 1, 2);
        Customer newCustomer = buildCustomer(2L, 2, 1, 1);

        TrackParcel parcel = new TrackParcel();
        parcel.setId(11L);
        parcel.setStatus(GlobalStatus.RETURNED);
        parcel.setCustomer(oldCustomer);

        when(trackParcelRepository.findById(11L)).thenReturn(Optional.of(parcel));
        when(transactionalService.findByPhone("375222222222")).thenReturn(Optional.of(newCustomer));
        when(customerRepository.incrementSentCount(2L)).thenReturn(1);
        when(customerRepository.incrementReturnedCount(2L)).thenReturn(1);

        service.assignCustomerToParcel(11L, "375222222222");

        // old customer decreased
        assertEquals(2, oldCustomer.getSentCount());
        assertEquals(1, oldCustomer.getReturnedCount());
        assertEquals(BuyerReputation.NEW, oldCustomer.getReputation());

        // new customer increased
        assertEquals(3, newCustomer.getSentCount());
        assertEquals(2, newCustomer.getReturnedCount());
        assertEquals(BuyerReputation.UNRELIABLE, newCustomer.getReputation());

        assertSame(newCustomer, parcel.getCustomer());
    }

    private static Customer buildCustomer(Long id, int sent, int picked, int returned) {
        Customer c = new Customer();
        c.setId(id);
        c.setSentCount(sent);
        c.setPickedUpCount(picked);
        c.setReturnedCount(returned);
        c.recalculateReputation();
        return c;
    }
}

