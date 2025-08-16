package com.project.tracking_system.service.customer;

import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.entity.CustomerNameEvent;
import com.project.tracking_system.entity.CustomerNameEventStatus;
import com.project.tracking_system.repository.CustomerNameEventRepository;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сервис фиксации попыток изменения ФИО покупателя.
 */
@Service
@RequiredArgsConstructor
public class CustomerNameEventService {

    private final CustomerNameEventRepository repository;

    /**
     * Записать событие изменения ФИО и пометить предыдущее как устаревшее.
     *
     * @param customer покупатель
     * @param oldName  предыдущее ФИО
     * @param newName  новое ФИО
     */
    @Transactional
    public void recordEvent(Customer customer, String oldName, String newName) {
        if (customer == null || newName == null || newName.isBlank()) {
            return;
        }
        repository.findTopByCustomerOrderByCreatedAtDesc(customer)
                .ifPresent(event -> {
                    event.setStatus(CustomerNameEventStatus.SUPERSEDED);
                    repository.save(event);
                });
        CustomerNameEvent event = new CustomerNameEvent();
        event.setCustomer(customer);
        event.setOldName(oldName);
        event.setNewName(newName);
        event.setStatus(CustomerNameEventStatus.ACTIVE);
        event.setCreatedAt(ZonedDateTime.now(ZoneOffset.UTC));
        repository.save(event);
    }
}
