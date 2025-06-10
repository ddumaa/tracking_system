import com.project.tracking_system.entity.Role;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.exception.UserNotAuthenticatedException;
import com.project.tracking_system.util.AuthUtils;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.*;

public class AuthUtilsTest {

    @Test
    void getCurrentUser_ReturnsUser() {
        User user = new User();
        user.setId(1L);
        user.setRole(Role.ROLE_USER);
        Authentication auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());

        User result = AuthUtils.getCurrentUser(auth);

        assertEquals(1L, result.getId());
    }

    @Test
    void getCurrentUser_InvalidAuth_ThrowsException() {
        Authentication auth = new UsernamePasswordAuthenticationToken("anonymous", null);

        assertThrows(UserNotAuthenticatedException.class, () -> AuthUtils.getCurrentUser(auth));
    }
}
