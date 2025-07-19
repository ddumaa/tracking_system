package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackingResultAdd;
import com.project.tracking_system.entity.PostalServiceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link TrackUpdateCoordinatorService}.
 */
@ExtendWith(MockitoExtension.class)
class TrackUpdateCoordinatorServiceTest {

    @Mock
    private TrackUploadGroupingService groupingService;
    @Mock
    private TrackBatchProcessingService batchProcessingService;

    private TrackUpdateCoordinatorService service;

    @BeforeEach
    void setUp() {
        service = new TrackUpdateCoordinatorService(groupingService, batchProcessingService);
    }

    @Test
    void process_GroupsAndDelegates() {
        TrackMeta meta = new TrackMeta("A1", 1L, null, true);
        Map<PostalServiceType, List<TrackMeta>> grouped = Map.of(PostalServiceType.BELPOST, List.of(meta));
        when(groupingService.group(anyList())).thenReturn(grouped);
        List<TrackingResultAdd> expected = List.of(new TrackingResultAdd("A1", "ok"));
        when(batchProcessingService.processBatch(grouped, 5L)).thenReturn(expected);

        List<TrackingResultAdd> actual = service.process(List.of(meta), 5L);

        assertEquals(expected, actual);
    }
}
