package com.project.tracking_system.service.track;

import com.project.tracking_system.entity.PostalServiceType;
import com.project.tracking_system.service.track.TrackBatchData;
import com.project.tracking_system.service.track.TrackMeta;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.mock.web.MockMultipartFile;
import java.io.ByteArrayOutputStream;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.service.store.StoreService;
import com.project.tracking_system.service.track.TrackParcelService;
import com.project.tracking_system.service.track.TypeDefinitionTrackPostService;
import com.project.tracking_system.service.track.TrackFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrackingNumberServiceXLSTest {

    @Mock
    private TrackParcelService trackParcelService;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private StoreService storeService;
    @Mock
    private TypeDefinitionTrackPostService typeDefinitionTrackPostService;
    @Mock
    private TrackFacade trackFacade;

    private TrackingNumberServiceXLS service;

    @BeforeEach
    void setUp() {
        service = new TrackingNumberServiceXLS(
                trackParcelService,
                subscriptionService,
                storeService,
                typeDefinitionTrackPostService,
                trackFacade
        );
    }

    @Test
    void processTrackingNumber_GuestUser_ParsesTracks() throws Exception {
        XSSFWorkbook workbook = new XSSFWorkbook();
        var sheet = workbook.createSheet();
        sheet.createRow(0).createCell(0).setCellValue("num");
        sheet.createRow(1).createCell(0).setCellValue("AA111");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);

        MockMultipartFile file = new MockMultipartFile("file", out.toByteArray());

        when(typeDefinitionTrackPostService.detectPostalService("AA111"))
                .thenReturn(PostalServiceType.BELPOST);

        TrackBatchData result = service.processTrackingNumber(file, null);

        assertNull(result.limitExceededMessage());
        assertEquals(1, result.tracksByService().get(PostalServiceType.BELPOST).size());

        TrackMeta meta = result.tracksByService().get(PostalServiceType.BELPOST).get(0);
        assertEquals("AA111", meta.number());
        assertNull(meta.storeId());
    }
}
