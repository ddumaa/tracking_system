package com.project.tracking_system.service.customer;

import com.project.tracking_system.entity.*;
import com.project.tracking_system.dto.CustomerInfoDTO;
import com.project.tracking_system.repository.CustomerRepository;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.model.subscription.FeatureKey;
import com.project.tracking_system.utils.PhoneUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        String phone = PhoneUtils.normalizePhone(rawPhone);
        log.info("🔍 Начало поиска/регистрации покупателя по телефону {}", phone);
        // Первый поиск выполняем отдельно, чтобы не создавать дубликаты
        Optional<Customer> existing = transactionalService.findByPhone(phone);
        if (existing.isPresent()) {
            return existing.get();
        }

        Customer customer = new Customer();
        customer.setPhone(phone);
        try {
            Customer saved = transactionalService.saveCustomer(customer);
            log.info("Создан новый покупатель с номером {}", phone);
            return saved;
        } catch (DataIntegrityViolationException e) {
            log.warn("Покупатель с номером {} уже существует, выполняем повторный поиск", phone);
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
        customerStatsService.incrementSent(track.getCustomer());
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
        customerStatsService.incrementPickedUp(track.getCustomer());
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
        if (customer.getSentCount() > 0) {
            customer.setSentCount(customer.getSentCount() - 1);
        }
        if (track.getStatus() != null && track.getStatus().isFinal() && customer.getPickedUpCount() > 0) {
            customer.setPickedUpCount(customer.getPickedUpCount() - 1);
        }
        customer.recalculateReputation();
        customerRepository.save(customer);
        log.debug("↩️ [rollbackStatsOnTrackDelete] ID={} sent: {} -> {}, picked: {} -> {}",
                customer.getId(), beforeSent, customer.getSentCount(), beforePicked, customer.getPickedUpCount());
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
                .map(this::toInfoDto)
                .orElseGet(() -> {
                    log.debug("ℹ️ Покупатель для посылки ID={} не найден", parcelId);
                    return null;
                });
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
        log.debug("📞 Привязываем телефон {} к посылке ID={}", rawPhone, parcelId);
        Customer newCustomer = registerOrGetByPhone(rawPhone);

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

        // Привязываем нового покупателя
        parcel.setCustomer(newCustomer);
        trackParcelRepository.save(parcel);

        log.debug("📦 Посылка ID={} привязана к покупателю ID={}", parcelId, newCustomer.getId());

        // Статистику увеличиваем только при фактическом добавлении нового покупателя
        customerStatsService.incrementSent(newCustomer);
        log.debug("📈 Статистика покупателя ID={} обновлена после привязки посылки ID={}", newCustomer.getId(), parcelId);
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

        return ownerId != null && subscriptionService.isFeatureEnabled(ownerId, FeatureKey.TELEGRAM_NOTIFICATIONS);
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
                customer.getSentCount(),
                customer.getPickedUpCount(),
                customer.getReturnedCount(),
                Math.round(percentage * 100.0) / 100.0,
                customer.getReputation()
        );
    }
}
