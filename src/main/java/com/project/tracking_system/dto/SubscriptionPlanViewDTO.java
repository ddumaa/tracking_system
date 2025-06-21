package com.project.tracking_system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Dmitriy Anisimov
 * @date 21.06.2025
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPlanViewDTO {
    private String code;
    private Integer maxTracksPerFile;
    private Integer maxSavedTracks;
    private Integer maxTrackUpdates;
    private boolean allowBulkUpdate;
    private Integer maxStores;
    private boolean allowTelegramNotifications;
    private String monthlyPriceLabel;
    private String annualPriceLabel;


    private String annualFullPriceLabel;     // "180 BYN"
    private String annualDiscountLabel;      // "выгода −16%"
}