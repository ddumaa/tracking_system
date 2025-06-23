package com.project.tracking_system.repository;

import com.project.tracking_system.entity.EvropostServiceCredential;
import com.project.tracking_system.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты для {@link UserRepository}.
 */
@ExtendWith(SpringExtension.class)
@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager entityManager;

    @Test
    void findUsersForTokenRefresh_ReturnsOnlyExpired() {
        User expired = new User();
        expired.setEmail("expired@example.com");
        expired.setPassword("pass");
        expired.setTimeZone("UTC");
        EvropostServiceCredential credExpired = new EvropostServiceCredential();
        credExpired.setUser(expired);
        credExpired.setUseCustomCredentials(true);
        credExpired.setTokenCreatedAt(ZonedDateTime.now(ZoneOffset.UTC).minusDays(30));
        expired.setEvropostServiceCredential(credExpired);
        entityManager.persist(expired);

        User valid = new User();
        valid.setEmail("valid@example.com");
        valid.setPassword("pass");
        valid.setTimeZone("UTC");
        EvropostServiceCredential credValid = new EvropostServiceCredential();
        credValid.setUser(valid);
        credValid.setUseCustomCredentials(true);
        credValid.setTokenCreatedAt(ZonedDateTime.now(ZoneOffset.UTC).minusDays(10));
        valid.setEvropostServiceCredential(credValid);
        entityManager.persist(valid);

        entityManager.flush();

        ZonedDateTime threshold = ZonedDateTime.now(ZoneOffset.UTC).minusDays(29);
        List<User> result = userRepository.findUsersForTokenRefresh(threshold);

        assertEquals(1, result.size());
        assertEquals("expired@example.com", result.get(0).getEmail());
    }
}
