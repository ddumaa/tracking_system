package com.project.tracking_system.repository;

import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.entity.BuyerReputation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Репозиторий для работы с сущностью {@link Customer}.
 */
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /**
     * Найти покупателя по номеру телефона.
     *
     * @param phone номер телефона в формате 375XXXXXXXXX
     * @return найденный покупатель или {@link java.util.Optional#empty()}
     */
    Optional<Customer> findByPhone(String phone);

    /**
     * Найти покупателя по идентификатору чата Telegram.
     *
     * @param chatId идентификатор чата
     * @return найденный покупатель или {@link java.util.Optional#empty()}
     */
    Optional<Customer> findByTelegramChatId(Long chatId);

    /**
     * Обновить статус уведомлений для покупателя по chatId.
     *
     * @param chatId  идентификатор Telegram-чата
     * @param enabled новое значение флага
     * @return количество обновлённых записей
     */
    @Modifying
    @Transactional
    @Query("""
        UPDATE Customer c
        SET c.notificationsEnabled = :enabled
        WHERE c.telegramChatId = :chatId
        """)
    int updateNotificationsEnabled(@Param("chatId") Long chatId, @Param("enabled") boolean enabled);

    /**
     * Атомарно увеличить счётчик отправленных посылок с проверкой версии.
     *
     * @param id      идентификатор покупателя
     * @param version ожидаемая версия записи
     * @return количество обновлённых записей (0 при конфликте версий)
     */
    @Modifying
    @Transactional
    @Query("""
        UPDATE Customer c
        SET c.sentCount = c.sentCount + 1, c.version = c.version + 1
        WHERE c.id = :id AND c.version = :version
        """)
    int incrementSentCount(@Param("id") Long id, @Param("version") long version);

    /**
     * Атомарно увеличить счётчик забранных посылок с учётом версии записи.
     *
     * @param id      идентификатор покупателя
     * @param version ожидаемая версия записи
     * @return количество обновлённых записей
     */
    @Modifying
    @Transactional
    @Query("""
        UPDATE Customer c
        SET c.pickedUpCount = c.pickedUpCount + 1, c.version = c.version + 1
        WHERE c.id = :id AND c.version = :version
        """)
    int incrementPickedUpCount(@Param("id") Long id, @Param("version") long version);

    /**
     * Атомарно увеличить счётчик возвращённых посылок с проверкой версии.
     *
     * @param id      идентификатор покупателя
     * @param version ожидаемая версия записи
     * @return количество обновлённых записей
     */
    @Modifying
    @Transactional
    @Query("""
        UPDATE Customer c
        SET c.returnedCount = c.returnedCount + 1, c.version = c.version + 1
        WHERE c.id = :id AND c.version = :version
        """)
    int incrementReturnedCount(@Param("id") Long id, @Param("version") long version);

    /**
     * Обновить репутацию покупателя без изменения версии записи.
     *
     * @param id         идентификатор покупателя
     * @param version    ожидаемая версия записи
     * @param reputation новое значение репутации
     * @return количество обновлённых записей
     */
    @Modifying
    @Transactional
    @Query("""
        UPDATE Customer c
        SET c.reputation = :reputation
        WHERE c.id = :id AND c.version = :version
        """)
    int updateReputation(
            @Param("id") Long id,
            @Param("version") long version,
            @Param("reputation") BuyerReputation reputation
    );

    /**
     * Подсчитать количество покупателей с указанной репутацией.
     *
     * @param reputation репутация покупателя
     * @return число покупателей
     */
    long countByReputation(BuyerReputation reputation);

    /**
     * Получить покупателей по репутации.
     *
     * @param reputation уровень доверия покупателя
     * @return список покупателей
     */
    List<Customer> findByReputation(BuyerReputation reputation);

    /**
     * Подсчитать количество покупателей, привязавших Telegram.
     *
     * @return число покупателей с Telegram
     */
    long countByTelegramChatIdNotNull();

    /**
     * Получить идентификаторы чатов подтверждённых покупателей в Telegram.
     * <p>
     * Метод возвращает только те чаты, где покупатель подтвердил связку с ботом, что
     * позволяет выполнять рассылки без загрузки полного профиля клиента.
     * </p>
     *
     * @return список идентификаторов чатов Telegram
     */
    @Query("""
        SELECT c.telegramChatId
        FROM Customer c
        WHERE c.telegramChatId IS NOT NULL
          AND c.telegramConfirmed = true
    """)
    List<Long> findConfirmedTelegramChatIds();
}
