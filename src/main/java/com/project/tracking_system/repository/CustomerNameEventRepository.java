package com.project.tracking_system.repository;

import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.entity.CustomerNameEvent;
import com.project.tracking_system.entity.CustomerNameEventStatus;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Репозиторий для работы с событиями изменения ФИО.
 */
public interface CustomerNameEventRepository extends JpaRepository<CustomerNameEvent, Long> {

    /**
     * Найти последнее событие для покупателя.
     *
     * @param customer покупатель
     * @return последнее событие или {@link Optional#empty()}
     */
    Optional<CustomerNameEvent> findTopByCustomerOrderByCreatedAtDesc(Customer customer);

    /**
     * Получить все события покупателя в порядке их создания.
     *
     * @param customer покупатель
     * @return список событий
     */
    List<CustomerNameEvent> findByCustomerOrderByCreatedAtAsc(Customer customer);

    /**
     * Найти события по статусу, созданные до указанного времени.
     *
     * @param status     статус события
     * @param createdAt  верхняя граница даты создания
     * @return список подходящих событий
     */
    List<CustomerNameEvent> findByStatusAndCreatedAtBefore(CustomerNameEventStatus status,
                                                           ZonedDateTime createdAt);
}
