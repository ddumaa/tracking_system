package com.project.tracking_system.service.customer;

import com.project.tracking_system.entity.*;
import com.project.tracking_system.dto.CustomerInfoDTO;
import com.project.tracking_system.exception.ConfirmedNameChangeException;
import com.project.tracking_system.repository.CustomerRepository;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.service.user.UserSettingsService;
import com.project.tracking_system.service.customer.CustomerNameEventService;
import com.project.tracking_system.model.subscription.FeatureKey;
import com.project.tracking_system.utils.NameUtils;
import com.project.tracking_system.utils.PhoneUtils;
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.concurrent.TimeUnit;

import java.util.Optional;

/**
 * Сервис управления покупателями.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final TrackParcelRepository trackParcelRepository;
    private final CustomerTransactionalService transactionalService;
    private final CustomerStatsService customerStatsService;
    private final SubscriptionService subscriptionService;
    private final UserSettingsService userSettingsService;
    private final CustomerNameEventService customerNameEventService;
    /** Клиент Telegram для отправки уведомлений. */
    private final TelegramClient telegramClient;

    /** Фича-флаг для вывода маскированных ФИО в DEBUG. */
    @Value("${debug.log-masked-fio:false}")
    private boolean debugLogMaskedFio;

    /** Менеджер сущностей для управления состоянием JPA-объектов. */
    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Зарегистрировать нового покупателя или получить существующего по телефону.
     * <p>
     * Все операции поиска и сохранения выполняются в отдельных транзакциях,
     * что исключает ошибку "current transaction is aborted" при конкурентной записи.
     * При возникновении гонки сохранения выполняется несколько повторных чтений
     * записи с небольшими задержками.
     * </p>
     *
     * @param rawPhone телефон в произвольном формате
     * @return сущность покупателя
     */
    public Customer registerOrGetByPhone(String rawPhone) {
        // Нормализуем телефон и обрабатываем ошибку формата,
        // чтобы вернуть клиенту понятный ответ с кодом 400
        String phone;
        try {
            phone = PhoneUtils.normalizePhone(rawPhone);
        } catch (IllegalArgumentException ex) {
            log.warn("Некорректный формат телефона {}: {}",
                    PhoneUtils.maskPhone(rawPhone), ex.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Некорректный номер телефона");
        }
        log.info("🔍 Начало поиска/регистрации покупателя по телефону {}",
                PhoneUtils.maskPhone(phone));
        // Первый поиск выполняем отдельно, чтобы не создавать дубликаты
        Optional<Customer> existing = transactionalService.findByPhone(phone);
        if (existing.isPresent()) {
            return existing.get();
        }

        Customer customer = new Customer();
        customer.setPhone(phone);
        try {
            Customer saved = transactionalService.saveCustomer(customer);
            log.info("Создан новый покупатель с номером {}",
                    PhoneUtils.maskPhone(phone));
            return saved;
        } catch (DataIntegrityViolationException e) {
            log.warn("Покупатель с номером {} уже существует, выполняем повторный поиск",
                    PhoneUtils.maskPhone(phone));
            // Несколько раз пытаемся прочитать покупателя, ожидая завершения транзакции сохранения
            for (int attempt = 0; attempt < 3; attempt++) {
                Optional<Customer> byPhone = transactionalService.findByPhone(phone);
                if (byPhone.isPresent()) {
                    return byPhone.get();
                }
                try {
                    TimeUnit.MILLISECONDS.sleep(50);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            throw new IllegalStateException("Покупатель не найден после ошибки сохранения");
        }

    }

    /**
     * Обновляет ФИО покупателя и фиксирует событие смены имени.
     * <p>
     * Если имя подтверждено пользователем, попытки обновления от магазина
     * игнорируются. При успешном изменении сохраняется событие, а предыдущие
     * помечаются как {@code SUPERSEDED}.
     * </p>
     *
     * @param customer изменяемый покупатель
     * @param newName  новое ФИО
     * @param source   источник данных имени
     * @return {@code true}, если обновление было выполнено
     */
    @Transactional
    public boolean updateCustomerName(Customer customer, String newName, NameSource source) {
        return updateCustomerName(customer, newName, source, null);
    }

    /**
     * Обновляет ФИО покупателя с учётом роли инициатора операции.
     *
     * @param customer  изменяемый покупатель
     * @param newName   новое ФИО
     * @param source    источник данных имени
     * @param actorRole роль пользователя, выполняющего изменение
     * @return {@code true}, если обновление выполнено
     */
    @Transactional
    public boolean updateCustomerName(Customer customer, String newName, NameSource source, Role actorRole) {
        if (customer == null || source == null || newName == null || newName.isBlank()) {
            return false;
        }
        // Запрещаем магазинам менять подтверждённое имя
        if (customer.getNameSource() == NameSource.USER_CONFIRMED
                && source == NameSource.MERCHANT_PROVIDED) {
            if (actorRole != Role.ROLE_ADMIN) {
                log.warn("🚫 Попытка магазина изменить подтверждённое имя клиента ID={}", customer.getId());
                throw new ConfirmedNameChangeException("Имя подтверждено пользователем");
            } else {
                log.info("⚠️ Администратор изменяет подтверждённое имя клиента ID={}", customer.getId());
                if (debugLogMaskedFio && log.isDebugEnabled()) {
                    log.debug("⚠️ Администратор изменяет подтверждённое имя клиента ID={} на '{}'",
                            customer.getId(), NameUtils.maskName(newName));
                }
                notifyCustomer(customer, newName);
            }
        }
        if (newName.equals(customer.getFullName())) {
            return false;
        }
        String oldName = customer.getFullName();
        customer.setFullName(newName);
        customer.setNameSource(source);
        customer.setNameUpdatedAt(ZonedDateTime.now(ZoneOffset.UTC));
        customerRepository.save(customer);
        customerNameEventService.recordEvent(customer, oldName, newName);
        return true;
    }

    /**
     * Отправить уведомление покупателю об изменении имени администратором.
     *
     * @param customer покупатель
     * @param newName  новое ФИО
     */
    private void notifyCustomer(Customer customer, String newName) {
        Long chatId = customer.getTelegramChatId();
        if (chatId == null) {
            return;
        }
        String text = "⚠️ Администратор изменил ваше имя на '" + newName + "'.";
        try {
            telegramClient.execute(new SendMessage(chatId.toString(), text));
        } catch (TelegramApiException e) {
            log.error("Ошибка отправки уведомления клиенту {}: {}", chatId, e.getMessage(), e);
        }
    }

    /**
     * Увеличить счётчик отправленных посылок для покупателя.
     *
     * @param track посылка, связанная с покупателем
     */
    @Transactional
    public void updateStatsOnTrackAdd(TrackParcel track) {
        if (track == null || track.getCustomer() == null) {
            return;
        }
        log.debug("📈 [updateStatsOnTrackAdd] Покупатель ID={} посылка ID={}",
                track.getCustomer().getId(), track.getId());
        // Пересчитываем статистику и получаем актуальную сущность покупателя
        Customer customer = customerStatsService.incrementSent(track.getCustomer());
        // Отсоединяем сущность, чтобы избежать повторного flush и конфликтов версий
        entityManager.detach(customer);
    }

    /**
     * Увеличить счётчик забранных посылок при доставке.
     *
     * @param track посылка, связанная с покупателем
     */
    @Transactional
    public void updateStatsOnTrackDelivered(TrackParcel track) {
        if (track == null || track.getCustomer() == null) {
            return;
        }
        log.debug("📦 [updateStatsOnTrackDelivered] Покупатель ID={} посылка ID={}",
                track.getCustomer().getId(), track.getId());
        // Обновляем статистику получения и получаем актуальный объект покупателя
        Customer customer = customerStatsService.incrementPickedUp(track.getCustomer());
        // Отсоединяем покупателя, чтобы исключить повторное обновление при фиксации транзакции
        entityManager.detach(customer);
    }

    /**
     * Откатить статистику при удалении посылки.
     *
     * @param track удаляемая посылка
     */
    @Transactional
    public void rollbackStatsOnTrackDelete(TrackParcel track) {
        if (track == null || track.getCustomer() == null) {
            return;
        }

        Customer customer = track.getCustomer();
        int beforeSent = customer.getSentCount();
        int beforePicked = customer.getPickedUpCount();
        int beforeReturned = customer.getReturnedCount();

        if (customer.getSentCount() > 0) {
            customer.setSentCount(customer.getSentCount() - 1);
        }

        if (track.getStatus() == GlobalStatus.DELIVERED && customer.getPickedUpCount() > 0) {
            customer.setPickedUpCount(customer.getPickedUpCount() - 1);
        } else if (track.getStatus() == GlobalStatus.RETURNED && customer.getReturnedCount() > 0) {
            customer.setReturnedCount(customer.getReturnedCount() - 1);
        }

        customer.recalculateReputation();
        customerRepository.save(customer);

        log.debug(
                "↩️ [rollbackStatsOnTrackDelete] ID={} sent: {} -> {}, picked: {} -> {}, returned: {} -> {}",
                customer.getId(),
                beforeSent,
                customer.getSentCount(),
                beforePicked,
                customer.getPickedUpCount(),
                beforeReturned,
                customer.getReturnedCount()
        );
    }

    /**
     * Получить информацию о покупателе по идентификатору посылки.
     *
     * @param parcelId идентификатор посылки
     * @return DTO с информацией о покупателе или {@code null}, если покупатель не найден
     */
    @Transactional(readOnly = true)
    public CustomerInfoDTO getCustomerInfoByParcelId(Long parcelId) {
        return trackParcelRepository.findById(parcelId)
                .map(track -> {
                    log.debug("🔍 Найден покупатель ID={} для посылки ID={}",
                            track.getCustomer() != null ? track.getCustomer().getId() : null,
                            parcelId);
                    return track.getCustomer();
                })
                // Источник имени возвращаем, чтобы клиент мог блокировать редактирование подтверждённого имени
                .map(this::toInfoDto)
                .orElseGet(() -> {
                    log.debug("ℹ️ Покупатель для посылки ID={} не найден", parcelId);
                    return null;
                });
    }

    /**
     * Найти покупателя по номеру телефона.
     * <p>
     * Номер нормализуется до формата {@code 375XXXXXXXXX}. При пустом значении
     * возвращается {@link Optional#empty()}. Возможны исключения
     * {@link IllegalArgumentException}, если номер не удаётся нормализовать.
     * </p>
     *
     * @param rawPhone телефон в произвольном формате
     * @return Optional с покупателем или {@link Optional#empty()}, если клиент не найден
     * @throws IllegalArgumentException при неверном формате номера
     */
    @Transactional(readOnly = true)
    public Optional<Customer> findByPhone(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            return Optional.empty();
        }
        String phone = PhoneUtils.normalizePhone(rawPhone);
        return customerRepository.findByPhone(phone);
    }

    /**
     * Получить данные покупателя по номеру телефона.
     * <p>
     * Делегирует поиск методу {@link #findByPhone(String)} и не создаёт новых записей.
     * </p>
     *
     * @param rawPhone телефон покупателя в произвольном формате
     * @return Optional с информацией о покупателе или {@link Optional#empty()}, если клиент не найден
     * @throws IllegalArgumentException при некорректном формате номера
     */
    @Transactional(readOnly = true)
    public Optional<CustomerInfoDTO> getCustomerInfoByPhone(String rawPhone) {
        return findByPhone(rawPhone).map(this::toInfoDto);
    }

    /**
     * Привязать покупателя к посылке по телефону.
     *
     * @param parcelId идентификатор посылки
     * @param rawPhone телефон покупателя
     * @return обновлённая информация о покупателе
     */
    @Transactional
    public CustomerInfoDTO assignCustomerToParcel(Long parcelId, String rawPhone) {
        log.debug("🔍 Поиск посылки ID={} для привязки покупателя", parcelId);
        TrackParcel parcel = trackParcelRepository.findById(parcelId)
                .orElseThrow(() -> new IllegalArgumentException("Посылка не найдена"));
        log.debug("📞 Привязываем телефон {} к посылке ID={}",
                PhoneUtils.maskPhone(rawPhone), parcelId);
        Customer newCustomer;
        try {
            newCustomer = registerOrGetByPhone(rawPhone);
        } catch (ResponseStatusException ex) {
            // Логируем проблему и пробрасываем исключение для корректного ответа
            log.warn("Ошибка привязки телефона {}: {}",
                    PhoneUtils.maskPhone(rawPhone), ex.getReason());
            throw ex;
        }

        Customer current = parcel.getCustomer();
        // Если посылка уже привязана к этому же покупателю, ничего не меняем
        if (current != null && current.getId().equals(newCustomer.getId())) {
            log.debug("ℹ️ Посылка ID={} уже связана с покупателем ID={}", parcelId, newCustomer.getId());
            return toInfoDto(current);
        }

        // Если посылка была связана с другим покупателем, корректируем статистику старого
        if (current != null) {
            log.debug("🔄 Посылка ID={} была связана с другим покупателем ID={}. Корректируем статистику", parcelId, current.getId());
            rollbackStatsOnTrackDelete(parcel);
        }

        // Привязываем нового покупателя и сохраняем изменения
        parcel.setCustomer(newCustomer);
        trackParcelRepository.save(parcel);

        log.debug("📦 Посылка ID={} привязана к покупателю ID={}", parcelId, newCustomer.getId());

        // Обновляем статистику покупателя в зависимости от статуса посылки
        customerStatsService.incrementSent(newCustomer);
        if (parcel.getStatus() == GlobalStatus.DELIVERED) {
            customerStatsService.incrementPickedUp(newCustomer);
        } else if (parcel.getStatus() == GlobalStatus.RETURNED) {
            customerStatsService.incrementReturned(newCustomer);
        }

        log.debug("📈 Статистика покупателя ID={} обновлена после привязки посылки ID={}",
                newCustomer.getId(), parcelId);
        // Возвращаем имя и его источник, чтобы при подтверждённом имени запретить дальнейшее редактирование
        return toInfoDto(newCustomer);
    }

    /**
     * Проверяет, можно ли отправлять уведомления покупателю.
     * <p>
     * Уведомления разрешены, если у покупателя указан идентификатор Telegram-чатa,
     * включены уведомления и владелец магазина имеет тариф, допускающий отправку
     * Telegram-уведомлений.
     * </p>
     *
     * @param customer покупатель
     * @param store    магазин
     * @return {@code true}, если уведомления разрешены
     */
    @Transactional(readOnly = true)
    public boolean isNotifiable(Customer customer, Store store) {
        if (customer == null || store == null) {
            return false;
        }

        // Проверяем наличие привязанного чата и разрешение на уведомления
        if (customer.getTelegramChatId() == null || !customer.isNotificationsEnabled()) {
            return false;
        }

        // Проверяем возможность отправки уведомлений согласно подписке владельца
        Long ownerId = Optional.ofNullable(store.getOwner())
                .map(User::getId)
                .orElse(null);

        if (ownerId == null || !subscriptionService.isFeatureEnabled(ownerId, FeatureKey.TELEGRAM_NOTIFICATIONS)) {
            return false;
        }

        return userSettingsService.isTelegramNotificationsEnabled(ownerId);
    }

    private CustomerInfoDTO toInfoDto(Customer customer) {
        if (customer == null) {
            return null;
        }
        double percentage = customer.getSentCount() > 0
                ? (double) customer.getPickedUpCount() / customer.getSentCount() * 100
                : 0.0;
        return new CustomerInfoDTO(
                customer.getPhone(),
                customer.getFullName(),
                customer.getNameSource(),
                customer.getSentCount(),
                customer.getPickedUpCount(),
                customer.getReturnedCount(),
                Math.round(percentage * 100.0) / 100.0,
                customer.getReputation()
        );
    }
}
