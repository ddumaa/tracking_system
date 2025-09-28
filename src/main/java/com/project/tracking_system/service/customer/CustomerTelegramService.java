package com.project.tracking_system.service.customer;

import com.project.tracking_system.dto.TelegramParcelInfoDTO;
import com.project.tracking_system.dto.TelegramParcelsOverviewDTO;
import com.project.tracking_system.entity.*;
import com.project.tracking_system.mapper.BuyerStatusMapper;
import com.project.tracking_system.repository.CustomerNotificationLogRepository;
import com.project.tracking_system.repository.CustomerRepository;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.service.telegram.FullNameValidator;
import com.project.tracking_system.service.telegram.TelegramNotificationService;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.project.tracking_system.dto.CustomerStatisticsDTO;

import com.project.tracking_system.utils.PhoneUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
    private final FullNameValidator fullNameValidator;

    /**
     * Предопределённые наборы статусов для формирования сводки Telegram.
     * <p>
     * «Получено» отражает финальные доставки, «Ожидает забора» содержит все варианты ожидания клиента,
     * включая повторные напоминания, а «В пути» собирает предшествующие доставке статусы и контролирует
     * проблемные ситуации до момента получения.
     * </p>
     */
    private static final List<GlobalStatus> DELIVERED_STATUSES = List.of(GlobalStatus.DELIVERED);
    private static final List<GlobalStatus> WAITING_STATUSES = List.of(
            GlobalStatus.WAITING_FOR_CUSTOMER,
            GlobalStatus.CUSTOMER_NOT_PICKING_UP
    );
    private static final List<GlobalStatus> IN_TRANSIT_STATUSES = List.of(
            GlobalStatus.PRE_REGISTERED,
            GlobalStatus.REGISTERED,
            GlobalStatus.IN_TRANSIT,
            GlobalStatus.WAITING_FOR_CUSTOMER,
            GlobalStatus.CUSTOMER_NOT_PICKING_UP
    );

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
        String maskedPhone = PhoneUtils.maskPhone(normalized);
        log.info("🔗 Попытка привязки телефона {} к чату {}",
                maskedPhone, chatId);

        // Регистрируем покупателя при необходимости
        Customer customer;
        try {
            customer = customerService.registerOrGetByPhone(normalized);
        } catch (ResponseStatusException ex) {
            // При некорректном телефоне информируем пользователя кодом 400
            log.warn("Телефон {} не прошёл проверку: {}",
                    maskedPhone, ex.getReason());
            throw ex;
        }

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
     * Подтвердить имя покупателя по идентификатору чата.
     * <p>Имя становится подтверждённым пользователем.</p>
     *
     * @param chatId идентификатор чата Telegram
     * @return {@code true}, если имя подтверждено
     */
    @Transactional
    public boolean confirmName(Long chatId) {
        return customerRepository.findByTelegramChatId(chatId)
                .map(c -> {
                    String fullName = c.getFullName();
                    if (fullName == null || fullName.isBlank()) {
                        return false;
                    }
                    FullNameValidator.FullNameValidationResult validation = fullNameValidator.validate(fullName);
                    if (!validation.valid()) {
                        if (c.getNameSource() == NameSource.USER_CONFIRMED) {
                            c.setNameSource(NameSource.MERCHANT_PROVIDED);
                            c.setNameUpdatedAt(ZonedDateTime.now(ZoneOffset.UTC));
                            customerRepository.save(c);
                        }
                        return false;
                    }
                    if (c.getNameSource() != NameSource.USER_CONFIRMED) {
                        c.setNameSource(NameSource.USER_CONFIRMED);
                        c.setNameUpdatedAt(ZonedDateTime.now(ZoneOffset.UTC));
                        customerRepository.save(c);
                    }
                    return true;
                })
                .orElse(false);
    }

    /**
     * Обновить ФИО покупателя, введённое в Telegram.
     *
     * @param chatId   идентификатор чата Telegram
     * @param fullName новое ФИО
     * @return {@code true}, если имя успешно сохранено
     */
    @Transactional
    public boolean updateNameFromTelegram(Long chatId, String fullName) {
        return customerRepository.findByTelegramChatId(chatId)
                .map(c -> customerService.updateCustomerName(c, fullName, NameSource.USER_CONFIRMED))
                .orElse(false);
    }

    /**
     * Пометить имя как неподтверждённое магазином.
     *
     * @param chatId идентификатор чата Telegram
     */
    @Transactional
    public void markNameUnconfirmed(Long chatId) {
        customerRepository.findByTelegramChatId(chatId)
                .ifPresent(c -> {
                    c.setNameSource(NameSource.MERCHANT_PROVIDED);
                    c.setNameUpdatedAt(ZonedDateTime.now(ZoneOffset.UTC));
                    customerRepository.save(c);
                });
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

            boolean sent = telegramNotificationService.sendStatusUpdate(parcel, status);
            if (!sent) {
                log.debug("Уведомление для посылки {} со статусом {} не отправлено, запись в журнал пропущена",
                        parcel.getNumber(), status);
                continue;
            }

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

    /**
     * Формирует сводку посылок покупателя для отображения в Telegram.
     * <p>
     * Метод группирует посылки по ключевым статусам, чтобы бот мог показать
     * их в разделе «Мои посылки». Если покупатель не найден, возвращается
     * {@link Optional#empty()}.
     * </p>
     *
     * @param chatId идентификатор чата Telegram
     * @return опциональная сводка посылок по категориям
     */
    @Transactional(readOnly = true)
    public Optional<TelegramParcelsOverviewDTO> getParcelsOverview(Long chatId) {
        if (chatId == null) {
            return Optional.empty();
        }

        return customerRepository.findByTelegramChatId(chatId)
                .map(customer -> {
                    Long customerId = customer.getId();
                    List<TelegramParcelInfoDTO> delivered = loadParcelsForStatuses(customerId, DELIVERED_STATUSES);
                    List<TelegramParcelInfoDTO> waiting = loadParcelsForStatuses(customerId, WAITING_STATUSES);
                    List<TelegramParcelInfoDTO> inTransit = loadParcelsForStatuses(customerId, IN_TRANSIT_STATUSES);
                    return new TelegramParcelsOverviewDTO(delivered, waiting, inTransit);
                });
    }

    /**
     * Загружает посылки покупателя по указанным статусам и подготавливает их для Telegram.
     * <p>
     * Метод инкапсулирует запрос в репозиторий, обеспечивая единый способ построения
     * выборки и преобразования в DTO. Это упрощает поддержку и расширение набора
     * категорий, не нарушая принцип единственной ответственности.
     * </p>
     *
     * @param customerId идентификатор покупателя
     * @param statuses   целевые статусы посылок
     * @return отсортированный список DTO для отображения в боте
     */
    private List<TelegramParcelInfoDTO> loadParcelsForStatuses(Long customerId, List<GlobalStatus> statuses) {
        List<TrackParcel> parcels = trackParcelRepository.findByCustomerIdAndStatusIn(customerId, statuses);
        return mapParcelsForTelegram(parcels);
    }

    /**
     * Преобразует список сущностей посылок в DTO для Telegram.
     * <p>
     * Посылки сортируются по дате последнего обновления в обратном порядке,
     * чтобы свежие статусы отображались первыми. Для отсутствующих номеров и
     * названий магазинов используются читаемые заглушки.
     * </p>
     *
     * @param parcels исходные сущности посылок
     * @return список DTO с информацией для отображения
     */
    private List<TelegramParcelInfoDTO> mapParcelsForTelegram(List<TrackParcel> parcels) {
        if (parcels == null || parcels.isEmpty()) {
            return List.of();
        }

        return parcels.stream()
                .sorted(Comparator.comparing(
                        TrackParcel::getLastUpdate,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ).reversed())
                .map(this::toTelegramParcelInfo)
                .collect(Collectors.toList());
    }

    /**
     * Создаёт DTO для Telegram из сущности посылки, заполняя читаемые значения по умолчанию.
     *
     * @param parcel исходная сущность посылки из базы данных
     * @return DTO с безопасными для отображения полями
     */
    private TelegramParcelInfoDTO toTelegramParcelInfo(TrackParcel parcel) {
        if (parcel == null) {
            return new TelegramParcelInfoDTO("Без номера", "Магазин не указан");
        }

        String trackNumber = parcel.getNumber();
        if (trackNumber == null || trackNumber.isBlank()) {
            trackNumber = "Без номера";
        }

        String storeName = (parcel.getStore() != null && parcel.getStore().getName() != null)
                ? parcel.getStore().getName()
                : "Магазин не указан";

        return new TelegramParcelInfoDTO(trackNumber, storeName);
    }

}
