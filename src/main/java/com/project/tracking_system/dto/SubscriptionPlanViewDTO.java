package com.project.tracking_system.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/**
 * @author Dmitriy Anisimov
 * @date 21.06.2025
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPlanViewDTO {
    private String code;
    private String name;
    private Integer maxTracksPerFile;
    private Integer maxSavedTracks;
    private Integer maxTrackUpdates;
    private boolean allowBulkUpdate;
    private boolean allowAutoUpdate;
    private Integer maxStores;
    private boolean allowTelegramNotifications;
    private boolean allowCustomBot;
    /** Возможность использовать собственные шаблоны уведомлений. */
    private boolean allowCustomNotifications;
    private String monthlyPriceLabel;
    private String annualPriceLabel;


    private String annualFullPriceLabel;     // "180 BYN"
    private String annualDiscountLabel;      // "выгода −16%"

    /**
     * Позиция плана в общей иерархии.
     */
    private int position;
}