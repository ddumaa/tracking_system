package com.project.tracking_system.mapper;

import com.project.tracking_system.entity.BuyerStatus;
import com.project.tracking_system.entity.GlobalStatus;

/**
 * Утилита для сопоставления глобального статуса с пользовательским.
 * <p>
 * Позволяет определить, какое уведомление отправить покупателю в зависимости
 * от текущего статуса посылки.
 * </p>
 */
public final class BuyerStatusMapper {

    private BuyerStatusMapper() {
    }

    /**
     * Преобразует глобальный статус в статус, предназначенный для отображения
     * покупателю.
     *
     * @param globalStatus глобальный статус посылки
     * @return подходящий {@link BuyerStatus} или {@code null}, если статус не
     *         предназначен для уведомления
     */
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
