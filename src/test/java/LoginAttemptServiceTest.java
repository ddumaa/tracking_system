import com.project.tracking_system.entity.LoginAttempt;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.repository.LoginAttemptRepository;
import com.project.tracking_system.repository.UserRepository;
import com.project.tracking_system.service.user.LoginAttemptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Dmitriy Anisimov
 * @date 04.02.2025
 */
@ExtendWith(MockitoExtension.class)
public class LoginAttemptServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private LoginAttemptRepository loginAttemptRepository;

    @InjectMocks
    private LoginAttemptService loginAttemptService;

    private final String testEmail = "user@example.com";
    private final String testIP = "192.168.1.1";

    @BeforeEach
    void setUp() {
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(new User()));
    }

    @Test
    void testLoginFailed_ShouldIncreaseAttemptCount() {
        User user = new User();
        LoginAttempt loginAttempt = new LoginAttempt();
        loginAttempt.setAttempts(2); // Было 2 попытки

        user.setLoginAttempt(loginAttempt);
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(user));

        loginAttemptService.loginFailed(testEmail, testIP);

        assertEquals(3, loginAttempt.getAttempts()); // Должно стать 3
        verify(loginAttemptRepository).save(loginAttempt);
    }

    @Test
    void testIsBlocked_ShouldReturnTrue_WhenMaxAttemptsReached() {
        User user = new User();
        LoginAttempt loginAttempt = new LoginAttempt();
        loginAttempt.setAttempts(4); // Достигнут лимит
        loginAttempt.setLastModified(ZonedDateTime.now(ZoneOffset.UTC)); // 🛠 Добавляем дату!

        user.setLoginAttempt(loginAttempt);
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(user));

        assertTrue(loginAttemptService.isEmailBlocked(testEmail)); // Метод теперь корректно обрабатывает блокировки
    }

    @Test
    void testIsBlocked_ShouldReturnFalse_WhenAttemptsBelowLimit() {
        User user = new User();
        LoginAttempt loginAttempt = new LoginAttempt();
        loginAttempt.setAttempts(2); // Еще не достиг лимита
        loginAttempt.setLastModified(ZonedDateTime.now(ZoneOffset.UTC)); // 🛠 Добавляем дату!

        user.setLoginAttempt(loginAttempt);
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(user));

        assertFalse(loginAttemptService.isEmailBlocked(testEmail));
    }
}