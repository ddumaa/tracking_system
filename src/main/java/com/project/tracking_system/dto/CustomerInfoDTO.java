package com.project.tracking_system.dto;

import com.project.tracking_system.entity.BuyerReputation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Информация о покупателе, связанная с посылкой.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerInfoDTO {
    private String phone;
    private int sentCount;
    private int pickedUpCount;
    private double pickupPercentage;
    private BuyerReputation reputation;
}
