import com.project.tracking_system.controller.PasswordController;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.service.user.PasswordResetService;
import com.project.tracking_system.service.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PasswordController.class)
@AutoConfigureMockMvc(addFilters = false)
public class PasswordControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PasswordResetService passwordResetService;
    @MockBean
    private UserService userService;

    @Test
    void forgotPasswordPage_ReturnsOk() throws Exception {
        mockMvc.perform(get("/forgot-password"))
                .andExpect(status().isOk());
    }

    @Test
    void resetPassword_InvalidToken_ReturnsForgotPassword() throws Exception {
        when(passwordResetService.isTokenValid("bad")).thenReturn(false);
        mockMvc.perform(get("/reset-password").param("token", "bad"))
                .andExpect(status().isOk());
    }

    @Test
    void forgotPasswordPost_CallsService() throws Exception {
        when(userService.findByUserEmail("a@b.c")).thenReturn(java.util.Optional.of(new User()));
        mockMvc.perform(post("/forgot-password").param("email", "a@b.c"))
                .andExpect(status().isOk());
        verify(passwordResetService).createPasswordResetToken("a@b.c");
    }
}
