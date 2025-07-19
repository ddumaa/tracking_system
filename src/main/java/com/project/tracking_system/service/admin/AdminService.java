package com.project.tracking_system.service.admin;

import com.project.tracking_system.dto.*;
import com.project.tracking_system.entity.*;
import com.project.tracking_system.repository.*;
import com.project.tracking_system.dto.TrackingResultAdd;
import com.project.tracking_system.service.track.TrackConstants;
import com.project.tracking_system.service.track.TrackDeletionService;
import com.project.tracking_system.service.track.TrackMeta;
import com.project.tracking_system.service.track.TrackUpdateCoordinatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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
    private final SubscriptionPlanService subscriptionPlanService;
    private final TrackParcelRepository trackParcelRepository;
    private final UserRepository userRepository;
    private final TrackDeletionService trackDeletionService;
    private final TrackUpdateCoordinatorService trackUpdateCoordinatorService;
    private final com.project.tracking_system.service.user.UserService userService;
    private final com.project.tracking_system.service.store.StoreService storeService;

    /**
     * Подсчитать общее количество покупателей.
     */
    @Transactional(readOnly = true)
    public long countCustomers() {
        return customerRepository.count();
    }

    /**
     * Подсчитать количество ненадёжных покупателей.
     */
    @Transactional(readOnly = true)
    public long countUnreliableCustomers() {
        return customerRepository.countByReputation(BuyerReputation.UNRELIABLE);
    }

    /**
     * Получить список ненадёжных покупателей.
     */
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
    public long countTelegramBoundCustomers() {
        return customerRepository.countByTelegramChatIdNotNull();
    }

    /**
     * Количество магазинов с включёнными напоминаниями.
     */
    @Transactional(readOnly = true)
    public long countStoresWithReminders() {
        return storeTelegramSettingsRepository.countByRemindersEnabledTrue();
    }

    /**
     * Получить последние уведомления.
     */
    @Transactional(readOnly = true)
    public List<CustomerNotificationLog> getRecentLogs() {
        return notificationRepository.findTop10ByOrderBySentAtDesc();
    }

    /**
     * Список магазинов с настройками и планом подписки владельца.
     */
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
    public List<UserSubscription> getAllUserSubscriptions() {
        return userSubscriptionRepository.findAllWithUserAndPlan();
    }

    /**
     * Получить все планы подписки.
     */
    @Transactional(readOnly = true)
    public List<SubscriptionPlanDTO> getPlans() {
        return subscriptionPlanService.getAllPlans();
    }

    /**
     * Создать новый тарифный план.
     *
     * @param dto параметры нового плана
     * @return созданный план
     */
    public SubscriptionPlan createPlan(SubscriptionPlanDTO dto) {
        return subscriptionPlanService.createPlan(dto);
    }

    /**
     * Обновить тарифный план.
     *
     * @param id  идентификатор плана
     * @param dto новые параметры
     * @return обновлённый план
     */
    public SubscriptionPlan updatePlan(Long id, SubscriptionPlanDTO dto) {
        return subscriptionPlanService.updatePlan(id, dto);
    }

    /**
     * Изменить активность плана.
     *
     * @param id     идентификатор плана
     * @param active новый статус
     */
    public void setPlanActive(Long id, boolean active) {
        subscriptionPlanService.setPlanActive(id, active);
    }

    /**
     * Удалить тарифный план.
     *
     * @param id идентификатор плана
     */
    public void deletePlan(Long id) {
        subscriptionPlanService.deletePlan(id);
    }

    /**
     * Подсчитать количество магазинов в системе.
     */
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
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
                formatter.format(p.getTimestamp())
        ));
    }

    /**
     * Получить список пользователей с фильтрами по email, роли и подписке.
     *
     * @param search       часть email
     * @param role         строковое представление роли
     * @param subscription название плана подписки
     * @return список DTO с информацией о пользователях
     */
    @Transactional(readOnly = true)
    public List<UserListAdminInfoDTO> getUsers(String search, String role, String subscription) {
        Role roleEnum = null;
        if (role != null && !role.isBlank()) {
            try {
                roleEnum = Role.valueOf(role);
            } catch (IllegalArgumentException e) {
                log.warn("Некорректная роль '{}', фильтр проигнорирован", role);
            }
        }

        List<User> users = userRepository.findByFilters(
                search != null ? search : "",
                roleEnum,
                subscription
        );

        List<UserListAdminInfoDTO> result = new ArrayList<>();

        for (User u : users) {
            String code = Optional.ofNullable(u.getSubscription())
                    .map(UserSubscription::getSubscriptionPlan)
                    .map(SubscriptionPlan::getCode)
                    .orElseGet(() -> subscriptionPlanService.getFreePlan().getCode());

            result.add(new UserListAdminInfoDTO(
                    u.getId(),
                    u.getEmail(),
                    u.getRole(),
                    code
            ));
        }

        return result;
    }

    /**
     * Найти посылку по трек-номеру.
     *
     * @param number трек-номер
     * @return DTO с информацией о посылке или {@code null}
     */
    @Transactional(readOnly = true)
    public TrackParcelAdminInfoDTO findParcelByNumber(String number) {
        TrackParcel parcel = trackParcelRepository.findByNumberWithStoreAndUser(number);
        if (parcel == null) {
            return null;
        }
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter
                .ofPattern("dd.MM.yyyy HH:mm:ss")
                .withZone(java.time.ZoneId.systemDefault());
        return new TrackParcelAdminInfoDTO(
                parcel.getId(),
                parcel.getNumber(),
                parcel.getStatus().getDescription(),
                parcel.getStore().getName(),
                parcel.getUser().getEmail(),
                formatter.format(parcel.getTimestamp())
        );
    }

    /**
     * Получить информацию о посылке по идентификатору.
     *
     * @param id идентификатор посылки
     * @return DTO с информацией о посылке
     */
    @Transactional(readOnly = true)
    public TrackParcelAdminInfoDTO getParcelById(Long id) {
        TrackParcel parcel = trackParcelRepository.findByIdWithStoreAndUser(id);
        if (parcel == null) {
            throw new IllegalArgumentException("Посылка не найдена");
        }
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter
                .ofPattern("dd.MM.yyyy HH:mm:ss")
                .withZone(java.time.ZoneId.systemDefault());
        return new TrackParcelAdminInfoDTO(
                parcel.getId(),
                parcel.getNumber(),
                parcel.getStatus().getDescription(),
                parcel.getStore().getName(),
                parcel.getUser().getEmail(),
                formatter.format(parcel.getTimestamp())
        );
    }

    /**
     * Удалить посылку по идентификатору.
     *
     * @param id идентификатор посылки
     */
    public void deleteParcel(Long id) {
        TrackParcel parcel = trackParcelRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Посылка не найдена"));
        trackDeletionService.deleteByNumbersAndUserId(java.util.List.of(parcel.getNumber()), parcel.getUser().getId());
    }

    /**
     * Принудительно обновить статус посылки.
     *
     * @param id идентификатор посылки
     */
    public TrackingResultAdd forceUpdateParcel(Long id) {
        TrackParcel parcel = trackParcelRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Посылка не найдена"));

        TrackMeta meta = new TrackMeta(
                parcel.getNumber(),
                parcel.getStore().getId(),
                null,
                true
        );

        // Обновляем трек через координатор и берём первый результат
        List<TrackingResultAdd> results =
                trackUpdateCoordinatorService.process(List.of(meta), parcel.getUser().getId());
        return results.isEmpty()
                ? new TrackingResultAdd(meta.number(), TrackConstants.NO_DATA_STATUS)
                : results.get(0);
    }

    /**
     * Удалить пользователя целиком.
     *
     * @param userId идентификатор пользователя
     */
    public void deleteUser(Long userId) {
        log.info("Админ удаляет пользователя ID={}", userId);
        userService.deleteUser(userId);
    }

    /**
     * Удалить магазин вместе с его посылками и статистикой.
     *
     * @param storeId идентификатор магазина
     */
    public void deleteStore(Long storeId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("Магазин не найден"));
        storeService.deleteStore(storeId, store.getOwner().getId());
    }

    /**
     * Удалить покупателя и разорвать связи с его посылками.
     *
     * @param customerId идентификатор покупателя
     */
    @Transactional
    public void deleteCustomer(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Покупатель не найден"));

        // Удаляем связи с посылками
        trackParcelRepository.clearCustomer(customerId);

        // Удаляем связанные логи уведомлений
        notificationRepository.deleteByCustomerId(customerId);

        customerRepository.delete(customer);
        log.info("Удалён покупатель ID={}", customerId);
    }
}
