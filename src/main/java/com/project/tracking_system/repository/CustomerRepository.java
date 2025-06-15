package com.project.tracking_system.repository;

import com.project.tracking_system.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

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
}
