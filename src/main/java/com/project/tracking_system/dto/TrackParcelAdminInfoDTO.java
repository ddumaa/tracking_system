package com.project.tracking_system.dto;

import com.project.tracking_system.entity.RouteDirection;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/**
 * Детальная информация о посылке для административной панели.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TrackParcelAdminInfoDTO {
    private Long id;
    private String number;
    private String status;
    private RouteDirection routeDirection;
    private String storeName;
    private String userEmail;
    private String timestamp;
}
