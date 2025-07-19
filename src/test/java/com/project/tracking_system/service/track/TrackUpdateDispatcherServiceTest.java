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
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TrackUpdateDispatcherService}.
 */
@ExtendWith(MockitoExtension.class)
class TrackUpdateDispatcherServiceTest {

    @Mock
    private TrackUpdateProcessor belpostProcessor;
    @Mock
    private TrackUpdateProcessor evropostProcessor;

    private TrackUpdateDispatcherService dispatcher;

    @BeforeEach
    void setUp() {
        when(belpostProcessor.supportedType()).thenReturn(PostalServiceType.BELPOST);
        when(evropostProcessor.supportedType()).thenReturn(PostalServiceType.EVROPOST);
        dispatcher = new TrackUpdateDispatcherService(List.of(belpostProcessor, evropostProcessor));
    }

    @Test
    void dispatch_RoutesToCorrectProcessor() {
        TrackMeta belMeta = new TrackMeta("B1", 1L, null, true, PostalServiceType.BELPOST);
        TrackMeta evrMeta = new TrackMeta("E1", 1L, null, true, PostalServiceType.EVROPOST);
        Map<PostalServiceType, List<TrackMeta>> map = Map.of(
                PostalServiceType.BELPOST, List.of(belMeta),
                PostalServiceType.EVROPOST, List.of(evrMeta)
        );
        when(belpostProcessor.process(List.of(belMeta), 5L)).thenReturn(List.of(new TrackingResultAdd("B1", "ok")));
        when(evropostProcessor.process(List.of(evrMeta), 5L)).thenReturn(List.of(new TrackingResultAdd("E1", "ok")));

        List<TrackingResultAdd> result = dispatcher.dispatch(map, 5L);

        assertEquals(2, result.size());
    }
}
