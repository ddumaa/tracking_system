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
     * <p>Атомарно обновляет значение в БД, перечитывает сущность,
     * пересчитывает репутацию и пытается сохранить её атомарно по версии.
     * При неудаче перечитывает сущность ещё раз и сохраняет через репозиторий.
     * Возвращает свежий экземпляр, не изменяя переданный объект.</p>
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
            fresh.recalculateReputation();
            // пытаемся обновить репутацию атомарно по версии
            int reputationUpdated = customerRepository.updateReputation(
                    fresh.getId(),
                    fresh.getVersion(),
                    fresh.getReputation()
            );
            if (reputationUpdated == 0) {
                log.warn("⚠️ Репутация для customerId={} не обновлена, сохраняем через save", customer.getId());
                // перечитываем покупателя, чтобы получить актуальную версию
                fresh = customerRepository.findById(customer.getId())
                        .orElseThrow(() -> new IllegalStateException("Покупатель не найден"));
                fresh.recalculateReputation();
                fresh = customerRepository.save(fresh);
            }
        }
        return fresh;
    }

    /**
     * Увеличивает счётчик отправленных посылок и пересчитывает репутацию.
     * <p>Возвращает нового покупателя из БД; исходный объект не меняется.</p>
     *
     * @param customer покупатель
     * @return обновлённый экземпляр покупателя
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Customer incrementSent(Customer customer) {
        return updateStatistic(
                customer,
                "отправленных",
                customerRepository::incrementSentCount,
                Customer::getSentCount,
                Customer::setSentCount
        );
    }

    /**
     * Увеличивает счётчик забранных посылок с обновлением репутации.
     * <p>Возвращает свежий экземпляр из БД, не изменяя переданный объект.</p>
     *
     * @param customer покупатель
     * @return обновлённый экземпляр покупателя
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Customer incrementPickedUp(Customer customer) {
        return updateStatistic(
                customer,
                "забранных",
                customerRepository::incrementPickedUpCount,
                Customer::getPickedUpCount,
                Customer::setPickedUpCount
        );
    }

    /**
     * Увеличивает счётчик возвращённых посылок и корректирует репутацию.
     * <p>Исходный объект остаётся неизменным, возвращается перечитанный покупатель.</p>
     *
     * @param customer покупатель
     * @return обновлённый экземпляр покупателя
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Customer incrementReturned(Customer customer) {
        return updateStatistic(
                customer,
                "возвратов",
                customerRepository::incrementReturnedCount,
                Customer::getReturnedCount,
                Customer::setReturnedCount
        );
    }
}
