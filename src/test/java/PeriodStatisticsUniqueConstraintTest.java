import com.project.tracking_system.entity.*;
import com.project.tracking_system.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
public class PeriodStatisticsUniqueConstraintTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StoreRepository storeRepository;
    @Autowired
    private StoreWeeklyStatisticsRepository storeWeeklyRepo;
    @Autowired
    private StoreMonthlyStatisticsRepository storeMonthlyRepo;
    @Autowired
    private StoreYearlyStatisticsRepository storeYearlyRepo;
    @Autowired
    private PostalServiceWeeklyStatisticsRepository psWeeklyRepo;
    @Autowired
    private PostalServiceMonthlyStatisticsRepository psMonthlyRepo;
    @Autowired
    private PostalServiceYearlyStatisticsRepository psYearlyRepo;

    private Store prepareStore() {
        User user = new User();
        user.setEmail("user@example.com");
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
    void duplicateStoreWeeklyStatistics_throwsException() {
        Store store = prepareStore();

        StoreWeeklyStatistics first = new StoreWeeklyStatistics();
        first.setStore(store);
        first.setPeriodYear(2024);
        first.setPeriodNumber(1);
        storeWeeklyRepo.saveAndFlush(first);

        StoreWeeklyStatistics duplicate = new StoreWeeklyStatistics();
        duplicate.setStore(store);
        duplicate.setPeriodYear(2024);
        duplicate.setPeriodNumber(1);

        // unique constraint should trigger upon flush
        org.junit.jupiter.api.Assertions.assertThrows(DataIntegrityViolationException.class, () -> {
            storeWeeklyRepo.saveAndFlush(duplicate);
        });
    }

    @Test
    void duplicatePostalServiceMonthlyStatistics_throwsException() {
        Store store = prepareStore();

        PostalServiceMonthlyStatistics first = new PostalServiceMonthlyStatistics();
        first.setStore(store);
        first.setPostalServiceType(PostalServiceType.BELPOST);
        first.setPeriodYear(2024);
        first.setPeriodNumber(1);
        psMonthlyRepo.saveAndFlush(first);

        PostalServiceMonthlyStatistics duplicate = new PostalServiceMonthlyStatistics();
        duplicate.setStore(store);
        duplicate.setPostalServiceType(PostalServiceType.BELPOST);
        duplicate.setPeriodYear(2024);
        duplicate.setPeriodNumber(1);

        org.junit.jupiter.api.Assertions.assertThrows(DataIntegrityViolationException.class, () -> {
            psMonthlyRepo.saveAndFlush(duplicate);
        });
    }
}
