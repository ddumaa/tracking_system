package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackingResultAdd;
import com.project.tracking_system.entity.PostalServiceType;

import java.util.List;

/**
 * Contract for processing track updates for a specific postal service.
 */
public interface TrackUpdateProcessor {

    /**
     * @return postal service type that this processor handles
     */
    PostalServiceType supportedType();

    /**
     * Processes the provided tracks.
     *
     * @param tracks tracks to process
     * @param userId identifier of the user performing update (may be {@code null})
     * @return list of processing results
     */
    List<TrackingResultAdd> process(List<TrackMeta> tracks, Long userId);
}
