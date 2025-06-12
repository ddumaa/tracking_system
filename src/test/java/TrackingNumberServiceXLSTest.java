import com.project.tracking_system.dto.TrackInfoDTO;
import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.model.TrackingResponse;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.service.store.StoreService;
import com.project.tracking_system.service.track.TrackParcelService;
import com.project.tracking_system.service.track.TrackingNumberServiceXLS;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TrackingNumberServiceXLSTest {

    @Mock
    private TrackParcelService trackParcelService;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private StoreService storeService;

    @InjectMocks
    private TrackingNumberServiceXLS service;

    private TrackInfoListDTO sampleInfo;

    @BeforeEach
    void setup() {
        TrackInfoDTO dto = new TrackInfoDTO("01.01.2024 00:00", "info");
        sampleInfo = new TrackInfoListDTO(java.util.List.of(dto));

        when(subscriptionService.canUploadTracks(anyLong(), anyInt())).thenReturn(1);
        when(subscriptionService.canSaveMoreTracks(anyLong(), anyInt())).thenReturn(1);
        when(trackParcelService.isNewTrack(anyString(), any())).thenReturn(true);
        when(trackParcelService.processTrack(anyString(), any(), any(), anyBoolean())).thenReturn(sampleInfo);
    }

    private MockMultipartFile buildFile(Long storeId) throws Exception {
        Workbook wb = new XSSFWorkbook();
        var sheet = wb.createSheet();
        var header = sheet.createRow(0);
        header.createCell(0).setCellValue("track");
        header.createCell(1).setCellValue("store");
        var row = sheet.createRow(1);
        row.createCell(0).setCellValue("RR123");
        if (storeId != null) {
            row.createCell(1).setCellValue(storeId);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        wb.close();
        return new MockMultipartFile("file", "test.xlsx", "application/vnd.ms-excel", out.toByteArray());
    }

    @Test
    void processTrackingNumber_ValidStoreId_UsesProvided() throws Exception {
        Long userId = 1L;
        Long defaultStore = 10L;
        Long providedStore = 30L;

        when(storeService.getDefaultStoreId(userId)).thenReturn(defaultStore);
        when(storeService.userOwnsStore(providedStore, userId)).thenReturn(true);

        MockMultipartFile file = buildFile(providedStore);

        TrackingResponse resp = service.processTrackingNumber(file, userId);
        assertNotNull(resp);

        ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
        verify(trackParcelService).processTrack(eq("RR123"), captor.capture(), eq(userId), eq(true));
        assertEquals(providedStore, captor.getValue());
    }

    @Test
    void processTrackingNumber_InvalidStoreId_FallsBackToDefault() throws Exception {
        Long userId = 2L;
        Long defaultStore = 11L;
        Long providedStore = 40L;

        when(storeService.getDefaultStoreId(userId)).thenReturn(defaultStore);
        when(storeService.userOwnsStore(providedStore, userId)).thenReturn(false);

        MockMultipartFile file = buildFile(providedStore);

        TrackingResponse resp = service.processTrackingNumber(file, userId);
        assertNotNull(resp);

        ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
        verify(trackParcelService).processTrack(eq("RR123"), captor.capture(), eq(userId), eq(true));
        assertEquals(defaultStore, captor.getValue());
    }

    @Test
    void shutdownExecutor_CalledOnContextClose() {
        TrackingNumberServiceXLS spyService = Mockito.spy(new TrackingNumberServiceXLS(trackParcelService, subscriptionService, storeService));

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(TrackingNumberServiceXLS.class, () -> spyService);
            context.refresh();
        }

        Mockito.verify(spyService).shutdownExecutor();
    }
}
