import com.project.tracking_system.controller.UploadController;
import com.project.tracking_system.service.store.StoreService;
import com.project.tracking_system.service.track.TrackNumberOcrService;
import com.project.tracking_system.service.track.TrackingNumberServiceXLS;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UploadController.class)
@AutoConfigureMockMvc(addFilters = false)
public class UploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TrackingNumberServiceXLS trackingNumberServiceXLS;
    @MockBean
    private TrackNumberOcrService trackNumberOcrService;
    @MockBean
    private StoreService storeService;

    @Test
    void uploadFile_Empty_ReturnsHome() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", new byte[0]);
        mockMvc.perform(multipart("/upload").file(file))
                .andExpect(status().isOk());
    }
}
