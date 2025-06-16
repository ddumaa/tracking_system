package com.project.tracking_system.service.telegram;

import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.TrackParcel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/**
 * Сервис отправки уведомлений в Telegram-покупателям.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramNotificationService {

    private final BuyerTelegramBot telegramBot;

    /**
     * Отправить уведомление о смене статуса посылки.
     *
     * @param parcel посылка
     * @param status новый статус
     */
    public void sendStatusUpdate(TrackParcel parcel, GlobalStatus status) {
        Long chatId = getChatId(parcel);
        if (chatId == null) {
            log.warn("⛔ Невозможно отправить уведомление: отсутствует чат для трека {}", parcel.getNumber());
            return;
        }

        String text = buildStatusText(parcel, status);
        SendMessage message = new SendMessage(chatId.toString(), text);

        try {
            telegramBot.execute(message);
            log.info("📨 Статус {} отправлен в чат {} для трека {}", status, chatId, parcel.getNumber());
        } catch (TelegramApiException e) {
            log.error("❌ Ошибка отправки уведомления в чат {}: {}", chatId, e.getMessage(), e);
        }
    }

    /**
     * Отправить напоминание о необходимости забрать посылку.
     *
     * @param parcel посылка
     */
    public void sendReminder(TrackParcel parcel) {
        Long chatId = getChatId(parcel);
        if (chatId == null) {
            log.warn("⛔ Невозможно отправить напоминание: отсутствует чат для трека {}", parcel.getNumber());
            return;
        }

        String text = String.format(
                "Напоминание: заберите заказ %s из магазина %s.",
                parcel.getNumber(),
                parcel.getStore().getName()
        );
        SendMessage message = new SendMessage(chatId.toString(), text);

        try {
            telegramBot.execute(message);
            log.info("✅ Напоминание отправлено в чат {} о треке {}", chatId, parcel.getNumber());
        } catch (TelegramApiException e) {
            log.error("❌ Ошибка отправки напоминания в чат {}: {}", chatId, e.getMessage(), e);
        }
    }

    // Получение chatId покупателя из посылки
    private Long getChatId(TrackParcel parcel) {
        if (parcel == null || parcel.getCustomer() == null) {
            return null;
        }
        return parcel.getCustomer().getTelegramChatId();
    }

    // Формирование текста уведомления в зависимости от статуса
    private String buildStatusText(TrackParcel parcel, GlobalStatus status) {
        String storeName = parcel.getStore().getName();
        String track = parcel.getNumber();
        return switch (status) {
            case WAITING_FOR_CUSTOMER ->
                    String.format("Посылка %s из магазина %s прибыла и ждёт вас в пункте выдачи.", track, storeName);
            case DELIVERED ->
                    String.format("Посылка %s из магазина %s получена. Спасибо за покупку!", track, storeName);
            case RETURNED ->
                    String.format("Посылка %s из магазина %s возвращена отправителю.", track, storeName);
            default ->
                    String.format("Ваш заказ %s из магазина %s отправлен.", track, storeName);
        };
    }
}
