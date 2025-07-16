package com.project.tracking_system.service.customer;

import com.project.tracking_system.entity.*;
import com.project.tracking_system.mapper.BuyerStatusMapper;
import com.project.tracking_system.repository.CustomerNotificationLogRepository;
import com.project.tracking_system.repository.CustomerRepository;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.service.telegram.TelegramNotificationService;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import com.project.tracking_system.dto.CustomerStatisticsDTO;

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
     * @return сущность покупателя после обновления
     */
    @Transactional
    public Customer linkTelegramToCustomer(String phone, Long chatId) {
        String normalized = PhoneUtils.normalizePhone(phone);
        log.info("🔗 Попытка привязки телефона {} к чату {}",
                PhoneUtils.maskPhone(normalized), chatId);

        // Регистрируем покупателя при необходимости
        Customer customer = customerService.registerOrGetByPhone(normalized);

        // Если чат уже привязан, повторная привязка игнорируется
        if (customer.getTelegramChatId() != null) {
            log.warn("⚠️ Покупатель {} уже привязан к чату {}", customer.getId(), customer.getTelegramChatId());
            return customer;
        }

        customer.setTelegramChatId(chatId);
        Customer saved = customerRepository.save(customer);
        log.info("✅ Чат {} привязан к покупателю {}", chatId, saved.getId());
        return saved;
    }

    /**
     * Найти покупателя по идентификатору Telegram-чата.
     *
     * @param chatId идентификатор чата
     * @return найденный покупатель или {@link java.util.Optional#empty()}
     */
    @Transactional(readOnly = true)
    public Optional<Customer> findByChatId(Long chatId) {
        if (chatId == null) {
            return Optional.empty();
        }
        return customerRepository.findByTelegramChatId(chatId);
    }

    /**
     * Подтвердить получение уведомления о привязке Telegram.
     *
     * @param customer покупатель
     * @return обновлённый покупатель
     */
    @Transactional
    public Customer confirmTelegram(Customer customer) {
        if (customer == null) {
            throw new IllegalArgumentException("Покупатель не задан");
        }
        if (!customer.isTelegramConfirmed()) {
            customer.setTelegramConfirmed(true);
            customer = customerRepository.save(customer);
            log.info("✅ Покупатель {} подтвердил Telegram", customer.getId());
        }
        return customer;
    }

    /**
     * Отправить текущие статусы всех активных посылок покупателю после привязки Telegram.
     * <p>
     * Метод ищет все посылки покупателя в не финальных статусах и отправляет
     * соответствующие уведомления через Telegram, если такие уведомления ещё не
     * были отправлены ранее.
     * </p>
     *
     * @param customer покупатель, подтвердивший Telegram
     */
    @Transactional
    public void notifyActualStatuses(Customer customer) {
        if (customer == null || customer.getTelegramChatId() == null) {
            return;
        }

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

        return customerRepository.findByTelegramChatId(chatId)
                .filter(Customer::isNotificationsEnabled)
                .map(customer -> {
                    customer.setNotificationsEnabled(false);
                    customerRepository.save(customer);
                    log.info("🔕 Уведомления отключены для покупателя {}", customer.getId());
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

        return customerRepository.findByTelegramChatId(chatId)
                .filter(c -> !c.isNotificationsEnabled())
                .map(customer -> {
                    customer.setNotificationsEnabled(true);
                    customerRepository.save(customer);
                    log.info("🔔 Уведомления включены для покупателя {}", customer.getId());
                    return true;
                })
                .orElse(false);
    }

    /**
     * Получить статистику покупателя по идентификатору Telegram-чата.
     * <p>
     * Возвращает количество забранных и возвращённых посылок,
     * список магазинов, где покупатель делал заказы, и его репутацию.
     * </p>
     *
     * @param chatId идентификатор чата Telegram
     * @return опциональная статистика покупателя
     */
    @Transactional(readOnly = true)
    public Optional<CustomerStatisticsDTO> getStatistics(Long chatId) {
        return customerRepository.findByTelegramChatId(chatId)
                .map(customer -> {
                    List<String> stores = trackParcelRepository.findDistinctStoreNamesByCustomerId(customer.getId());
                    return new CustomerStatisticsDTO(
                            customer.getPickedUpCount(),
                            customer.getReturnedCount(),
                            stores,
                            customer.getReputation()
                    );
                });
    }

}