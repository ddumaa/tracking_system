package com.project.tracking_system.service.store;

import com.project.tracking_system.controller.WebSocketController;
import com.project.tracking_system.entity.*;
import com.project.tracking_system.repository.StoreRepository;
import com.project.tracking_system.repository.StoreAnalyticsRepository;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.repository.UserRepository;
import com.project.tracking_system.repository.PostalServiceStatisticsRepository;
import com.project.tracking_system.repository.StoreTelegramSettingsRepository;
import com.project.tracking_system.dto.StoreTelegramSettingsDTO;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * @author Dmitriy Anisimov
 * @date 11.03.2025
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class StoreService {

    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final StoreAnalyticsRepository storeAnalyticsRepository;
    private final PostalServiceStatisticsRepository postalServiceStatisticsRepository;
    private final TrackParcelRepository trackParcelRepository;
    private final StoreTelegramSettingsRepository storeTelegramSettingsRepository;
    private final WebSocketController webSocketController;

    /**
     * Возвращает `Store` по Id, проверяя, принадлежит ли он указанному пользователю.
     * Если магазин не найден или не принадлежит пользователю — выбрасывает исключение.
     */
    public Store getStore(Long storeId, Long userId) {
        Store store = storeRepository.findStoreById(storeId);
        if (store == null) {
            throw new IllegalArgumentException("Магазин не найден!");
        }

        if (!store.getOwner().getId().equals(userId)) {
            throw new SecurityException("Вы не можете управлять этим магазином!");
        }

        return store;
    }

    /**
     * Найти магазин по Id и проверить принадлежность текущему пользователю.
     *
     * @param storeId   идентификатор магазина
     * @param principal текущий пользователь
     * @return найденный магазин
     */
    public Store findOwnedByUser(Long storeId, Principal principal) {
        String email = principal.getName();
        Long userId = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"))
                .getId();
        return getStore(storeId, userId);
    }

    /**
     * Возвращает список магазинов, принадлежащих пользователю.
     *
     * @param userId идентификатор пользователя
     * @return список магазинов владельца
     */
    public List<Store> getUserStores(Long userId) {
        return storeRepository.findByOwnerId(userId);
    }

    /**
     * Возвращает магазины пользователя вместе с Telegram-настройками.
     *
     * @param userId идентификатор пользователя
     * @return список магазинов с настройками
     */
    public List<Store> getUserStoresWithSettings(Long userId) {
        return storeRepository.findByOwnerIdFetchSettings(userId);
    }

    /**
     * Возвращает список идентификаторов магазинов пользователя.
     *
     * @param userId идентификатор пользователя
     * @return список ID магазинов
     */
    public List<Long> getUserStoreIds(Long userId) {
        return storeRepository.findStoreIdsByOwnerId(userId);
    }

    /**
     * Создать новый магазин.
     */
    @Transactional
    public Store createStore(Long userId, String storeName) {
        log.info("Начало создания магазина '{}' для пользователя ID={}", storeName, userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        int userStoreCount = storeRepository.countByOwnerId(userId);

        // Получаем подписку пользователя
        SubscriptionPlan subscriptionPlan = Optional.ofNullable(user.getSubscription())
                .map(UserSubscription::getSubscriptionPlan)
                .orElseThrow(() -> new IllegalStateException("У пользователя нет активной подписки"));

        int maxStores = subscriptionPlan.getMaxStores(); // Получаем лимит магазинов

        if (userStoreCount >= maxStores) {
            String message = "Вы достигли лимита магазинов (" + maxStores + ")";
            webSocketController.sendUpdateStatus(userId, message, false);
            throw new IllegalStateException(message);
        }

        Store store = new Store();
        store.setName(storeName);
        store.setOwner(user);

        Store savedStore = storeRepository.save(store);
        log.info("Магазин '{}' создан с ID={}", savedStore.getName(), savedStore.getId());

        // Создаём пустую статистику для нового магазина если её ещё нет
        if (storeAnalyticsRepository.findByStoreId(savedStore.getId()).isEmpty()) {
            StoreStatistics statistics = new StoreStatistics();
            statistics.setStore(savedStore);
            statistics.setTotalSent(0);
            statistics.setTotalDelivered(0);
            statistics.setTotalReturned(0);
            statistics.setSumDeliveryDays(BigDecimal.ZERO);
            statistics.setSumPickupDays(BigDecimal.ZERO);
            statistics.setUpdatedAt(ZonedDateTime.now(ZoneOffset.UTC));

            storeAnalyticsRepository.save(statistics);
            log.info("Создана пустая статистика для магазина ID={}", savedStore.getId());
        } else {
            log.warn("Статистика для магазина ID={} уже существует", savedStore.getId());
        }

        // Создаём пустую статистику для каждой почтовой службы
        for (PostalServiceType type : PostalServiceType.values()) {
            if (postalServiceStatisticsRepository
                    .findByStoreIdAndPostalServiceType(savedStore.getId(), type)
                    .isEmpty()) {
                PostalServiceStatistics psStats = new PostalServiceStatistics();
                psStats.setStore(savedStore);
                psStats.setPostalServiceType(type);
                psStats.setUpdatedAt(ZonedDateTime.now(ZoneOffset.UTC));
                postalServiceStatisticsRepository.save(psStats);
            } else {
                log.warn("Статистика {} уже существует для магазина ID={}", type, savedStore.getId());
            }
        }

        webSocketController.sendUpdateStatus(userId, "Магазин '" + storeName + "' добавлен!", true);

        // Создаём настройки Telegram по умолчанию
        StoreTelegramSettings telegramSettings = new StoreTelegramSettings();
        telegramSettings.setStore(savedStore);
        storeTelegramSettingsRepository.save(telegramSettings);
        savedStore.setTelegramSettings(telegramSettings);

        log.info("Создание магазина '{}' для пользователя ID={} успешно завершено", savedStore.getName(), userId);
        return savedStore;
    }

    /**
     * Создаёт магазин по умолчанию для указанного пользователя.
     * <p>
     * Использует внутренний метод {@link #createStore(Long, String)} для создания магазина
     * с названием "Мой магазин", после чего устанавливает флаг {@code default = true} и сохраняет изменения.
     * <p>
     * В процессе также:
     * <ul>
     *     <li>Создаётся объект {@code StoreStatistics} для аналитики;</li>
     *     <li>Создаются записи {@code PostalServiceStatistics} для всех служб доставки;</li>
     *     <li>Создаются настройки Telegram уведомлений {@code StoreTelegramSettings}.</li>
     * </ul>
     *
     * @param user пользователь, для которого создаётся магазин
     * @return созданный магазин с флагом {@code default = true}
     * @throws IllegalArgumentException если пользователь не найден или превышен лимит магазинов
     * @throws IllegalStateException    если отсутствует активная подписка
     */
    @Transactional
    public Store createDefaultStoreForUser(User user) {
        Store store = createStore(user.getId(), "Мой магазин");
        store.setDefault(true);
        return storeRepository.save(store);
    }

    /**
     * Обновить название магазина.
     *
     * @param storeId ID магазина, который нужно обновить.
     * @param userId  ID пользователя, который запрашивает обновление.
     */
    @Transactional
    public Store updateStore(Long storeId, Long userId, String newName) {
        log.info("Начало обновления магазина ID={} пользователем ID={}", storeId, userId);

        // Проверяем, что магазин принадлежит пользователю
        checkStoreOwnership(storeId, userId);

        // Загружаем магазин после проверки прав
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("Магазин не найден"));

        log.info("Обновление названия магазина: {} (ID={}) на '{}' пользователем ID={}",
                store.getName(), storeId, newName, userId);

        store.setName(newName);
        Store updatedStore = storeRepository.save(store);

        webSocketController.sendUpdateStatus(userId, "Название магазина обновлено на '" + newName + "'", true);

        log.info("Магазин ID={} успешно переименован в '{}'", storeId, newName);
        return updatedStore;
    }

    /**
     * Удаляет магазин, включая все связанные посылки и статистику.
     *
     * @param storeId ID магазина, который нужно удалить.
     * @param userId  ID пользователя, который запрашивает удаление.
     */
    @Transactional
    public void deleteStore(Long storeId, Long userId) {
        log.info("Начало удаления магазина ID={} пользователем ID={}", storeId, userId);

        // Проверяем, что магазин принадлежит пользователю
        checkStoreOwnership(storeId, userId);

        // Получаем количество магазинов у пользователя
        int userStoreCount = storeRepository.countByOwnerId(userId);
        if (userStoreCount <= 1) {
            log.warn("Попытка удалить единственный магазин пользователем ID={}", userId);
            webSocketController.sendUpdateStatus(userId, "Нельзя удалить единственный магазин!", false);
            return;
        }

        // Загружаем магазин
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("Магазин не найден"));

        log.info("Удаление магазина: {} (ID={}) пользователем ID={}", store.getName(), storeId, userId);

        // Удаляем все посылки магазина
        trackParcelRepository.deleteByStoreId(storeId);
        log.info("Удалены все посылки магазина ID={}", storeId);

        // Удаляем статистику магазина
        storeAnalyticsRepository.findByStoreId(storeId)
                .ifPresent(storeStatistics -> {
                    storeAnalyticsRepository.delete(storeStatistics);
                    log.info("Удалена статистика для магазина ID={}", storeId);
                });

        // Удаляем магазин
        storeRepository.deleteById(storeId);

        // 🔥 Отправляем WebSocket-уведомление
        webSocketController.sendUpdateStatus(userId, "Магазин '" + store.getName() + "' удалён!", true);

        log.info("Магазин ID={} успешно удалён пользователем ID={}", storeId, userId);
    }

    /**
     * Проверяет принадлежность магазина пользователю и выбрасывает исключение при отсутствии прав.
     *
     * @param storeId идентификатор магазина
     * @param userId  идентификатор пользователя
     */
    public void checkStoreOwnership(Long storeId, Long userId) {
        if (!userOwnsStore(storeId, userId)) {
            throw new SecurityException("Вы не можете управлять этим магазином");
        }
    }

    /**
     * Проверяет принадлежность магазина пользователю.
     *
     * @param storeId идентификатор магазина
     * @param userId  идентификатор пользователя
     * @return {@code true}, если магазин принадлежит пользователю
     */
    public boolean userOwnsStore(Long storeId, Long userId) {
        return storeRepository.existsByIdAndOwnerId(storeId, userId);
    }

    /**
     * Установка магазина по умолчанию для пользователя
     */
    @Transactional
    public void setDefaultStore(Long userId, Long storeId) {
        log.info("Начало установки магазина ID={} по умолчанию для пользователя ID={}", storeId, userId);

        List<Store> userStores = storeRepository.findByOwnerId(userId);

        if (userStores.size() == 1) {
            throw new IllegalStateException("У вас только один магазин, он уже установлен по умолчанию.");
        }

        Store selectedStore = userStores.stream()
                .filter(store -> store.getId().equals(storeId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Магазин не найден"));

        // Убираем статус "по умолчанию" у всех других магазинов пользователя
        userStores.forEach(store -> store.setDefault(store.getId().equals(storeId)));

        storeRepository.saveAll(userStores);

        log.info("Магазин '{}' теперь установлен по умолчанию для пользователя ID={}", selectedStore.getName(), userId);

        // 🔥 Отправляем WebSocket-уведомление
        webSocketController.sendUpdateStatus(userId, "Магазин по умолчанию: " + selectedStore.getName(), true);

        log.info("Установка магазина ID={} по умолчанию для пользователя ID={} завершена", storeId, userId);
    }

    /**
     * Получить ID магазина по умолчанию для пользователя.
     * Если у пользователя только 1 магазин — он автоматически становится по умолчанию.
     *
     * @param userId ID пользователя.
     * @return ID магазина по умолчанию или `null`, если магазинов нет.
     */
    public Long getDefaultStoreId(Long userId) {
        List<Store> userStores = storeRepository.findByOwnerId(userId);

        if (userStores.isEmpty()) {
            log.warn("⚠ У пользователя ID={} нет магазинов!", userId);
            return null;
        }

        if (userStores.size() == 1) {
            // Если у пользователя один магазин, он становится по умолчанию
            Store singleStore = userStores.get(0);
            if (!singleStore.isDefault()) {
                singleStore.setDefault(true);
                storeRepository.save(singleStore);
                log.info("✅ Автоматически установлен магазин ID={} как по умолчанию для пользователя ID={}",
                        singleStore.getId(), userId);
            }
            return singleStore.getId();
        }

        // Ищем магазин, установленный по умолчанию
        return userStores.stream()
                .filter(Store::isDefault)
                .map(Store::getId)
                .findFirst()
                .orElseGet(() -> {
                    // Если нет установленного по умолчанию, выбираем первый в списке
                    Store fallbackStore = userStores.get(0);
                    fallbackStore.setDefault(true);
                    storeRepository.save(fallbackStore);
                    log.info("🔄 Магазин по умолчанию не был установлен, теперь ID={} назначен как дефолтный для пользователя ID={}",
                            fallbackStore.getId(), userId);
                    return fallbackStore.getId();
                });
    }

    /**
     * Ищет магазин по имени для конкретного пользователя.
     *
     * @param storeName название магазина
     * @param userId    ID пользователя
     * @return ID найденного магазина или null, если не найден
     */
    public Long findStoreIdByName(String storeName, Long userId) {
        return storeRepository.findByOwnerId(userId).stream()
                .filter(store -> store.getName().equalsIgnoreCase(storeName))
                .map(Store::getId)
                .findFirst()
                .orElse(null);
    }

    /**
     * Определяет корректный ID магазина исходя из списка доступных магазинов и переданного значения.
     *
     * @param storeId переданный ID магазина (может быть {@code null})
     * @param stores  список магазинов пользователя
     * @return выбранный ID магазина или {@code null}, если определить невозможно
     */
    public Long resolveStoreId(Long storeId, List<Store> stores) {
        if (storeId != null) return storeId;

        if (stores.size() == 1) {
            return stores.get(0).getId();
        }

        return stores.stream()
                .filter(Store::isDefault)
                .map(Store::getId)
                .findFirst()
                .orElse(null);
    }

    /**
     * Преобразовать сущность настроек в DTO.
     */
    public StoreTelegramSettingsDTO toDto(StoreTelegramSettings settings) {
        if (settings == null) return null;
        StoreTelegramSettingsDTO dto = new StoreTelegramSettingsDTO();
        dto.setEnabled(settings.isEnabled());
        dto.setReminderStartAfterDays(settings.getReminderStartAfterDays());
        dto.setReminderRepeatIntervalDays(settings.getReminderRepeatIntervalDays());
        dto.setCustomSignature(settings.getCustomSignature());
        dto.setRemindersEnabled(settings.isRemindersEnabled());
        return dto;
    }

    /**
     * Обновить сущность настроек на основе DTO.
     */
    public void updateFromDto(StoreTelegramSettings settings, StoreTelegramSettingsDTO dto) {
        settings.setEnabled(dto.isEnabled());
        settings.setReminderStartAfterDays(dto.getReminderStartAfterDays());
        settings.setReminderRepeatIntervalDays(dto.getReminderRepeatIntervalDays());
        settings.setCustomSignature(dto.getCustomSignature());
        settings.setRemindersEnabled(dto.isRemindersEnabled());
    }


}