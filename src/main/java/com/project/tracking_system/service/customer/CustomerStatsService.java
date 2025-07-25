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
        log.debug("🔄 Попытка атомарного увеличения отправленных для customerId={}", customer.getId());
        int updated = customerRepository.incrementSentCount(customer.getId());
        if (updated == 0) {
            log.warn("⚠️ Не удалось атомарно обновить отправленные для customerId={}, переключаемся на ручной режим", customer.getId());
            // При неудаче загружаем сущность и обновляем вручную
            Customer fresh = customerRepository.findById(customer.getId())
                    .orElseThrow(() -> new IllegalStateException("Покупатель не найден"));
            fresh.setSentCount(fresh.getSentCount() + 1);
            fresh.recalculateReputation();
            customerRepository.save(fresh);
            // Синхронизируем переданный объект
            customer.setSentCount(fresh.getSentCount());
            customer.setReputation(fresh.getReputation());
            log.debug("✅ Счётчик отправленных вручную увеличен для customerId={}", customer.getId());
        } else {
            log.debug("✅ Атомарное увеличение отправленных успешно для customerId={}", customer.getId());
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
        log.debug("🔄 Попытка атомарного увеличения забранных для customerId={}", customer.getId());
        int updated = customerRepository.incrementPickedUpCount(customer.getId());
        if (updated == 0) {
            log.warn("⚠️ Не удалось атомарно обновить забранные для customerId={}, переключаемся на ручной режим", customer.getId());
            // При неудаче читаем и обновляем вручную
            Customer fresh = customerRepository.findById(customer.getId())
                    .orElseThrow(() -> new IllegalStateException("Покупатель не найден"));
            fresh.setPickedUpCount(fresh.getPickedUpCount() + 1);
            fresh.recalculateReputation();
            customerRepository.save(fresh);
            // Обновляем переданный экземпляр
            customer.setPickedUpCount(fresh.getPickedUpCount());
            customer.setReputation(fresh.getReputation());
            log.debug("✅ Счётчик забранных вручную увеличен для customerId={}", customer.getId());
        } else {
            log.debug("✅ Атомарное увеличение забранных успешно для customerId={}", customer.getId());
            customer.setPickedUpCount(customer.getPickedUpCount() + 1);
            customer.recalculateReputation();
            // Сохраняем репутацию для согласованности с БД
            customerRepository.save(customer);
        }
    }

    /**
     * Увеличить счётчик возвращённых посылок покупателя.
     *
     * @param customer покупатель
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void incrementReturned(Customer customer) {
        if (customer == null) {
            return;
        }
        log.debug("🔄 Попытка атомарного увеличения возвратов для customerId={}", customer.getId());
        int updated = customerRepository.incrementReturnedCount(customer.getId());
        if (updated == 0) {
            log.warn("⚠️ Не удалось атомарно обновить возвраты для customerId={}, переключаемся на ручной режим", customer.getId());
            Customer fresh = customerRepository.findById(customer.getId())
                    .orElseThrow(() -> new IllegalStateException("Покупатель не найден"));
            fresh.setReturnedCount(fresh.getReturnedCount() + 1);
            fresh.recalculateReputation();
            customerRepository.save(fresh);
            customer.setReturnedCount(fresh.getReturnedCount());
            customer.setReputation(fresh.getReputation());
            log.debug("✅ Счётчик возвратов вручную увеличен для customerId={}", customer.getId());
        } else {
            log.debug("✅ Атомарное увеличение возвратов успешно для customerId={}", customer.getId());
            customer.setReturnedCount(customer.getReturnedCount() + 1);
            customer.recalculateReputation();
            customerRepository.save(customer);
        }
    }
}
