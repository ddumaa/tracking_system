package com.project.tracking_system.customer;

import com.project.tracking_system.entity.BuyerReputation;
import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.dto.CustomerInfoDTO;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.entity.Role;
import com.project.tracking_system.repository.CustomerRepository;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.repository.StoreRepository;
import com.project.tracking_system.repository.UserRepository;
import com.project.tracking_system.repository.SubscriptionPlanRepository;
import com.project.tracking_system.repository.UserSubscriptionRepository;
import com.project.tracking_system.entity.SubscriptionPlan;
import com.project.tracking_system.entity.UserSubscription;
import com.project.tracking_system.service.customer.CustomerService;
import com.project.tracking_system.service.customer.CustomerStatsService;
import com.project.tracking_system.service.customer.CustomerTransactionalService;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Интеграционные тесты для {@link CustomerService}.
 */
@DataJpaTest
@Import({CustomerService.class, CustomerStatsService.class, CustomerTransactionalService.class})
class CustomerServiceTest {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private TrackParcelRepository trackParcelRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Autowired
    private UserSubscriptionRepository userSubscriptionRepository;

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
        assertEquals(BuyerReputation.NEW, afterAdd.getReputation());

        // Доставляем посылку
        track.setStatus(GlobalStatus.DELIVERED);
        customerService.updateStatsOnTrackDelivered(track);
        Customer afterDeliver = customerRepository.findById(customer.getId()).orElseThrow();
        assertEquals(1, afterDeliver.getSentCount());
        assertEquals(1, afterDeliver.getPickedUpCount());
        assertEquals(BuyerReputation.NEW, afterDeliver.getReputation());

        // Удаляем посылку
        customerService.rollbackStatsOnTrackDelete(track);
        Customer afterDelete = customerRepository.findById(customer.getId()).orElseThrow();
        assertEquals(0, afterDelete.getSentCount());
        assertEquals(0, afterDelete.getPickedUpCount());
        assertEquals(BuyerReputation.NEW, afterDelete.getReputation());
    }

    private TrackParcel prepareParcel() {
        User user = new User();
        user.setEmail("user@example.com");
        user.setPassword("pass");
        user.setRole(Role.ROLE_USER);
        userRepository.save(user);

        Store store = new Store();
        store.setName("test");
        store.setDefault(false);
        store.setOwner(user);
        storeRepository.save(store);

        TrackParcel parcel = new TrackParcel();
        parcel.setNumber("TR" + System.nanoTime());
        parcel.setStatus(GlobalStatus.IN_TRANSIT);
        parcel.setData(ZonedDateTime.now());
        parcel.setStore(store);
        parcel.setUser(user);
        return trackParcelRepository.save(parcel);
    }

    @Test
    void assignCustomerAddsStats() {
        TrackParcel parcel = prepareParcel();

        CustomerInfoDTO dto = customerService.assignCustomerToParcel(parcel.getId(), "291234567");

        TrackParcel updated = trackParcelRepository.findById(parcel.getId()).orElseThrow();
        assertNotNull(updated.getCustomer());
        assertEquals("375291234567", updated.getCustomer().getPhone());
        assertEquals(1, updated.getCustomer().getSentCount());
        assertEquals("375291234567", dto.phone());
    }

    @Test
    void reassignCustomerAdjustsStats() {
        TrackParcel parcel = prepareParcel();

        customerService.assignCustomerToParcel(parcel.getId(), "291234567");
        Customer first = customerRepository.findByPhone("375291234567").orElseThrow();
        assertEquals(1, first.getSentCount());

        customerService.assignCustomerToParcel(parcel.getId(), "298765432");

        TrackParcel updated = trackParcelRepository.findById(parcel.getId()).orElseThrow();
        Customer second = customerRepository.findByPhone("375298765432").orElseThrow();
        Customer firstAfter = customerRepository.findByPhone("375291234567").orElseThrow();

        assertEquals("375298765432", updated.getCustomer().getPhone());
        assertEquals(1, second.getSentCount());
        assertEquals(0, firstAfter.getSentCount());
    }

    @Test
    void notifiableReturnsTrueWhenChatAndPremium() {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setName("PREMIUM");
        plan.setMaxTracksPerFile(1);
        plan.setMaxSavedTracks(1);
        plan.setMaxTrackUpdates(1);
        plan.setAllowBulkUpdate(true);
        plan.setMaxStores(1);
        subscriptionPlanRepository.save(plan);

        User user = new User();
        user.setEmail("premium@example.com");
        user.setPassword("pass");
        user.setRole(Role.ROLE_USER);
        userRepository.save(user);

        UserSubscription sub = new UserSubscription();
        sub.setUser(user);
        sub.setSubscriptionPlan(plan);
        userSubscriptionRepository.save(sub);
        user.setSubscription(sub);

        Store store = new Store();
        store.setName("s");
        store.setDefault(false);
        store.setOwner(user);
        storeRepository.save(store);

        Customer customer = new Customer();
        customer.setPhone("375291000000");
        customer.setTelegramChatId(5L);
        customerRepository.save(customer);

        assertTrue(customerService.isNotifiable(customer, store));
    }

    @Test
    void notifiableReturnsFalseWhenNoPremium() {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setName("FREE");
        plan.setMaxTracksPerFile(1);
        plan.setMaxSavedTracks(1);
        plan.setMaxTrackUpdates(1);
        plan.setAllowBulkUpdate(true);
        plan.setMaxStores(1);
        subscriptionPlanRepository.save(plan);

        User user = new User();
        user.setEmail("free@example.com");
        user.setPassword("pass");
        user.setRole(Role.ROLE_USER);
        userRepository.save(user);

        UserSubscription sub = new UserSubscription();
        sub.setUser(user);
        sub.setSubscriptionPlan(plan);
        userSubscriptionRepository.save(sub);
        user.setSubscription(sub);

        Store store = new Store();
        store.setName("s");
        store.setDefault(false);
        store.setOwner(user);
        storeRepository.save(store);

        Customer customer = new Customer();
        customer.setPhone("375292000000");
        customer.setTelegramChatId(6L);
        customerRepository.save(customer);

        assertFalse(customerService.isNotifiable(customer, store));
    }
}
