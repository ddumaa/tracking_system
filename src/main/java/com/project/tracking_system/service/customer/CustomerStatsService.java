package com.project.tracking_system.service.customer;

import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сервис для обновления статистики покупателя.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerStatsService {

    private final CustomerRepository customerRepository;

    /**
     * Увеличить счётчик отправленных посылок покупателя.
     *
     * @param customer покупатель
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void incrementSent(Customer customer) {
        if (customer == null) {
            return;
        }
        // Пытаемся атомарно увеличить счётчик
        int updated = customerRepository.incrementSentCount(customer.getId());
        if (updated == 0) {
            // При неудаче загружаем сущность и обновляем вручную
            Customer fresh = customerRepository.findById(customer.getId())
                    .orElseThrow(() -> new IllegalStateException("Покупатель не найден"));
            fresh.setSentCount(fresh.getSentCount() + 1);
            fresh.recalculateReputation();
            customerRepository.save(fresh);
            // Синхронизируем переданный объект
            customer.setSentCount(fresh.getSentCount());
            customer.setReputation(fresh.getReputation());
        } else {
            customer.setSentCount(customer.getSentCount() + 1);
            customer.recalculateReputation();
            // Сохраняем репутацию для согласованности с БД
            customerRepository.save(customer);
        }
    }

    /**
     * Увеличить счётчик забранных посылок покупателя.
     *
     * @param customer покупатель
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void incrementPickedUp(Customer customer) {
        if (customer == null) {
            return;
        }
        // Атомарное обновление счётчика
        int updated = customerRepository.incrementPickedUpCount(customer.getId());
        if (updated == 0) {
            // При неудаче читаем и обновляем вручную
            Customer fresh = customerRepository.findById(customer.getId())
                    .orElseThrow(() -> new IllegalStateException("Покупатель не найден"));
            fresh.setPickedUpCount(fresh.getPickedUpCount() + 1);
            fresh.recalculateReputation();
            customerRepository.save(fresh);
            // Обновляем переданный экземпляр
            customer.setPickedUpCount(fresh.getPickedUpCount());
            customer.setReputation(fresh.getReputation());
        } else {
            customer.setPickedUpCount(customer.getPickedUpCount() + 1);
            customer.recalculateReputation();
            // Сохраняем репутацию для согласованности с БД
            customerRepository.save(customer);
        }
    }
}
