import com.project.tracking_system.controller.AuthController;
import com.project.tracking_system.service.user.LoginAttemptService;
import com.project.tracking_system.service.user.UserService;
import com.project.tracking_system.service.user.RegistrationService;
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

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;
    @MockBean
    private RegistrationService registrationService;
    @MockBean
    private LoginAttemptService loginAttemptService;

    @Test
    void registrationPage_ReturnsOk() throws Exception {
        mockMvc.perform(get("/registration"))
                .andExpect(status().isOk());
    }

    @Test
    void loginPage_WithPrincipal_Redirects() throws Exception {
        mockMvc.perform(get("/login").principal(() -> "user"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void registrationPost_CallsService() throws Exception {
        mockMvc.perform(post("/registration")
                        .param("email", "test@test.com")
                        .param("password", "pass123")
                        .param("confirmPassword", "pass123")
                        .param("agreeToTerms", "true"))
                .andExpect(status().isOk());
        verify(registrationService).handleInitialStep(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
