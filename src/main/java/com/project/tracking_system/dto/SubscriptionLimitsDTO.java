package com.project.tracking_system.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/**
 * DTO лимитов тарифного плана.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionLimitsDTO {
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
}
