package com.project.tracking_system.dto;

import com.project.tracking_system.entity.BuyerReputation;
import java.util.List;

/**
 * Краткая статистика о покупателе.
 * <p>
 * Используется при запросе статистики через Telegram.
 * </p>
 */
public class CustomerStatisticsDTO {

    private final int pickedUpCount;
    private final int returnedCount;
    private final List<String> storeNames;
    private final BuyerReputation reputation;

    /**
     * Создать неизменяемый объект статистики покупателя.
     *
     * @param pickedUpCount  количество забранных посылок
     * @param returnedCount  количество не забранных и возвращённых посылок
     * @param storeNames     список магазинов, в которых покупатель делал заказы
     * @param reputation     текущая репутация покупателя
     */
    public CustomerStatisticsDTO(int pickedUpCount,
                                 int returnedCount,
                                 List<String> storeNames,
                                 BuyerReputation reputation) {
        this.pickedUpCount = pickedUpCount;
        this.returnedCount = returnedCount;
        this.storeNames = storeNames;
        this.reputation = reputation;
    }

    /**
     * @return сколько посылок покупатель забрал
     */
    public int getPickedUpCount() {
        return pickedUpCount;
    }

    /**
     * @return сколько посылок было возвращено
     */
    public int getReturnedCount() {
        return returnedCount;
    }

    /**
     * @return список магазинов, где покупатель делал заказы
     */
    public List<String> getStoreNames() {
        return storeNames;
    }

    /**
     * @return текущая репутация покупателя
     */
    public BuyerReputation getReputation() {
        return reputation;
    }
}
