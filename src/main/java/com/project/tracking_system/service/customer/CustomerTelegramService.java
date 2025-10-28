package com.project.tracking_system.service.customer;

import com.project.tracking_system.dto.ActionRequiredReturnRequestDto;
import com.project.tracking_system.dto.ReturnRequestAvailableActionsDto;
import com.project.tracking_system.dto.ReturnRequestStateDto;
import com.project.tracking_system.dto.ReturnRequestTimestampsDto;
import com.project.tracking_system.dto.TelegramParcelInfoDTO;
import com.project.tracking_system.dto.TelegramParcelsOverviewDTO;
import com.project.tracking_system.dto.TelegramReturnRequestInfoDTO;
import com.project.tracking_system.dto.ReturnRequestUpdateResponse;
import com.project.tracking_system.entity.*;
import com.project.tracking_system.mapper.BuyerStatusMapper;
import com.project.tracking_system.repository.CustomerNotificationLogRepository;
import com.project.tracking_system.repository.CustomerRepository;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.repository.OrderReturnRequestRepository;
import com.project.tracking_system.service.order.OrderReturnRequestService;
import com.project.tracking_system.service.telegram.FullNameValidator;
import com.project.tracking_system.service.telegram.TelegramNotificationService;
import org.springframework.security.access.AccessDeniedException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.Objects;
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
    private final OrderReturnRequestRepository returnRequestRepository;
    private final OrderReturnRequestService orderReturnRequestService;

    /**
     * Формат для отображения дат заявок пользователю в Telegram.
     */
    private static final DateTimeFormatter RETURN_REQUEST_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    /**
     * Предопределённые наборы статусов для формирования сводки Telegram.
     * <p>
     * «Получено» отражает финальные доставки, «Ожидает забора» содержит все варианты ожидания клиента,
     * а «В пути» ограничен ключевыми этапами движения отправления до момента выдачи.
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
            GlobalStatus.IN_TRANSIT
    );

    /**
     * Статусы заявок, считающиеся активными для блокировки кнопок возврата.
     */
    private static final Set<OrderReturnRequestStatus> ACTIVE_RETURN_STATUSES = Set.of(
            OrderReturnRequestStatus.REGISTERED,
            OrderReturnRequestStatus.EXCHANGE_APPROVED
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
                    List<TrackParcel> deliveredParcels = loadParcelsForStatuses(customerId, DELIVERED_STATUSES);
                    List<TrackParcel> waitingParcels = loadParcelsForStatuses(customerId, WAITING_STATUSES);
                    List<TrackParcel> inTransitParcels = loadParcelsForStatuses(customerId, IN_TRANSIT_STATUSES);

                    Set<Long> parcelsWithActiveRequests = findParcelsWithActiveRequests(customerId, deliveredParcels);

                    List<TelegramParcelInfoDTO> delivered = mapParcelsForTelegram(deliveredParcels, parcelsWithActiveRequests);
                    List<TelegramParcelInfoDTO> waiting = mapParcelsForTelegram(waitingParcels, Set.of());
                    List<TelegramParcelInfoDTO> inTransit = mapParcelsForTelegram(inTransitParcels, Set.of());
                    return new TelegramParcelsOverviewDTO(delivered, waiting, inTransit);
                });
    }

    /**
     * Регистрирует заявку на возврат/обмен для покупателя, обратившегося через Telegram.
     * <p>
     * Метод проверяет, что чат принадлежит существующему покупателю и что выбранная
     * посылка закреплена за этим покупателем. После проверки делегируется логика
     * {@link OrderReturnRequestService}, которая валидирует идемпотентный ключ и данные заявки.
     * Комментарий и обратный трек заполняются позднее, поэтому при регистрации используются
     * пустые значения, а время обращения фиксируется автоматически в часовом поясе UTC.
     * </p>
     *
     * @param chatId          идентификатор Telegram-чата покупателя
     * @param parcelId        идентификатор посылки, для которой оформляется возврат
     * @param idempotencyKey  внешний идемпотентный ключ, предотвращающий дубли
     * @param reason          причина возврата, указанная пользователем
     * @return созданная или ранее зарегистрированная заявка
     */
    @Transactional
    public OrderReturnRequest registerReturnRequestFromTelegram(Long chatId,
                                                                Long parcelId,
                                                                String idempotencyKey,
                                                                String reason) {
        Customer customer = requireCustomerByChat(chatId);
        TrackParcel parcel = requireOwnedParcel(parcelId, customer.getId());
        validateIdempotencyKey(idempotencyKey);
        User owner = requireParcelOwner(parcel);
        ZonedDateTime requestedAt = ZonedDateTime.now(ZoneOffset.UTC);
        return orderReturnRequestService.registerReturn(
                parcelId,
                owner,
                idempotencyKey,
                reason,
                null,
                requestedAt,
                null,
                false
        );
    }

    /**
     * Запускает обмен по ранее зарегистрированной заявке покупателя.
     * <p>
     * Метод убеждается, что посылка принадлежит покупателю, а заявка с указанным идентификатором
     * относится к той же посылке. Правила перехода статусов и проверка повторных запусков
     * остаются на уровне {@link OrderReturnRequestService}.
     * </p>
     *
     * @param chatId   идентификатор Telegram-чата
     * @param parcelId идентификатор посылки
     * @param requestId идентификатор заявки, которую требуется одобрить
     * @return заявка после перевода в обмен
     */
    @Transactional
    public OrderReturnRequest approveExchangeFromTelegram(Long chatId,
                                                          Long parcelId,
                                                          Long requestId) {
        Customer customer = requireCustomerByChat(chatId);
        TrackParcel parcel = requireOwnedParcel(parcelId, customer.getId());
        User owner = requireParcelOwner(parcel);
        return orderReturnRequestService.approveExchange(requestId, parcelId, owner);
    }

    /**
     * Закрывает активную заявку без запуска обмена от имени покупателя.
     * <p>
     * Используется, когда клиент отказался от обмена после регистрации заявки.
     * Проверяется принадлежность посылки и доступность заявки в нужном статусе.
     * </p>
     *
     * @param chatId   идентификатор Telegram-чата
     * @param parcelId идентификатор посылки
     * @param requestId идентификатор заявки
     * @return заявка после закрытия
     */
    @Transactional
    public OrderReturnRequest closeReturnRequestFromTelegram(Long chatId,
                                                             Long parcelId,
                                                             Long requestId) {
        Customer customer = requireCustomerByChat(chatId);
        TrackParcel parcel = requireOwnedParcel(parcelId, customer.getId());
        User owner = requireParcelOwner(parcel);
        return orderReturnRequestService.closeWithoutExchange(requestId, parcelId, owner);
    }

    /**
     * Отменяет одобренный обмен по заявке покупателя.
     */
    @Transactional
    public OrderReturnRequest cancelExchangeFromTelegram(Long chatId,
                                                         Long parcelId,
                                                         Long requestId) {
        Customer customer = requireCustomerByChat(chatId);
        TrackParcel parcel = requireOwnedParcel(parcelId, customer.getId());
        User owner = requireParcelOwner(parcel);
        return orderReturnRequestService.cancelExchange(requestId, parcelId, owner);
    }

    /**
     * Переводит обменную заявку покупателя обратно в статус возврата.
     */
    @Transactional
    public OrderReturnRequest convertExchangeToReturnFromTelegram(Long chatId,
                                                                  Long parcelId,
                                                                  Long requestId) {
        Customer customer = requireCustomerByChat(chatId);
        TrackParcel parcel = requireOwnedParcel(parcelId, customer.getId());
        User owner = requireParcelOwner(parcel);
        return orderReturnRequestService.reopenAsReturn(requestId, parcelId, owner);
    }

    /**
     * Формирует запрос магазину на отмену обмена, когда обменная посылка уже отправлена.
     * <p>
     * Метод сохраняет обращение покупателя для обработки в веб-интерфейсе магазина
     * и не инициирует отмену автоматически.
     * </p>
     *
     * @param chatId   идентификатор чата Telegram
     * @param parcelId идентификатор посылки
     * @param requestId идентификатор обменной заявки
     * @return сохранённый запрос к магазину
     */
    @Transactional
    public OrderReturnRequestActionRequest requestExchangeCancellationFromTelegram(Long chatId,
                                                                                   Long parcelId,
                                                                                   Long requestId) {
        Customer customer = requireCustomerByChat(chatId);
        TrackParcel parcel = requireOwnedParcel(parcelId, customer.getId());
        User owner = requireParcelOwner(parcel);
        return orderReturnRequestService.requestMerchantAction(
                requestId,
                parcelId,
                owner,
                customer,
                ReturnRequestAction.CANCEL_EXCHANGE
        );
    }

    /**
     * Формирует запрос магазину на перевод обмена обратно в возврат после отправки посылки.
     *
     * @param chatId   идентификатор чата Telegram
     * @param parcelId идентификатор посылки
     * @param requestId идентификатор обменной заявки
     * @return сохранённый запрос к магазину
     */
    @Transactional
    public OrderReturnRequestActionRequest requestExchangeConversionFromTelegram(Long chatId,
                                                                                  Long parcelId,
                                                                                  Long requestId) {
        Customer customer = requireCustomerByChat(chatId);
        TrackParcel parcel = requireOwnedParcel(parcelId, customer.getId());
        User owner = requireParcelOwner(parcel);
        return orderReturnRequestService.requestMerchantAction(
                requestId,
                parcelId,
                owner,
                customer,
                ReturnRequestAction.CONVERT_TO_RETURN
        );
    }

    /**
     * Обновляет обратный трек и комментарий активной заявки от имени покупателя.
     * <p>
     * Метод проверяет, что чат принадлежит покупателю, выбранная посылка закреплена за ним,
     * а затем делегирует обновление сервису заявок, который выполняет бизнес-проверки.
     * </p>
     *
     * @param chatId       идентификатор Telegram-чата
     * @param parcelId     идентификатор посылки
     * @param requestId    идентификатор заявки
     * @param reverseTrack новое значение обратного трека или признак очистки
     * @param comment      новый комментарий или признак очистки
     * @return DTO с подтверждением сохранённых данных
     */
    @Transactional
    public ReturnRequestUpdateResponse updateReturnRequestDetailsFromTelegram(Long chatId,
                                                                              Long parcelId,
                                                                              Long requestId,
                                                                              String reverseTrack,
                                                                              String comment) {
        Customer customer = requireCustomerByChat(chatId);
        TrackParcel parcel = requireOwnedParcel(parcelId, customer.getId());
        User owner = requireParcelOwner(parcel);
        return orderReturnRequestService.updateReverseTrackAndComment(requestId, parcelId, owner, reverseTrack, comment);
    }

    /**
     * Возвращает активные заявки покупателя для отображения в Telegram.
     *
     * @param chatId идентификатор чата Telegram
     * @return список активных заявок с безопасными данными для бота
     */
    @Transactional(readOnly = true)
    public List<TelegramReturnRequestInfoDTO> getActiveReturnRequests(Long chatId) {
        Customer customer = requireCustomerByChat(chatId);
        Long customerId = customer.getId();
        if (customerId == null) {
            return List.of();
        }

        List<OrderReturnRequest> requests = returnRequestRepository
                .findActiveRequestsByCustomerWithDetails(customerId, ACTIVE_RETURN_STATUSES);
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }

        return requests.stream()
                .map(this::toTelegramReturnRequestInfo)
                .collect(Collectors.toList());
    }

    /**
     * Возвращает расширенный список активных заявок, требующих действий пользователя.
     *
     * @param chatId идентификатор Telegram-чата
     * @return заявки в удобном для отображения виде
     */
    @Transactional(readOnly = true)
    public List<ActionRequiredReturnRequestDto> getReturnRequestsRequiringAction(Long chatId) {
        Customer customer = requireCustomerByChat(chatId);
        Long customerId = customer.getId();
        if (customerId == null) {
            return List.of();
        }

        List<OrderReturnRequest> requests = returnRequestRepository
                .findActiveRequestsByCustomerWithDetails(customerId, ACTIVE_RETURN_STATUSES);
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }

        return requests.stream()
                .map(this::toActionRequiredDto)
                .collect(Collectors.toList());
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
    private List<TrackParcel> loadParcelsForStatuses(Long customerId, List<GlobalStatus> statuses) {
        return trackParcelRepository.findByCustomerIdAndStatusIn(customerId, statuses);
    }

    /**
     * Загружает покупателя по идентификатору Telegram-чата или выбрасывает исключение.
     */
    private Customer requireCustomerByChat(Long chatId) {
        if (chatId == null) {
            throw new IllegalArgumentException("Не указан идентификатор чата");
        }
        return customerRepository.findByTelegramChatId(chatId)
                .orElseThrow(() -> new IllegalArgumentException("Покупатель не найден для указанного чата"));
    }

    /**
     * Преобразует заявку в DTO для Telegram.
     *
     * @param request исходная заявка на возврат/обмен
     * @return DTO с подготовленными строками
     */
    private ActionRequiredReturnRequestDto toActionRequiredDto(OrderReturnRequest request) {
        if (request == null) {
            return null;
        }

        TrackParcel parcel = request.getParcel();
        Long parcelId = parcel != null ? parcel.getId() : null;
        String trackNumber = parcel != null ? parcel.getNumber() : null;
        String storeName = parcel != null && parcel.getStore() != null ? parcel.getStore().getName() : null;
        GlobalStatus parcelStatus = parcel != null ? parcel.getStatus() : null;
        boolean canStartExchange = orderReturnRequestService.canStartExchange(request);
        EnumSet<ReturnRequestAction> actions = orderReturnRequestService.resolveAvailableActions(request);
        boolean canCloseWithoutExchange = actions.contains(ReturnRequestAction.CANCEL_RETURN);
        boolean canReopenAsReturn = actions.contains(ReturnRequestAction.CONVERT_TO_RETURN);
        boolean canCancelExchange = actions.contains(ReturnRequestAction.CANCEL_EXCHANGE);
        String cancelExchangeReason = orderReturnRequestService
                .getExchangeCancellationBlockReason(request)
                .orElse(null);
        boolean exchangeShipmentDispatched = orderReturnRequestService.isExchangeShipmentDispatched(request);
        boolean canConfirmReceipt = orderReturnRequestService.canConfirmReceipt(request);

        ReturnRequestMode mode = request.getMode() != null ? request.getMode() : ReturnRequestMode.RETURN;
        ReturnRequestStage stage = request.getStage() != null ? request.getStage() : ReturnRequestStage.CUSTOMER_RETURN;

        ReturnRequestStateDto state = new ReturnRequestStateDto(
                mode,
                stage,
                request.isManualStageOverride(),
                request.isManualTrackOverride(),
                request.isExchangeRequested(),
                request.isExchangeApproved(),
                exchangeShipmentDispatched,
                request.getExchangeTrackNumber(),
                request.isReturnReceiptConfirmed()
        );

        ReturnRequestAvailableActionsDto availableActions = new ReturnRequestAvailableActionsDto(
                canStartExchange,
                orderReturnRequestService.canCreateExchangeParcel(request),
                canCloseWithoutExchange,
                canReopenAsReturn,
                canCancelExchange,
                canConfirmReceipt,
                cancelExchangeReason
        );

        String requestedAt = formatRequestMoment(request.getRequestedAt());
        if (requestedAt == null) {
            requestedAt = formatRequestMoment(request.getCreatedAt());
        }

        ReturnRequestTimestampsDto timestamps = new ReturnRequestTimestampsDto(
                requestedAt,
                formatRequestMoment(request.getCreatedAt()),
                formatRequestMoment(request.getDecisionAt()),
                formatRequestMoment(request.getClosedAt()),
                formatRequestMoment(request.getStageStartedAt()),
                formatRequestMoment(request.getStageUpdatedAt()),
                formatRequestMoment(request.getExchangeTrackAssignedAt()),
                formatRequestMoment(request.getReturnReceiptConfirmedAt())
        );

        return new ActionRequiredReturnRequestDto(
                request.getId(),
                parcelId,
                trackNumber,
                storeName,
                parcelStatus != null ? parcelStatus.getDescription() : null,
                status,
                status != null ? status.getDisplayName() : null,
                request.getReason(),
                request.getComment(),
                request.getReverseTrackNumber(),
                state,
                availableActions,
                timestamps
        );
    }

    /**
     * Форматирует момент времени заявки к отображению в Telegram.
     *
     * @param moment исходное значение в UTC
     * @return строка или {@code null}, если момент отсутствует
     */
    private String formatRequestMoment(ZonedDateTime moment) {
        if (moment == null) {
            return null;
        }
        return RETURN_REQUEST_DATE_FORMAT.format(moment.withZoneSameInstant(ZoneOffset.UTC));
    }

    /**
     * Убеждается, что посылка принадлежит покупателю, оформившему заявку.
     */
    private TrackParcel requireOwnedParcel(Long parcelId, Long customerId) {
        if (parcelId == null) {
            throw new IllegalArgumentException("Не указан идентификатор посылки");
        }
        TrackParcel parcel = trackParcelRepository.findById(parcelId)
                .orElseThrow(() -> new IllegalArgumentException("Посылка не найдена"));
        Long parcelCustomerId = Optional.ofNullable(parcel.getCustomer())
                .map(Customer::getId)
                .orElse(null);
        if (parcelCustomerId == null || !parcelCustomerId.equals(customerId)) {
            throw new AccessDeniedException("Посылка принадлежит другому покупателю");
        }
        return parcel;
    }

    /**
     * Возвращает владельца посылки (пользователя магазина) для делегирования сервису заявок.
     */
    private User requireParcelOwner(TrackParcel parcel) {
        User owner = Optional.ofNullable(parcel)
                .map(TrackParcel::getUser)
                .orElseThrow(() -> new IllegalStateException("У посылки не найден владелец"));
        if (owner.getId() == null) {
            throw new IllegalStateException("У владельца посылки не заполнен идентификатор");
        }
        return owner;
    }

    /**
     * Проверяет корректность идемпотентного ключа заявки.
     */
    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Не указан идемпотентный ключ заявки");
        }
    }

    /**
     * Возвращает набор идентификаторов посылок, по которым есть активные заявки на возврат.
     *
     * @param customerId       идентификатор покупателя
     * @param deliveredParcels список доставленных посылок
     * @return множество идентификаторов посылок с активными заявками
     */
    private Set<Long> findParcelsWithActiveRequests(Long customerId, List<TrackParcel> deliveredParcels) {
        if (customerId == null) {
            return Set.of();
        }

        List<Long> activeIds = returnRequestRepository
                .findParcelIdsByCustomerAndStatusIn(customerId, ACTIVE_RETURN_STATUSES);
        if (activeIds == null || activeIds.isEmpty()) {
            return Set.of();
        }

        if (deliveredParcels == null || deliveredParcels.isEmpty()) {
            return new HashSet<>(activeIds);
        }

        Set<Long> deliveredIds = deliveredParcels.stream()
                .map(TrackParcel::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return activeIds.stream()
                .filter(deliveredIds::contains)
                .collect(Collectors.toCollection(HashSet::new));
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
    private List<TelegramParcelInfoDTO> mapParcelsForTelegram(List<TrackParcel> parcels,
                                                             Set<Long> parcelsWithActiveRequests) {
        if (parcels == null || parcels.isEmpty()) {
            return List.of();
        }

        return parcels.stream()
                .sorted(Comparator.comparing(
                        TrackParcel::getLastUpdate,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ).reversed())
                .map(parcel -> toTelegramParcelInfo(parcel,
                        parcelsWithActiveRequests != null
                                && parcelsWithActiveRequests.contains(parcel.getId())))
                .collect(Collectors.toList());
    }

    /**
     * Создаёт DTO для Telegram из сущности посылки, заполняя читаемые значения по умолчанию.
     *
     * @param parcel исходная сущность посылки из базы данных
     * @return DTO с безопасными для отображения полями
     */
    private TelegramParcelInfoDTO toTelegramParcelInfo(TrackParcel parcel, boolean hasActiveRequest) {
        if (parcel == null) {
            return new TelegramParcelInfoDTO(null, "Без номера", "Магазин не указан", null, false);
        }

        String trackNumber = parcel.getNumber();
        if (trackNumber == null || trackNumber.isBlank()) {
            trackNumber = "Без номера";
        }

        String storeName = (parcel.getStore() != null && parcel.getStore().getName() != null)
                ? parcel.getStore().getName()
                : "Магазин не указан";

        GlobalStatus status = parcel.getStatus();

        return new TelegramParcelInfoDTO(parcel.getId(), trackNumber, storeName, status, hasActiveRequest);
    }

    /**
     * Преобразует заявку на возврат в DTO для отображения в Telegram.
     */
    private TelegramReturnRequestInfoDTO toTelegramReturnRequestInfo(OrderReturnRequest request) {
        if (request == null) {
            return new TelegramReturnRequestInfoDTO(null, null, "Без номера", "Магазин не указан",
                    OrderReturnRequestStatus.REGISTERED, null);
        }

        TrackParcel parcel = request.getParcel();
        Long parcelId = Optional.ofNullable(parcel)
                .map(TrackParcel::getId)
                .orElse(null);
        String trackNumber = Optional.ofNullable(parcel)
                .map(TrackParcel::getNumber)
                .filter(number -> !number.isBlank())
                .orElse("Без номера");
        String storeName = Optional.ofNullable(parcel)
                .map(TrackParcel::getStore)
                .map(store -> store.getName() != null ? store.getName() : null)
                .filter(name -> !name.isBlank())
                .orElse("Магазин не указан");

        OrderReturnRequestStatus status = Optional.ofNullable(request.getStatus())
                .orElse(OrderReturnRequestStatus.REGISTERED);

        return new TelegramReturnRequestInfoDTO(
                request.getId(),
                parcelId,
                trackNumber,
                storeName,
                status,
                request.getRequestedAt()
        );
    }

}
