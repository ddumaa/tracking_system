package com.project.tracking_system.dto;

import java.util.List;

/**
 * Сводка посылок покупателя для отображения в Telegram.
 * <p>
 * Хранит отдельные списки по ключевым категориям статусов,
 * чтобы бот мог сформировать удобное сообщение с разделами.
 * </p>
 */
public class TelegramParcelsOverviewDTO {

    private final List<TelegramParcelInfoDTO> delivered;
    private final List<TelegramParcelInfoDTO> waitingForPickup;
    private final List<TelegramParcelInfoDTO> inTransit;

    /**
     * Создаёт сводку посылок, разделённых по статусам.
     *
     * @param delivered       посылки со статусом «Получена»
     * @param waitingForPickup посылки, ожидающие покупателя
     * @param inTransit       посылки, находящиеся в пути
     */
    public TelegramParcelsOverviewDTO(List<TelegramParcelInfoDTO> delivered,
                                      List<TelegramParcelInfoDTO> waitingForPickup,
                                      List<TelegramParcelInfoDTO> inTransit) {
        this.delivered = delivered;
        this.waitingForPickup = waitingForPickup;
        this.inTransit = inTransit;
    }

    /**
     * @return список посылок со статусом «Получена»
     */
    public List<TelegramParcelInfoDTO> getDelivered() {
        return delivered;
    }

    /**
     * @return список посылок, ожидающих покупателя
     */
    public List<TelegramParcelInfoDTO> getWaitingForPickup() {
        return waitingForPickup;
    }

    /**
     * @return список посылок, находящихся в пути
     */
    public List<TelegramParcelInfoDTO> getInTransit() {
        return inTransit;
    }
}
