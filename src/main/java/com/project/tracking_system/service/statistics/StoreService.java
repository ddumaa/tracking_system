package com.project.tracking_system.service.statistics;

import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.repository.StoreRepository;
import com.project.tracking_system.repository.StoreStatisticsRepository;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
    private final StoreStatisticsRepository storeStatisticsRepository;
    private final TrackParcelRepository trackParcelRepository;

    /**
     * Получить список магазинов пользователя.
     */
    public List<Store> getUserStores(Long userId) {
        return storeRepository.findByOwnerId(userId);
    }

    /**
     * Создать новый магазин.
     */
    @Transactional
    public Store createStore(Long userId, String storeName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        Store store = new Store();
        store.setName(storeName);
        store.setOwner(user);

        return storeRepository.save(store);
    }

    /**
     * Обновить название магазина.
     */
    @Transactional
    public Store updateStore(Long storeId, String newName) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("Магазин не найден"));

        store.setName(newName);
        return storeRepository.save(store);
    }

    /**
     * Удаляет магазин, включая все связанные посылки и статистику.
     *
     * @param storeId ID магазина, который нужно удалить.
     */
    @Transactional
    public void deleteStore(Long storeId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("Магазин не найден"));

        log.info("Удаление магазина: {} (ID={})", store.getName(), storeId);

        // Удаляем все посылки, связанные с этим магазином
        trackParcelRepository.deleteByStoreId(storeId);
        log.info("Удалены все посылки магазина ID={}", storeId);

        // Удаляем статистику магазина, если есть
        storeStatisticsRepository.findByStoreId(storeId)
                .ifPresent(storeStatistics -> {
                    storeStatisticsRepository.delete(storeStatistics);
                    log.info("Удалена статистика для магазина ID={}", storeId);
                });

        // Удаляем сам магазин
        storeRepository.delete(store);
        log.info("Магазин ID={} удалён", storeId);
    }

}