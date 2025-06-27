package com.project.tracking_system.service.customer;

import com.project.tracking_system.entity.*;
import com.project.tracking_system.mapper.BuyerStatusMapper;
import com.project.tracking_system.repository.CustomerNotificationLogRepository;
import com.project.tracking_system.repository.CustomerRepository;
import com.project.tracking_system.repository.CustomerTelegramLinkRepository;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.service.telegram.TelegramNotificationService;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import com.project.tracking_system.utils.PhoneUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сервис привязки Telegram-чатов к покупателям.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerTelegramService {

    private final CustomerRepository customerRepository;
    private final CustomerTelegramLinkRepository linkRepository;
    private final CustomerService customerService;
    private final TrackParcelRepository trackParcelRepository;
    private final CustomerNotificationLogRepository notificationLogRepository;
    private final TelegramNotificationService telegramNotificationService;

    /**
     * Привязать чат Telegram к покупателю по номеру телефона.
     * <p>
     * Номер телефона нормализуется до формата 375XXXXXXXXX. Если покупатель с
     * таким номером существует и не имеет привязанного чата, чат будет сохранён.
     * При отсутствии покупателя создаётся новая запись с нейтральной репутацией.
     * Повторная привязка уже связанного покупателя игнорируется.
     * </p>
     *
     * @param phone  номер телефона в произвольном формате
     * @param chatId идентификатор чата Telegram
     * @return созданная привязка Telegram
     */
    @Transactional
    public CustomerTelegramLink linkTelegramToCustomer(String phone, Long chatId) {
        String normalized = PhoneUtils.normalizePhone(phone);
        log.info("🔗 Попытка привязки телефона {} к чату {}", normalized, chatId);

        // Регистрируем покупателя при необходимости
        Customer customer = customerService.registerOrGetByPhone(normalized);

        // Проверяем существующую привязку
        Optional<CustomerTelegramLink> existing = linkRepository.findByTelegramChatId(chatId);
        if (existing.isPresent()) {
            log.warn("⚠️ Чат {} уже привязан к покупателю {}", chatId, existing.get().getCustomer().getId());
            return existing.get();
        }

        CustomerTelegramLink link = new CustomerTelegramLink();
        link.setCustomer(customer);
        link.setTelegramChatId(chatId);
        link.setLinkedAt(ZonedDateTime.now(ZoneOffset.UTC));
        CustomerTelegramLink saved = linkRepository.save(link);
        log.info("✅ Чат {} привязан к покупателю {}", chatId, customer.getId());
        return saved;
    }

    /**
     * Найти привязку по идентификатору Telegram-чата.
     *
     * @param chatId идентификатор чата
     * @return найденная привязка или {@link java.util.Optional#empty()}
     */
    @Transactional(readOnly = true)
    public Optional<CustomerTelegramLink> findByChatId(Long chatId) {
        if (chatId == null) {
            return Optional.empty();
        }
        return linkRepository.findByTelegramChatId(chatId);
    }

    /**
     * Подтвердить получение уведомления о привязке Telegram.
     *
     * @param link привязка Telegram
     * @return обновлённая привязка
     */
    @Transactional
    public CustomerTelegramLink confirmTelegram(CustomerTelegramLink link) {
        if (link == null) {
            throw new IllegalArgumentException("Привязка не задана");
        }
        if (!link.isTelegramConfirmed()) {
            link.setTelegramConfirmed(true);
            link = linkRepository.save(link);
            log.info("✅ Покупатель {} подтвердил Telegram", link.getCustomer().getId());
        }
        return link;
    }

    /**
     * Отправить текущие статусы всех активных посылок покупателю после привязки Telegram.
     * <p>
     * Метод ищет все посылки покупателя в не финальных статусах и отправляет
     * соответствующие уведомления через Telegram, если такие уведомления ещё не
     * были отправлены ранее.
     * </p>
     *
     * @param link привязка покупателя к Telegram
     */
    @Transactional
    public void notifyActualStatuses(CustomerTelegramLink link) {
        if (link == null || link.getTelegramChatId() == null) {
            return;
        }

        Customer customer = link.getCustomer();

        List<TrackParcel> parcels = trackParcelRepository.findActiveByCustomerId(
                customer.getId(),
                List.of(GlobalStatus.DELIVERED, GlobalStatus.RETURNED)
        );

        for (TrackParcel parcel : parcels) {
            GlobalStatus status = parcel.getStatus();

            if (notificationLogRepository.existsByParcelIdAndStatusAndNotificationType(
                    parcel.getId(), status, NotificationType.INSTANT)) {
                continue;
            }

            BuyerStatus buyerStatus = BuyerStatusMapper.map(status);
            if (buyerStatus == null) {
                continue; // статус не подлежит уведомлению
            }

            telegramNotificationService.sendStatusUpdate(parcel, status);

            CustomerNotificationLog logEntry = new CustomerNotificationLog();
            logEntry.setCustomer(customer);
            logEntry.setParcel(parcel);
            logEntry.setStatus(status);
            logEntry.setNotificationType(NotificationType.INSTANT);
            logEntry.setSentAt(ZonedDateTime.now(ZoneOffset.UTC));
            notificationLogRepository.save(logEntry);
        }
    }

    /**
     * Отключить отправку Telegram-уведомлений покупателю.
     *
     * @param chatId идентификатор Telegram-чата
     * @return {@code true}, если уведомления были отключены
     */
    @Transactional
    public boolean disableNotifications(Long chatId) {
        if (chatId == null) {
            return false;
        }

        return linkRepository.findByTelegramChatId(chatId)
                .filter(CustomerTelegramLink::isNotificationsEnabled)
                .map(link -> {
                    link.setNotificationsEnabled(false);
                    linkRepository.save(link);
                    log.info("🔕 Уведомления отключены для покупателя {}", link.getCustomer().getId());
                    return true;
                })
                .orElse(false);
    }

    /**
     * Включить отправку Telegram-уведомлений покупателю.
     *
     * @param chatId идентификатор Telegram-чата
     * @return {@code true}, если уведомления были включены
     */
    @Transactional
    public boolean enableNotifications(Long chatId) {
        if (chatId == null) {
            return false;
        }

        return linkRepository.findByTelegramChatId(chatId)
                .filter(link -> !link.isNotificationsEnabled())
                .map(link -> {
                    link.setNotificationsEnabled(true);
                    linkRepository.save(link);
                    log.info("🔔 Уведомления включены для покупателя {}", link.getCustomer().getId());
                    return true;
                })
                .orElse(false);
    }

}