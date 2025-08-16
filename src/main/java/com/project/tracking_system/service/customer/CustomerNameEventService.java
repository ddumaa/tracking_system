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
        event.setStatus(CustomerNameEventStatus.PENDING);
        event.setCreatedAt(ZonedDateTime.now(ZoneOffset.UTC));
        repository.save(event);
    }

    /**
     * Подтвердить предложенное ФИО от лица покупателя.
     *
     * @param eventId        идентификатор заявки
     * @param chatCustomerId идентификатор покупателя из Telegram
     * @return {@code true}, если статус обновлён
     */
    @Transactional
    public boolean confirmFromTelegram(Long eventId, Long chatCustomerId) {
        if (eventId == null || chatCustomerId == null) {
            return false;
        }
        return repository.findById(eventId)
                .filter(e -> e.getStatus() == CustomerNameEventStatus.PENDING
                        && e.getCustomer() != null
                        && e.getCustomer().getId() != null
                        && e.getCustomer().getId().equals(chatCustomerId))
                .map(e -> {
                    e.setStatus(CustomerNameEventStatus.APPLIED);
                    repository.save(e);
                    return true;
                })
                .orElse(false);
    }

    /**
     * Отклонить предложение магазина.
     *
     * @param eventId        идентификатор заявки
     * @param chatCustomerId идентификатор покупателя из Telegram
     * @return {@code true}, если статус обновлён
     */
    @Transactional
    public boolean rejectFromTelegram(Long eventId, Long chatCustomerId) {
        if (eventId == null || chatCustomerId == null) {
            return false;
        }
        return repository.findById(eventId)
                .filter(e -> e.getStatus() == CustomerNameEventStatus.PENDING
                        && e.getCustomer() != null
                        && e.getCustomer().getId() != null
                        && e.getCustomer().getId().equals(chatCustomerId))
                .map(e -> {
                    e.setStatus(CustomerNameEventStatus.REJECTED);
                    repository.save(e);
                    return true;
                })
                .orElse(false);
    }

    /**
     * Применить исправленное покупателем ФИО.
     *
     * @param eventId        идентификатор исходной заявки
     * @param chatCustomerId идентификатор покупателя из Telegram
     * @param newName        новое ФИО от покупателя
     * @return {@code true}, если новое событие создано
     */
    @Transactional
    public boolean changeFromTelegram(Long eventId, Long chatCustomerId, String newName) {
        if (eventId == null || chatCustomerId == null || newName == null || newName.isBlank()) {
            return false;
        }
        return repository.findById(eventId)
                .filter(e -> e.getStatus() == CustomerNameEventStatus.PENDING
                        && e.getCustomer() != null
                        && e.getCustomer().getId() != null
                        && e.getCustomer().getId().equals(chatCustomerId))
                .map(e -> {
                    e.setStatus(CustomerNameEventStatus.SUPERSEDED);
                    repository.save(e);

                    CustomerNameEvent applied = new CustomerNameEvent();
                    applied.setCustomer(e.getCustomer());
                    applied.setOldName(e.getOldName());
                    applied.setNewName(newName);
                    applied.setStatus(CustomerNameEventStatus.APPLIED);
                    applied.setCreatedAt(ZonedDateTime.now(ZoneOffset.UTC));
                    repository.save(applied);
                    return true;
                })
                .orElse(false);
    }
}
