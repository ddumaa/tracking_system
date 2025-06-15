package com.project.tracking_system.repository;

import com.project.tracking_system.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Репозиторий для работы с сущностью {@link Customer}.
 */
public interface CustomerRepository extends JpaRepository<Customer, Long> {
}
