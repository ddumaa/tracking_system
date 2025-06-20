package com.project.tracking_system.dto;

import com.project.tracking_system.entity.SubscriptionCode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для работы с тарифными планами.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPlanDTO {
    private SubscriptionCode code;
    private Integer maxTracksPerFile;
    private Integer maxSavedTracks;
    private Integer maxTrackUpdates;
    private boolean allowBulkUpdate;
    private Integer maxStores;
    private boolean allowTelegramNotifications;
    private java.math.BigDecimal monthlyPrice;
    private java.math.BigDecimal annualPrice;
}
