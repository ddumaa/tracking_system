package com.project.tracking_system.entity;

import lombok.Data;

/**
 * @author Dmitriy Anisimov
 * @date 19.02.2025
 */
@Data
public class UpdateResult {

    private final boolean success;
    private final int updateCount;
    private final int requestedCount;
    private final String message;

    public UpdateResult(boolean success, int updateCount, int requestedCount, String message) {
        this.success = success;
        this.updateCount = updateCount;
        this.requestedCount = requestedCount;
        this.message = message;
    }

    public UpdateResult(boolean success, String message) {
        this.success = success;
        this.updateCount = 0;
        this.requestedCount = 0;
        this.message = message;
    }

}