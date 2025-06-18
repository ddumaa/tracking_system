package com.project.tracking_system.customer;

import com.project.tracking_system.entity.*;
import com.project.tracking_system.repository.*;
import com.project.tracking_system.service.customer.CustomerService;
import com.project.tracking_system.service.jsonEvropostService.JwtTokenManager;
import com.project.tracking_system.service.track.TrackNumberOcrService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.ZonedDateTime;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Проверка одновременного сохранения треков с одним телефоном покупателя.
 */
@SpringBootTest
@MockBean(JwtTokenManager.class)
@MockBean(TrackNumberOcrService.class)
class TrackConcurrencyTest {

    @Autowired
    private CustomerService customerService;
    @Autowired
    private TrackParcelRepository trackParcelRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StoreRepository storeRepository;

    @Test
    void concurrentTrackSaveUsesSameCustomer() throws Exception {
        User user = new User();
        user.setEmail("concurrent@example.com");
        user.setPassword("pass");
        user.setRole(Role.ROLE_USER);
        userRepository.save(user);

        Store store = new Store();
        store.setName("test");
        store.setDefault(false);
        store.setOwner(user);
        storeRepository.save(store);

        String phone = "291234567";
        CountDownLatch latch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Callable<TrackParcel> task = () -> {
            latch.await();
            Customer customer = customerService.registerOrGetByPhone(phone);
            TrackParcel track = new TrackParcel();
            track.setNumber("TR" + ThreadLocalRandom.current().nextInt(1_000_000));
            track.setStatus(GlobalStatus.IN_TRANSIT);
            track.setData(ZonedDateTime.now());
            track.setStore(store);
            track.setUser(user);
            track.setCustomer(customer);
            return trackParcelRepository.save(track);
        };

        Future<TrackParcel> first = executor.submit(task);
        Future<TrackParcel> second = executor.submit(task);
        latch.countDown();

        TrackParcel t1 = first.get(5, TimeUnit.SECONDS);
        TrackParcel t2 = second.get(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(t1.getCustomer().getId(), t2.getCustomer().getId());
        assertEquals(1, customerRepository.count());
        assertEquals(2, trackParcelRepository.count());
    }
}
