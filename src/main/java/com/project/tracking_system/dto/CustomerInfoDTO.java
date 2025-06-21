package com.project.tracking_system.dto;

import com.project.tracking_system.entity.BuyerReputation;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/**
 * Информация о покупателе, связанная с посылкой.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CustomerInfoDTO {
    private String phone;
    private int sentCount;
    private int pickedUpCount;
    private int returnedCount;
    private double pickupPercentage;
    private BuyerReputation reputation;

    /**
     * Получить отображаемое название репутации покупателя.
     *
     * @return строковое представление репутации
     */
    public String getReputationDisplayName() {
        return reputation.getDisplayName();
    }

    /**
     * Получить CSS-класс для отображения репутации.
     *
     * @return название класса для разметки
     */
    public String getColorClass() {
        return reputation.getColorClass();
    }
}
