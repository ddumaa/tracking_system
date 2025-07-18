package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.dto.TrackingResultAdd;
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
    void processSingleTracking_EmptyInfo_ReturnsNoData() throws Exception {
        TrackInfoListDTO dto = new TrackInfoListDTO();
        when(trackFacade.processTrack(anyString(), any(), any(), anyBoolean(), any())).thenReturn(dto);

        var method = TrackingNumberServiceXLS.class.getDeclaredMethod(
                "processSingleTracking",
                String.class,
                Long.class,
                Long.class,
                boolean.class,
                String.class
        );
        method.setAccessible(true);

        TrackingResultAdd result = (TrackingResultAdd) method.invoke(service,
                "AA111", null, 1L, true, null);

        assertEquals("AA111", result.getTrackingNumber());
        assertEquals("Нет данных", result.getStatus());
    }
}
