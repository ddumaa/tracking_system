package com.project.tracking_system.service.telegram;

import com.project.tracking_system.entity.BuyerStatus;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.StoreTelegramSettings;
import com.project.tracking_system.mapper.BuyerStatusMapper;
import com.project.tracking_system.service.customer.CustomerService;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Сервис отправки уведомлений в Telegram-покупателям.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramNotificationService {

    /** Стандартный шаблон напоминания. */
    public static final String DEFAULT_REMINDER_TEMPLATE =
            "\uD83D\uDD14 Не забудьте забрать посылку {track} из магазина {store} — она ждёт вас в пункте выдачи.";

    private final TelegramClient telegramClient;
    private final CustomerService customerService;

    /**
     * Отправить уведомление о смене статуса посылки.
     * <p>
     * Метод возвращает {@code true}, только если сообщение действительно было
     * доставлено в Telegram. Во всех случаях раннего выхода возвращается
     * {@code false}, что позволяет вызывающему коду не сохранять запись об
     * уведомлении в журнале.
     * </p>
     *
     * @param parcel посылка
     * @param status новый статус
     * @return {@code true}, если сообщение отправлено успешно
     */
    public boolean sendStatusUpdate(TrackParcel parcel, GlobalStatus status) {
        if (!customerService.isNotifiable(parcel.getCustomer(), parcel.getStore())) {
            log.warn("⛔ Уведомление не отправлено: условия не выполнены для трека {}", parcel.getNumber());
            return false;
        }

        StoreTelegramSettings settings = parcel.getStore().getTelegramSettings();
        if (settings != null && !settings.isEnabled()) {
            log.debug("Уведомления Telegram отключены для магазина {}", parcel.getStore().getId());
            return false;
        }

        BuyerStatus buyerStatus = BuyerStatusMapper.map(status);
        if (buyerStatus == null) {
            log.debug("Статус {} не предназначен для уведомления покупателя", status);
            return false;
        }

        Long chatId = getChatId(parcel);
        if (chatId == null) {
            log.warn("⛔ Уведомление не отправлено: отсутствует чат для покупателя трека {}", parcel.getNumber());
            return false;
        }

        String text;
        if (settings != null && settings.getTemplatesMap().containsKey(buyerStatus)) {
            text = settings.getTemplatesMap().get(buyerStatus)
                    .replace("{track}", parcel.getNumber())
                    .replace("{store}", parcel.getStore().getName());
        } else {
            text = buyerStatus.formatMessage(parcel.getNumber(), parcel.getStore().getName());
        }

        SendMessage message = new SendMessage(chatId.toString(), text);

        try {
            telegramClient.execute(message);
            log.info("📨 Уведомление отправлено: {} (статус {}) в чат {} для трека {}",
                    text, status, chatId, parcel.getNumber());
            return true;
        } catch (TelegramApiException e) {
            log.error("❌ Ошибка отправки уведомления в чат {}: {}", chatId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Отправить напоминание о необходимости забрать посылку.
     *
     * @param parcel посылка
     */
    public void sendReminder(TrackParcel parcel) {
        if (!customerService.isNotifiable(parcel.getCustomer(), parcel.getStore())) {
            log.warn("⛔ Напоминание не отправлено: условия не выполнены для трека {}", parcel.getNumber());
            return;
        }

        StoreTelegramSettings settings = parcel.getStore().getTelegramSettings();
        if (settings != null && (!settings.isEnabled() || !settings.isRemindersEnabled())) {
            log.debug("Напоминания отключены для магазина {}", parcel.getStore().getId());
            return;
        }

        Long chatId = getChatId(parcel);

        String template = (settings != null && StringUtils.hasText(settings.getReminderTemplate()))
                ? settings.getReminderTemplate()
                : DEFAULT_REMINDER_TEMPLATE;
        String text = template.replace("{track}", parcel.getNumber())
                .replace("{store}", parcel.getStore().getName());

        SendMessage message = new SendMessage(chatId.toString(), text);

        try {
            telegramClient.execute(message);
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

}