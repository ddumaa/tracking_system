package com.project.tracking_system.service.store;

import com.project.tracking_system.controller.WebSocketController;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.SubscriptionPlan;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.entity.UserSubscription;
import com.project.tracking_system.repository.StoreRepository;
import com.project.tracking_system.repository.AnalyticsRepository;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final AnalyticsRepository analyticsRepository;
    private final TrackParcelRepository trackParcelRepository;
    private final WebSocketController webSocketController;

    /**
     * Получить список магазинов пользователя.
     */
    public List<Store> getUserStores(Long userId) {
        return storeRepository.findByOwnerId(userId);
    }

    /**
     * Получить список ID магазинов пользователя.
     */
    public List<Long> getUserStoreIds(Long userId) {
        return storeRepository.findStoreIdsByOwnerId(userId);
    }

    /**
     * Создать новый магазин.
     */
    @Transactional
    public Store createStore(Long userId, String storeName) {
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
        webSocketController.sendUpdateStatus(userId, "Магазин '" + storeName + "' добавлен!", true);

        return savedStore;
    }

    /**
     * Обновить название магазина.
     *
     * @param storeId ID магазина, который нужно обновить.
     * @param userId  ID пользователя, который запрашивает обновление.
     */
    @Transactional
    public Store updateStore(Long storeId, Long userId, String newName) {
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
        // Проверяем, что магазин принадлежит пользователю
        checkStoreOwnership(storeId, userId);

        // Загружаем магазин
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("Магазин не найден"));

        log.info("Удаление магазина: {} (ID={}) пользователем ID={}", store.getName(), storeId, userId);

        // Удаляем посылки
        trackParcelRepository.deleteByStoreId(storeId);
        log.info("Удалены все посылки магазина ID={}", storeId);

        // Удаляем статистику магазина
        analyticsRepository.findByStoreId(storeId)
                .ifPresent(storeStatistics -> {
                    analyticsRepository.delete(storeStatistics);
                    log.info("Удалена статистика для магазина ID={}", storeId);
                });

        // Удаляем сам магазин
        storeRepository.deleteById(storeId);
        log.info("Магазин ID={} удалён пользователем ID={}", storeId, userId);

        // 🔥 Отправляем WebSocket-уведомление
        webSocketController.sendUpdateStatus(userId, "Магазин '" + store.getName() + "' удалён!", true);

    }

    /**
     * Проверяет, принадлежит ли магазин пользователю, и выбрасывает исключение, если нет.
     */
    private void checkStoreOwnership(Long storeId, Long userId) {
        if (!userOwnsStore(storeId, userId)) {
            throw new SecurityException("Вы не можете управлять этим магазином");
        }
    }

    /**
     * Проверяет, принадлежит ли магазин пользователю.
     */
    public boolean userOwnsStore(Long storeId, Long userId) {
        return storeRepository.existsByIdAndOwnerId(storeId, userId);
    }

    /**
     * Установка магазина по умолчанию для пользователя
     */
    @Transactional
    public void setDefaultStore(Long userId, Long storeId) {
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
    }


}