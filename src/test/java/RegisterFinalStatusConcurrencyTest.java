import com.project.tracking_system.entity.*;
import com.project.tracking_system.repository.*;
import com.project.tracking_system.service.analytics.DeliveryHistoryService;
import com.project.tracking_system.service.track.StatusTrackService;
import com.project.tracking_system.service.track.TypeDefinitionTrackPostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import(DeliveryHistoryService.class)
public class RegisterFinalStatusConcurrencyTest {

    @Autowired
    private DeliveryHistoryService deliveryHistoryService;
    @Autowired
    private TrackParcelRepository trackParcelRepository;
    @Autowired
    private StoreAnalyticsRepository storeAnalyticsRepository;
    @Autowired
    private DeliveryHistoryRepository deliveryHistoryRepository;
    @Autowired
    private PostalServiceStatisticsRepository postalServiceStatisticsRepository;
    @Autowired
    private StoreDailyStatisticsRepository storeDailyStatisticsRepository;
    @Autowired
    private PostalServiceDailyStatisticsRepository postalServiceDailyStatisticsRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StoreRepository storeRepository;

    @MockBean
    private StatusTrackService statusTrackService;
    @MockBean
    private TypeDefinitionTrackPostService typeDefinitionTrackPostService;

    private DeliveryHistory history;
    private TrackParcel parcel;
    private Store store;

    @BeforeEach
    void setup() {
        User user = new User();
        user.setEmail("user@example.com");
        user.setPassword("pass");
        user.setRole(Role.ROLE_USER);
        userRepository.saveAndFlush(user);

        store = new Store();
        store.setName("Store");
        store.setDefault(false);
        store.setOwner(user);
        storeRepository.saveAndFlush(store);

        parcel = new TrackParcel();
        parcel.setNumber("PC999999999BY");
        parcel.setStatus(GlobalStatus.IN_TRANSIT);
        parcel.setData(ZonedDateTime.now(ZoneOffset.UTC));
        parcel.setStore(store);
        parcel.setUser(user);
        trackParcelRepository.saveAndFlush(parcel);

        history = new DeliveryHistory();
        history.setTrackParcel(parcel);
        history.setStore(store);
        history.setPostalService(PostalServiceType.BELPOST);
        history.setSendDate(ZonedDateTime.now(ZoneOffset.UTC).minusDays(2));
        history.setArrivedDate(ZonedDateTime.now(ZoneOffset.UTC).minusDays(1));
        history.setReceivedDate(ZonedDateTime.now(ZoneOffset.UTC));
        deliveryHistoryRepository.saveAndFlush(history);
    }

    /**
     * Проверяет, что при одновременной регистрации финального статуса
     * обновление статистики выполняется только один раз.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentRegisterFinalStatus_onlyOneUpdate() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);

        Runnable task = () -> {
            try {
                start.await();
                deliveryHistoryService.registerFinalStatus(history, GlobalStatus.DELIVERED);
            } catch (Exception ignored) {
            } finally {
                done.countDown();
            }
        };

        Thread t1 = new Thread(task);
        Thread t2 = new Thread(task);
        t1.start();
        t2.start();
        start.countDown();
        done.await();

        StoreStatistics stats = storeAnalyticsRepository.findByStoreId(store.getId()).orElseThrow();
        TrackParcel updated = trackParcelRepository.findById(parcel.getId()).orElseThrow();

        assertEquals(1, stats.getTotalDelivered());
        assertTrue(updated.isIncludedInStatistics());
    }
}
