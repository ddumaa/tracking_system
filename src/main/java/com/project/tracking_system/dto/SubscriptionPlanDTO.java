package com.project.tracking_system.dto;

import com.project.tracking_system.entity.SubscriptionCode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO для отображения с тарифных планов.
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
    private BigDecimal monthlyPrice;
    private BigDecimal annualPrice;
}