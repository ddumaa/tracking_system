package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackingResultAdd;
import com.project.tracking_system.entity.PostalServiceType;
import com.project.tracking_system.model.TrackingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrackUploadProcessorServiceTest {

    @Mock
    private TrackExcelParser parser;
    @Mock
    private TrackMetaValidator validator;
    @Mock
    private TrackUploadGroupingService groupingService;
    @Mock
    private TrackBatchProcessingService batchProcessingService;

    private TrackUploadProcessorService processor;

    @BeforeEach
    void setUp() {
        processor = new TrackUploadProcessorService(parser, validator, groupingService, batchProcessingService);
    }

    @Test
    void process_ReturnsResponse() throws Exception {
        MockMultipartFile file = new MockMultipartFile("f", new byte[0]);
        when(parser.parse(file)).thenReturn(List.of(new TrackExcelRow("A1", null, null)));
        TrackMeta meta = new TrackMeta("A1", null, null, true);
        when(validator.validate(anyList(), any())).thenReturn(new TrackMetaValidationResult(List.of(meta), null));
        Map<PostalServiceType, List<TrackMeta>> grouped = Map.of(PostalServiceType.BELPOST, List.of(meta));
        when(groupingService.group(anyList())).thenReturn(grouped);
        List<TrackingResultAdd> results = List.of(new TrackingResultAdd("A1", "ok"));
        when(batchProcessingService.processBatch(grouped, 1L)).thenReturn(results);

        TrackingResponse response = processor.process(file, 1L);

        assertEquals(results, response.getTrackingResults());
    }
}
