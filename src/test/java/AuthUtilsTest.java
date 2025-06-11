import com.project.tracking_system.entity.User;
import com.project.tracking_system.utils.AuthUtils;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.*;

public class AuthUtilsTest {

    @Test
    void getCurrentUser_ReturnsUser_WhenAuthenticated() {
        User user = new User();
        Authentication auth = new UsernamePasswordAuthenticationToken(user, user.getPassword(), user.getAuthorities());

        User result = AuthUtils.getCurrentUser(auth);

        assertSame(user, result);
    }

    @Test
    void getCurrentUser_Throws_WhenUserAbsent() {
        Authentication auth = new UsernamePasswordAuthenticationToken("user", "pass");

        assertThrows(SecurityException.class, () -> AuthUtils.getCurrentUser(auth));
    }
}

