package com.project.tracking_system.repository;

import com.project.tracking_system.entity.CustomerTelegramLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Найти привязку Telegram по чату и магазину.
     *
     * @param chatId  идентификатор чата Telegram
     * @param storeId идентификатор магазина
     * @return найденная привязка или {@link java.util.Optional#empty()}
     */
    Optional<CustomerTelegramLink> findByTelegramChatIdAndStoreId(Long chatId, Long storeId);

    /**
     * Найти активные привязки покупателя (подтверждённые и с включёнными уведомлениями).
     *
     * @param customerId идентификатор покупателя
     * @return список активных привязок
     */
    List<CustomerTelegramLink> findByCustomerIdAndTelegramConfirmedTrueAndNotificationsEnabledTrue(Long customerId);

    /**
     * Найти активные привязки по номеру телефона покупателя.
     *
     * @param phone телефон покупателя в формате 375XXXXXXXXX
     * @return список активных привязок
     */
    @Query("""
            SELECT l FROM CustomerTelegramLink l
            JOIN l.customer c
            WHERE c.phone = :phone
              AND l.telegramConfirmed = true
              AND l.notificationsEnabled = true
            """)
    List<CustomerTelegramLink> findActiveLinksByPhone(@Param("phone") String phone);
}
