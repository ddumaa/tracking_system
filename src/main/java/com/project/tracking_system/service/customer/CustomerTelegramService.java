package com.project.tracking_system.service.customer;

import com.project.tracking_system.dto.CustomerTelegramLinkDTO;
import com.project.tracking_system.entity.*;
import com.project.tracking_system.mapper.BuyerStatusMapper;
import com.project.tracking_system.repository.CustomerNotificationLogRepository;
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

    private final CustomerTelegramLinkRepository linkRepository;
    private final CustomerService customerService;
    private final TrackParcelRepository trackParcelRepository;
    private final CustomerNotificationLogRepository notificationLogRepository;
    private final TelegramNotificationService telegramNotificationService;

    /**
     * Привязать чат Telegram к покупателю по номеру телефона.
     * <p>
     * Номер телефона нормализуется до формата 375XXXXXXXXX. Если покупатель
     * с таким номером существует и не имеет привязанного чата, будет создана
     * новая привязка. При отсутствии покупателя создаётся новая запись.
     * Для новых связей флаги {@code telegramConfirmed} и
     * {@code notificationsEnabled} устанавливаются в {@code true}.
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
        link.setTelegramConfirmed(true);
        link.setNotificationsEnabled(true);

        CustomerTelegramLink saved = linkRepository.save(link);
        log.info("✅ Чат {} привязан к покупателю {}", chatId, customer.getId());
        return saved;
    }

    /**
     * Создать или обновить привязку к магазину по номеру телефона.
     * <p>
     * Если привязка для указанного магазина уже существует, обновляется chatId
     * и дата привязки. Иначе создаётся новая запись с подтверждённым Telegram
     * и включёнными уведомлениями.
     * </p>
     *
     * @param phone  номер телефона покупателя
     * @param store  магазин, к которому осуществляется привязка
     * @param chatId идентификатор чата Telegram
     * @return созданная или обновлённая привязка
     */
    @Transactional
    public CustomerTelegramLink linkTelegramToCustomer(String phone, Store store, Long chatId) {
        String normalized = PhoneUtils.normalizePhone(phone);
        log.info("🔗 Привязка телефона {} к чату {} в магазине {}", normalized, chatId,
                store != null ? store.getId() : null);

        Customer customer = customerService.registerOrGetByPhone(normalized);

        Optional<CustomerTelegramLink> existing = Optional.empty();
        if (store != null) {
            existing = linkRepository.findByCustomerIdAndStoreId(customer.getId(), store.getId());
        }

        CustomerTelegramLink link = existing.orElseGet(CustomerTelegramLink::new);
        link.setCustomer(customer);
        link.setStore(store);
        link.setTelegramChatId(chatId);
        link.setLinkedAt(ZonedDateTime.now(ZoneOffset.UTC));

        if (existing.isEmpty()) {
            link.setTelegramConfirmed(true);
            link.setNotificationsEnabled(true);
        }

        CustomerTelegramLink saved = linkRepository.save(link);
        log.info("✅ Привязка сохранена для покупателя {} и магазина {}", customer.getId(),
                store != null ? store.getId() : null);
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
     * Найти привязку по чату и магазину.
     *
     * @param chatId  идентификатор чата
     * @param storeId идентификатор магазина
     * @return найденная привязка или {@link java.util.Optional#empty()}
     */
    @Transactional(readOnly = true)
    public Optional<CustomerTelegramLink> findByChatIdAndStore(Long chatId, Long storeId) {
        if (chatId == null || storeId == null) {
            return Optional.empty();
        }
        return linkRepository.findByTelegramChatIdAndStoreId(chatId, storeId);
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
        if (link == null || link.getCustomer() == null) {
            return;
        }

        Customer customer = link.getCustomer();

        // Получаем все активные привязки покупателя
        List<CustomerTelegramLink> activeLinks = linkRepository
                .findByCustomerIdAndTelegramConfirmedTrueAndNotificationsEnabledTrue(customer.getId());

        for (CustomerTelegramLink activeLink : activeLinks) {
            Store store = activeLink.getStore();
            if (store == null) {
                continue;
            }

            StoreTelegramSettings settings = store.getTelegramSettings();
            if (settings != null && !settings.isEnabled()) {
                continue;
            }

            List<TrackParcel> parcels = trackParcelRepository.findActiveByCustomerIdAndStoreId(
                    customer.getId(),
                    store.getId(),
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
    }

    /**
     * Отключить уведомления по конкретной привязке.
     *
     * @param chatId  идентификатор Telegram-чата
     * @param storeId идентификатор магазина
     * @return {@code true}, если уведомления были отключены
     */
    @Transactional
    public boolean disableNotifications(Long chatId, Long storeId) {
        if (chatId == null || storeId == null) {
            return false;
        }

        return linkRepository.findByTelegramChatIdAndStoreId(chatId, storeId)
                .filter(CustomerTelegramLink::isNotificationsEnabled)
                .map(link -> {
                    link.setNotificationsEnabled(false);
                    linkRepository.save(link);
                    log.info("🔕 Уведомления отключены для покупателя {} в магазине {}",
                            link.getCustomer().getId(), storeId);
                    return true;
                })
                .orElse(false);
    }

    /**
     * Включить уведомления по привязке.
     *
     * @param chatId  идентификатор Telegram-чата
     * @param storeId идентификатор магазина
     * @return {@code true}, если уведомления были включены
     */
    @Transactional
    public boolean enableNotifications(Long chatId, Long storeId) {
        if (chatId == null || storeId == null) {
            return false;
        }

        return linkRepository.findByTelegramChatIdAndStoreId(chatId, storeId)
                .filter(link -> !link.isNotificationsEnabled())
                .map(link -> {
                    link.setNotificationsEnabled(true);
                    linkRepository.save(link);
                    log.info("🔔 Уведомления включены для покупателя {} в магазине {}",
                            link.getCustomer().getId(), storeId);
                    return true;
                })
                .orElse(false);
    }

    /**
     * Отключить уведомления по всем привязкам чата (для обратной совместимости).
     *
     * @param chatId идентификатор чата
     * @return {@code true}, если хотя бы одна привязка была отключена
     */
    @Transactional
    public boolean disableNotifications(Long chatId) {
        if (chatId == null) {
            return false;
        }

        boolean[] result = {false};
        linkRepository.findByTelegramChatId(chatId).ifPresent(link -> {
            link.setNotificationsEnabled(false);
            linkRepository.save(link);
            log.info("🔕 Уведомления отключены для покупателя {}", link.getCustomer().getId());
            result[0] = true;
        });
        return result[0];
    }

    /**
     * Включить уведомления по всем привязкам чата (для обратной совместимости).
     *
     * @param chatId идентификатор чата
     * @return {@code true}, если хотя бы одна привязка была включена
     */
    @Transactional
public boolean enableNotifications(Long chatId) {
        if (chatId == null) {
            return false;
        }

        boolean[] result = {false};
        linkRepository.findByTelegramChatId(chatId).ifPresent(link -> {
            link.setNotificationsEnabled(true);
            linkRepository.save(link);
            log.info("🔔 Уведомления включены для покупателя {}", link.getCustomer().getId());
            result[0] = true;
        });
        return result[0];
    }

    /**
     * Получить список привязок Telegram для магазина.
     *
     * @param storeId идентификатор магазина
     * @return список DTO привязок
     */
    @Transactional(readOnly = true)
    public List<CustomerTelegramLinkDTO> getLinksByStore(Long storeId) {
        if (storeId == null) {
            return List.of();
        }
        return linkRepository.findByStoreId(storeId).stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Изменить состояние уведомлений по привязке.
     *
     * @param linkId  идентификатор привязки
     * @param storeId идентификатор магазина
     * @param enabled новое состояние уведомлений
     * @return {@code true}, если обновление прошло успешно
     */
    @Transactional
    public boolean setNotificationsEnabled(Long linkId, Long storeId, boolean enabled) {
        if (linkId == null || storeId == null) {
            return false;
        }

        return linkRepository.findById(linkId)
                .filter(link -> link.getStore() != null && link.getStore().getId().equals(storeId))
                .map(link -> {
                    link.setNotificationsEnabled(enabled);
                    linkRepository.save(link);
                    log.info("{} уведомления для покупателя {} в магазине {}", enabled ? "🔔 Включены" : "🔕 Отключены",
                            link.getCustomer().getId(), storeId);
                    return true;
                })
                .orElse(false);
    }

    private CustomerTelegramLinkDTO toDto(CustomerTelegramLink link) {
        CustomerTelegramLinkDTO dto = new CustomerTelegramLinkDTO();
        dto.setId(link.getId());
        dto.setPhone(link.getCustomer().getPhone());
        dto.setTelegramChatId(link.getTelegramChatId());
        dto.setTelegramConfirmed(link.isTelegramConfirmed());
        dto.setNotificationsEnabled(link.isNotificationsEnabled());
        return dto;
    }

}
