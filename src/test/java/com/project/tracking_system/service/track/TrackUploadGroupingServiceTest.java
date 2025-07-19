package com.project.tracking_system.service.track;

import com.project.tracking_system.entity.PostalServiceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrackUploadGroupingServiceTest {

    @Mock
    private TypeDefinitionTrackPostService typeDefinitionTrackPostService;

    private TrackUploadGroupingService service;

    @BeforeEach
    void setUp() {
        service = new TrackUploadGroupingService(typeDefinitionTrackPostService);
    }

    @Test
    void group_SplitsByService() {
        when(typeDefinitionTrackPostService.detectPostalService(anyString()))
                .thenReturn(PostalServiceType.BELPOST)
                .thenReturn(PostalServiceType.EVROPOST);

        List<TrackMeta> list = List.of(
                new TrackMeta("B1", 1L, null, true),
                new TrackMeta("E1", 1L, null, true)
        );

        Map<PostalServiceType, List<TrackMeta>> map = service.group(list);

        assertEquals(1, map.get(PostalServiceType.BELPOST).size());
        assertEquals(1, map.get(PostalServiceType.EVROPOST).size());
    }
}
