package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackingResultAdd;
import com.project.tracking_system.entity.PostalServiceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Coordinates update of track numbers for different postal services.
 * <p>
 * Service groups provided {@link TrackMeta} objects by {@link PostalServiceType}
 * and delegates processing to {@link TrackUpdateDispatcherService}.
 * It is used both for file uploads and manual update flows.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class TrackUpdateCoordinatorService {

    private final TrackUploadGroupingService groupingService;
    private final TrackUpdateDispatcherService dispatcherService;

    /**
     * Processes the given tracks for the specified user.
     *
     * @param tracks list of track metadata
     * @param userId id of the user performing the update
     * @return list of aggregated processing results
     */
    public List<TrackingResultAdd> process(List<TrackMeta> tracks, Long userId) {
        Map<PostalServiceType, List<TrackMeta>> grouped = groupingService.group(tracks);
        return dispatcherService.dispatch(grouped, userId);
    }
}
