package com.project.tracking_system.model;

import com.project.tracking_system.dto.TrackingResultAdd;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * @author Dmitriy Anisimov
 * @date 09.02.2025
 */
@Getter
@AllArgsConstructor
public class TrackingResponse {
    private final List<TrackingResultAdd> trackingResults;
    private final String limitExceededMessage;
}
