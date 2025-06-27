package com.project.tracking_system.service.telegram;

import com.project.tracking_system.entity.BuyerStatus;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.StoreTelegramSettings;
import com.project.tracking_system.mapper.BuyerStatusMapper;
import com.project.tracking_system.service.customer.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import com.project.tracking_system.service.telegram.TelegramClientFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис отправки уведомлений в Telegram-покупателям.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramNotificationService {

    private final TelegramClient telegramClient;
    private final CustomerService customerService;
    private final TelegramClientFactory telegramClientFactory;
    /** Кэш клиентов Telegram для пользовательских ботов. */
    private final Map<String, TelegramClient> clientCache = new ConcurrentHashMap<>();

    /**
     * Отправить уведомление о смене статуса посылки.
     * <p>
     * Если в профиле магазина указана подпись, она будет добавлена
     * в конец сообщения.
     * </p>
     *
     * @param parcel посылка
     * @param status новый статус
     */
    public void sendStatusUpdate(TrackParcel parcel, GlobalStatus status) {
        if (!customerService.isNotifiable(parcel.getCustomer(), parcel.getStore())) {
            log.warn("⛔ Уведомление не отправлено: условия не выполнены для трека {}", parcel.getNumber());
            return;
        }

        StoreTelegramSettings settings = parcel.getStore().getTelegramSettings();
        if (settings != null && !settings.isEnabled()) {
            log.debug("Уведомления Telegram отключены для магазина {}", parcel.getStore().getId());
            return;
        }

        BuyerStatus buyerStatus = BuyerStatusMapper.map(status);
        if (buyerStatus == null) {
            log.debug("Статус {} не предназначен для уведомления покупателя", status);
            return;
        }

        Long chatId = getChatId(parcel);
        if (chatId == null) {
            log.warn("⚠️ Чат покупателя не найден: уведомление не отправлено для трека {}", parcel.getNumber());
            return;
        }
        String text;
        if (settings != null && settings.getTemplatesMap().containsKey(buyerStatus)) {
            text = settings.getTemplatesMap().get(buyerStatus)
                    .replace("{track}", parcel.getNumber())
                    .replace("{store}", parcel.getStore().getName());
        } else {
            text = buyerStatus.formatMessage(parcel.getNumber(), parcel.getStore().getName());
        }

        if (settings != null && settings.getCustomSignature() != null && !settings.getCustomSignature().isBlank()) {
            text += "\n\n" + settings.getCustomSignature();
        }

        SendMessage message = new SendMessage(chatId.toString(), text);
        TelegramClient client = resolveClient(settings);

        try {
            client.execute(message);
            log.info("📨 Уведомление отправлено: {} (статус {}) в чат {} для трека {}",
                    text, status, chatId, parcel.getNumber());
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
        if (chatId == null) {
            log.warn("⚠️ Чат покупателя не найден: напоминание не отправлено для трека {}", parcel.getNumber());
            return;
        }

        String text;
        if (settings != null && settings.getReminderTemplate() != null && !settings.getReminderTemplate().isBlank()) {
            text = settings.getReminderTemplate()
                    .replace("{track}", parcel.getNumber())
                    .replace("{store}", parcel.getStore().getName());
        } else {
            text = String.format(
                    "🔔 Не забудьте забрать посылку %s из магазина %s — она ждёт вас в пункте выдачи.",
                    parcel.getNumber(),
                    parcel.getStore().getName()
            );
        }

        if (settings != null && settings.getCustomSignature() != null && !settings.getCustomSignature().isBlank()) {
            text += "\n\n" + settings.getCustomSignature();
        }

        SendMessage message = new SendMessage(chatId.toString(), text);

        TelegramClient client = resolveClient(settings);

        try {
            client.execute(message);
            log.info("✅ Напоминание отправлено в чат {} о треке {}", chatId, parcel.getNumber());
        } catch (TelegramApiException e) {
            log.error("❌ Ошибка отправки напоминания в чат {}: {}", chatId, e.getMessage(), e);
        }
    }

    // Получение TelegramClient с учётом пользовательского токена
    private TelegramClient resolveClient(StoreTelegramSettings settings) {
        if (settings == null) {
            return telegramClient;
        }

        String token = settings.getBotToken();
        String username = settings.getBotUsername();

        // Считаем токен валидным, если он не пустой и для него сохранено имя бота
        if (token == null || token.isBlank() || username == null) {
            return telegramClient;
        }

        // Создаём или берём из кэша клиента для данного токена
        return clientCache.computeIfAbsent(token, telegramClientFactory::create);
    }

    // Получение chatId покупателя из посылки
    private Long getChatId(TrackParcel parcel) {
        if (parcel == null || parcel.getCustomer() == null) {
            return null;
        }
        return parcel.getCustomer().getTelegramChatId();
    }

}