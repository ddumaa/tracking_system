package com.project.tracking_system.mapper;

import com.project.tracking_system.entity.BuyerStatus;
import com.project.tracking_system.entity.GlobalStatus;
import lombok.Getter;

/**
 * @author Dmitriy Anisimov
 * @date 19.06.2025
 */
@Getter
public class BuyerStatusMapper {

    public static BuyerStatus map(GlobalStatus globalStatus) {
        return switch (globalStatus) {
            case REGISTERED -> BuyerStatus.REGISTERED;
            case IN_TRANSIT -> BuyerStatus.IN_TRANSIT;
            case WAITING_FOR_CUSTOMER -> BuyerStatus.WAITING;
            case DELIVERED -> BuyerStatus.DELIVERED;
            case RETURNED, RETURN_IN_PROGRESS, RETURN_PENDING_PICKUP -> BuyerStatus.RETURNED;
            default -> null;
        };
    }

}