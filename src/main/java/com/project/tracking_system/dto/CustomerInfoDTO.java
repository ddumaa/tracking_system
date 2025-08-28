package com.project.tracking_system.dto;

import com.project.tracking_system.entity.BuyerReputation;
import com.project.tracking_system.entity.NameSource;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Информация о покупателе, связанная с посылкой.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CustomerInfoDTO {
    private String phone;
    private String fullName;
    private NameSource nameSource;
    private int sentCount;
    private int pickedUpCount;
    private int returnedCount;
    private double pickupPercentage;
    private BuyerReputation reputation;

    /**
     * Получить полное имя покупателя.
     *
     * @return ФИО покупателя
     */
    public String getFullName() {
        return fullName;
    }

    /**
     * Установить полное имя покупателя.
     *
     * @param fullName ФИО покупателя
     */
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    /**
     * Получить источник имени покупателя.
     *
     * @return источник данных имени
     */
    public NameSource getNameSource() {
        return nameSource;
    }

    /**
     * Установить источник имени покупателя.
     *
     * @param nameSource источник данных имени
     */
    public void setNameSource(NameSource nameSource) {
        this.nameSource = nameSource;
    }

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
