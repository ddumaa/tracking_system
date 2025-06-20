package com.project.tracking_system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Детальная информация о посылке для административной панели.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrackParcelAdminInfoDTO {
    private Long id;
    private String number;
    private String status;
    private String storeName;
    private String userEmail;
    private String timestamp;
}
