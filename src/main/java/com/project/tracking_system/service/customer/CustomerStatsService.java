package com.project.tracking_system.service.customer;

import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.repository.CustomerRepository;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
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
     * Универсальный метод увеличения счётчика статистики покупателя.
     * Принимает функции для атомарного обновления в БД и изменения нужного поля
     * в объекте. В случае конфликта версий выполняет ручное обновление.
     *
     * @param customer     покупатель
     * @param counterName  имя счётчика для логирования
     * @param atomicUpdate функция, выполняющая атомарное обновление в БД
     * @param getter       функция получения значения счётчика
     * @param setter       процедура установки значения счётчика
     * @return обновлённый экземпляр покупателя
     */
    private Customer updateStatistic(
            Customer customer,
            String counterName,
            BiFunction<Long, Long, Integer> atomicUpdate,
            Function<Customer, Integer> getter,
            BiConsumer<Customer, Integer> setter
    ) {
        if (customer == null) {
            return null;
        }
        log.debug("🔄 Попытка атомарного увеличения {} для customerId={}", counterName, customer.getId());
        int updated = atomicUpdate.apply(customer.getId(), customer.getVersion());
        Customer fresh;
        if (updated == 0) {
            log.warn("⚠️ Не удалось атомарно обновить {} для customerId={}, переключаемся на ручной режим", counterName, customer.getId());
            fresh = customerRepository.findById(customer.getId())
                    .orElseThrow(() -> new IllegalStateException("Покупатель не найден"));
            setter.accept(fresh, getter.apply(fresh) + 1);
            fresh.recalculateReputation();
            fresh = customerRepository.save(fresh);
            log.debug("✅ Счётчик {} вручную увеличен для customerId={}", counterName, customer.getId());
        } else {
            log.debug("✅ Атомарное увеличение {} успешно для customerId={}", counterName, customer.getId());
            fresh = customerRepository.findById(customer.getId())
                    .orElseThrow(() -> new IllegalStateException("Покупатель не найден"));
        }
        // Перекладываем актуальные данные в переданный объект для дальнейших вызовов
        setter.accept(customer, getter.apply(fresh));
        customer.setReputation(fresh.getReputation());
        customer.setVersion(fresh.getVersion());
        return fresh;
    }

    /**
     * Увеличивает счётчик отправленных посылок покупателя.
     *
     * @param customer покупатель
     * @return обновлённый экземпляр покупателя
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Customer incrementSent(Customer customer) {
        // Возвращаем перечитанного из БД покупателя с обновлённым счётчиком
        return updateStatistic(
                customer,
                "отправленных",
                customerRepository::incrementSentCount,
                Customer::getSentCount,
                Customer::setSentCount
        );
    }

    /**
     * Увеличивает счётчик забранных посылок покупателя.
     *
     * @param customer покупатель
     * @return обновлённый экземпляр покупателя
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Customer incrementPickedUp(Customer customer) {
        // После атомарного обновления счётчика возвращаем актуальную сущность
        return updateStatistic(
                customer,
                "забранных",
                customerRepository::incrementPickedUpCount,
                Customer::getPickedUpCount,
                Customer::setPickedUpCount
        );
    }

    /**
     * Увеличивает счётчик возвращённых посылок покупателя.
     *
     * @param customer покупатель
     * @return обновлённый экземпляр покупателя
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Customer incrementReturned(Customer customer) {
        // Возвращаем из БД покупателя с актуальным количеством возвратов
        return updateStatistic(
                customer,
                "возвратов",
                customerRepository::incrementReturnedCount,
                Customer::getReturnedCount,
                Customer::setReturnedCount
        );
    }
}
