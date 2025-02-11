package com.project.tracking_system.service;

import com.project.tracking_system.entity.SubscriptionPlan;
import com.project.tracking_system.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @author Dmitriy Anisimov
 * @date 11.02.2025
 */
@Service
@Slf4j
public class SubscriptionService {

    public boolean canUploadTracks(User user, int tracksCount) {
        SubscriptionPlan plan = user.getSubscriptionPlan();
        if(plan == null) {
            return false;
        }
        return plan.getMaxTracksPerFile() == null || tracksCount <= plan.getMaxTracksPerFile();
    }

    public boolean canSaveMoreTracks(User user, int currentTracks) {
        SubscriptionPlan plan = user.getSubscriptionPlan();
        if(plan == null) {
            return false;
        }
        return plan.getMaxSavedTracks() == null || currentTracks < plan.getMaxSavedTracks();
    }

    public boolean canUpdateTracks(User user, int updatesMade) {
        SubscriptionPlan plan = user.getSubscriptionPlan();
        if(plan == null) {
            return false;
        }
        return plan.getMaxTrackUpdates() == null || updatesMade < plan.getMaxTrackUpdates();
    }

    public boolean canUseBulkUpdate(User user) {
        SubscriptionPlan plan = user.getSubscriptionPlan();
        return plan != null && plan.isAllowBulkUpdate();
    }
}