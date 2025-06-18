package com.project.tracking_system.service.admin;

import com.project.tracking_system.dto.StoreAdminInfoDTO;
import com.project.tracking_system.dto.TrackParcelAdminInfoDTO;
import com.project.tracking_system.entity.*;
import com.project.tracking_system.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Сервис сбора административной статистики.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final CustomerRepository customerRepository;
    private final CustomerNotificationLogRepository notificationRepository;
    private final StoreRepository storeRepository;
    private final StoreTelegramSettingsRepository storeTelegramSettingsRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final TrackParcelRepository trackParcelRepository;

    /**
     * Подсчитать общее количество покупателей.
     */
    public long countCustomers() {
        return customerRepository.count();
    }

    /**
     * Подсчитать количество ненадёжных покупателей.
     */
    public long countUnreliableCustomers() {
        return customerRepository.countByReputation(BuyerReputation.UNRELIABLE);
    }

    /**
     * Получить список ненадёжных покупателей.
     */
    public List<Customer> getUnreliableCustomers() {
        return customerRepository.findByReputation(BuyerReputation.UNRELIABLE);
    }

    /**
     * Преобразовать список покупателей в CSV-формат.
     */
    public String toCsv(List<Customer> customers) {
        StringBuilder sb = new StringBuilder();
        sb.append("phone,sent,picked,returned,reputation\n");
        for (Customer c : customers) {
            sb.append(c.getPhone()).append(',')
              .append(c.getSentCount()).append(',')
              .append(c.getPickedUpCount()).append(',')
              .append(c.getReturnedCount()).append(',')
              .append(c.getReputation()).append('\n');
        }
        return sb.toString();
    }

    /**
     * Количество покупателей с привязанным Telegram.
     */
    public long countTelegramBoundCustomers() {
        return customerRepository.countByTelegramChatIdNotNull();
    }

    /**
     * Количество магазинов с включёнными напоминаниями.
     */
    public long countStoresWithReminders() {
        return storeTelegramSettingsRepository.countByRemindersEnabledTrue();
    }

    /**
     * Получить последние уведомления.
     */
    public List<CustomerNotificationLog> getRecentLogs() {
        return notificationRepository.findTop10ByOrderBySentAtDesc();
    }

    /**
     * Список магазинов с настройками и планом подписки владельца.
     */
    public List<StoreAdminInfoDTO> getStoresInfo() {
        List<Store> stores = storeRepository.findAllWithSettingsAndSubscription();
        return stores.stream()
                .map(s -> new StoreAdminInfoDTO(
                        s.getId(),
                        s.getName(),
                        s.getOwner().getEmail(),
                        Optional.ofNullable(s.getTelegramSettings()).map(StoreTelegramSettings::isEnabled).orElse(false),
                        Optional.ofNullable(s.getTelegramSettings()).map(StoreTelegramSettings::isRemindersEnabled).orElse(false),
                        Optional.ofNullable(s.getOwner().getSubscription())
                                .map(UserSubscription::getSubscriptionPlan)
                                .map(SubscriptionPlan::getName)
                                .orElse("NONE")
                ))
                .collect(Collectors.toList());
    }

    /**
     * Список подписок пользователей.
     */
    public List<UserSubscription> getAllUserSubscriptions() {
        return userSubscriptionRepository.findAllWithUserAndPlan();
    }

    /**
     * Получить все планы подписки.
     */
    public List<SubscriptionPlan> getPlans() {
        return subscriptionPlanRepository.findAll();
    }

    /**
     * Подсчитать количество магазинов в системе.
     */
    public long countStores() {
        return storeRepository.count();
    }

    /**
     * Получить список всех посылок системы с информацией о владельце и магазине.
     *
     * @param page номер страницы
     * @param size размер страницы
     * @return страница посылок для отображения в админ-панели
     */
    public org.springframework.data.domain.Page<TrackParcelAdminInfoDTO> getAllParcels(int page, int size) {
        // Создаём объект пагинации
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);

        // Загружаем посылки с подгруженными магазином и пользователем
        org.springframework.data.domain.Page<TrackParcel> parcels = trackParcelRepository.findAllWithStoreAndUser(pageable);

        // Преобразуем в DTO с форматированной датой
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
                .withZone(java.time.ZoneId.systemDefault());

        return parcels.map(p -> new TrackParcelAdminInfoDTO(
                p.getId(),
                p.getNumber(),
                p.getStatus().getDescription(),
                p.getStore().getName(),
                p.getUser().getEmail(),
                formatter.format(p.getData())
        ));
    }
}
