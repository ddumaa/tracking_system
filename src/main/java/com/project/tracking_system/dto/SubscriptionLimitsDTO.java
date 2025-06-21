package com.project.tracking_system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO лимитов тарифного плана.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionLimitsDTO {
    private Integer maxTracksPerFile;
    private Integer maxSavedTracks;
    private Integer maxTrackUpdates;
    private boolean allowBulkUpdate;
    private Integer maxStores;
    private boolean allowTelegramNotifications;
}
