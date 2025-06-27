package com.project.tracking_system.repository;

import com.project.tracking_system.entity.CustomerTelegramLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для связей покупателя с Telegram-чатами.
 */
public interface CustomerTelegramLinkRepository extends JpaRepository<CustomerTelegramLink, Long> {

    /**
     * Найти все привязки Telegram конкретного покупателя.
     *
     * @param customerId идентификатор покупателя
     * @return список привязок
     */
    List<CustomerTelegramLink> findByCustomerId(Long customerId);

    /**
     * Найти все привязки Telegram для магазина.
     *
     * @param storeId идентификатор магазина
     * @return список привязок
     */
    List<CustomerTelegramLink> findByStoreId(Long storeId);

    /**
     * Найти привязку по идентификатору чата Telegram.
     *
     * @param chatId идентификатор Telegram-чата
     * @return найденная привязка или {@link java.util.Optional#empty()}
     */
    Optional<CustomerTelegramLink> findByTelegramChatId(Long chatId);

    /**
     * Найти привязку к магазину для конкретного покупателя.
     *
     * @param customerId идентификатор покупателя
     * @param storeId    идентификатор магазина
     * @return найденная привязка или {@link java.util.Optional#empty()}
     */
    Optional<CustomerTelegramLink> findByCustomerIdAndStoreId(Long customerId, Long storeId);
}
