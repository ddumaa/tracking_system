package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackingResultAdd;
import com.project.tracking_system.entity.PostalServiceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dispatches grouped track batches to appropriate processors.
 */
@Service
@RequiredArgsConstructor
public class TrackUpdateDispatcherService {

    private final List<TrackUpdateProcessor> processors;

    /**
     * Routes each group of tracks to its dedicated processor.
     *
     * @param grouped tracks grouped by postal service
     * @param userId  identifier of the user performing update
     * @return list of aggregated results from all processors
     */
    public List<TrackingResultAdd> dispatch(Map<PostalServiceType, List<TrackMeta>> grouped, Long userId) {
        List<TrackingResultAdd> results = new ArrayList<>();
        if (grouped == null || grouped.isEmpty()) {
            return results;
        }
        for (Map.Entry<PostalServiceType, List<TrackMeta>> entry : grouped.entrySet()) {
            processors.stream()
                    .filter(p -> p.supportedType() == entry.getKey())
                    .findFirst()
                    .ifPresent(p -> results.addAll(p.process(entry.getValue(), userId)));
        }
        return results;
    }
}
