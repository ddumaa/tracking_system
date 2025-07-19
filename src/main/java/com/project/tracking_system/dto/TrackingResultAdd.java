package com.project.tracking_system.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/**
 * Результат обработки одного трек-номера.
 * <p>Содержит номер, итоговый статус, подробную информацию
 * и сообщение об ошибке, если она возникла.</p>
 */

@NoArgsConstructor
@Getter
@Setter
public class TrackingResultAdd {

    /**
     * Номер отслеживания посылки.
     */
    private String trackingNumber;

    /**
     * Статус после обработки.
     */
    private String status;

    /**
     * Детальная информация о посылке.
     */
    private TrackInfoListDTO trackInfo;

    /**
     * Сообщение об ошибке, если обработка завершилась неуспешно.
     */
    private String error;

    public TrackingResultAdd(String trackingNumber, String status) {
        this.trackingNumber = trackingNumber;
        this.status = status;
    }

    public TrackingResultAdd(String trackingNumber, String status, TrackInfoListDTO trackInfo) {
        this.trackingNumber = trackingNumber;
        this.status = status;
        this.trackInfo = trackInfo;
    }

    public TrackingResultAdd(String trackingNumber, String status, TrackInfoListDTO trackInfo, String error) {
        this.trackingNumber = trackingNumber;
        this.status = status;
        this.trackInfo = trackInfo;
        this.error = error;
    }
}
