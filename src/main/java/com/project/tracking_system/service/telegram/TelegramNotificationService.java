package com.project.tracking_system.service.telegram;

import com.project.tracking_system.entity.BuyerStatus;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.StoreTelegramSettings;
import com.project.tracking_system.mapper.BuyerStatusMapper;
import com.project.tracking_system.service.customer.CustomerService;
import com.project.tracking_system.repository.CustomerTelegramLinkRepository;
import com.project.tracking_system.repository.CustomerRepository;
import com.project.tracking_system.entity.CustomerTelegramLink;
import com.project.tracking_system.service.telegram.TelegramBotResolverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import com.project.tracking_system.utils.PhoneUtils;

/**
 * Сервис отправки уведомлений в Telegram-покупателям.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramNotificationService {

    private final CustomerService customerService;
    private final CustomerTelegramLinkRepository linkRepository;
    private final CustomerRepository customerRepository;
    private final TelegramBotResolverService botResolverService;

    /**
     * Удаляет клиента, связанный с указанным токеном, из кэша.
     *
     * @param token токен бота
     */
    public void invalidateClient(String token) {
        botResolverService.invalidateClient(token);
    }

    /**
     * Удаляет клиента из кэша по токену из настроек магазина.
     *
     * @param settings настройки Telegram магазина
     */
    public void invalidateClient(StoreTelegramSettings settings) {
        botResolverService.invalidateClient(settings);
    }

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
        TelegramClient client = botResolverService.resolveBotForStore(parcel.getStore());

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

        TelegramClient client = botResolverService.resolveBotForStore(parcel.getStore());

        try {
            client.execute(message);
            log.info("✅ Напоминание отправлено в чат {} о треке {}", chatId, parcel.getNumber());
        } catch (TelegramApiException e) {
            log.error("❌ Ошибка отправки напоминания в чат {}: {}", chatId, e.getMessage(), e);
        }
    }

    /**
     * Отправить произвольное сообщение покупателю по номеру телефона.
     *
     * @param phone   номер телефона покупателя
     * @param text    текст сообщения
     * @param storeId идентификатор магазина или {@code null} для системного бота
     * @return {@code true}, если сообщение отправлено хотя бы в один чат
     */
    public boolean sendCustomMessage(String phone, String text, Long storeId) {
        if (phone == null || phone.isBlank() || text == null || text.isBlank()) {
            return false;
        }

        String normalized;
        try {
            normalized = PhoneUtils.normalizePhone(phone);
        } catch (IllegalArgumentException e) {
            log.warn("Некорректный номер телефона {}", phone);
            return false;
        }

        var customerOpt = customerRepository.findByPhone(normalized);
        if (customerOpt.isEmpty()) {
            log.warn("Покупатель с номером {} не найден", normalized);
            return false;
        }

        var links = linkRepository.findActiveLinksByPhone(normalized);
        if (storeId != null) {
            // Фильтруем привязки по магазину или системному боту
            links = links.stream()
                    .filter(l -> storeId == 0
                            ? l.getStore() == null
                            : l.getStore() != null && l.getStore().getId().equals(storeId))
                    .toList();
        }
        if (links.isEmpty()) {
            log.warn("Нет активных Telegram привязок для номера {}", normalized);
            return false;
        }

        boolean sent = false;
        for (CustomerTelegramLink link : links) {
            SendMessage message = new SendMessage(link.getTelegramChatId().toString(), text);
            TelegramClient client = botResolverService.resolveBotForStore(link.getStore());
            try {
                client.execute(message);
                sent = true;
                log.info("📨 Сообщение отправлено в чат {}", link.getTelegramChatId());
            } catch (TelegramApiException e) {
                log.error("❌ Ошибка отправки сообщения в чат {}: {}", link.getTelegramChatId(), e.getMessage(), e);
            }
        }

        return sent;
    }


    // Получение chatId покупателя из привязки к магазину
    private Long getChatId(TrackParcel parcel) {
        if (parcel == null || parcel.getCustomer() == null || parcel.getStore() == null) {
            return null;
        }

        return linkRepository.findByCustomerIdAndStoreId(
                        parcel.getCustomer().getId(),
                        parcel.getStore().getId())
                .filter(CustomerTelegramLink::isTelegramConfirmed)
                .filter(CustomerTelegramLink::isNotificationsEnabled)
                .map(CustomerTelegramLink::getTelegramChatId)
                .orElse(null);
    }

}