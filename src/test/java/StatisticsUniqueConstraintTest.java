import com.project.tracking_system.entity.*;
import com.project.tracking_system.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
public class StatisticsUniqueConstraintTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StoreRepository storeRepository;
    @Autowired
    private StoreAnalyticsRepository storeAnalyticsRepository;
    @Autowired
    private PostalServiceStatisticsRepository postalServiceStatisticsRepository;

    private Store prepareStore() {
        User user = new User();
        user.setEmail("unique@example.com");
        user.setPassword("pass");
        user.setRole(Role.ROLE_USER);
        userRepository.saveAndFlush(user);

        Store store = new Store();
        store.setName("Store");
        store.setDefault(false);
        store.setOwner(user);
        return storeRepository.saveAndFlush(store);
    }

    @Test
    void duplicateStoreStatistics_throwsException() {
        Store store = prepareStore();

        StoreStatistics first = new StoreStatistics();
        first.setStore(store);
        storeAnalyticsRepository.saveAndFlush(first);

        StoreStatistics duplicate = new StoreStatistics();
        duplicate.setStore(store);

        org.junit.jupiter.api.Assertions.assertThrows(DataIntegrityViolationException.class, () -> {
            storeAnalyticsRepository.saveAndFlush(duplicate);
        });
    }

    @Test
    void duplicatePostalServiceStatistics_throwsException() {
        Store store = prepareStore();

        PostalServiceStatistics first = new PostalServiceStatistics();
        first.setStore(store);
        first.setPostalServiceType(PostalServiceType.BELPOST);
        postalServiceStatisticsRepository.saveAndFlush(first);

        PostalServiceStatistics duplicate = new PostalServiceStatistics();
        duplicate.setStore(store);
        duplicate.setPostalServiceType(PostalServiceType.BELPOST);

        org.junit.jupiter.api.Assertions.assertThrows(DataIntegrityViolationException.class, () -> {
            postalServiceStatisticsRepository.saveAndFlush(duplicate);
        });
    }
}
