package com.project.tracking_system.service.customer;

import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.repository.CustomerRepository;
import com.project.tracking_system.repository.TrackParcelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Сервис пересчёта статистики покупателей.
 * <p>
 * Используется для исправления случаев, когда
 * суммарное число полученных и возвращённых посылок
 * превышает количество отправленных.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerStatisticsFixService {

    private final CustomerRepository customerRepository;
    private final TrackParcelRepository trackParcelRepository;

    /**
     * Пересчитать счётчик отправленных посылок для всех покупателей.
     * Если отправлено меньше, чем сумма полученных и возвращённых,
     * значение отправленных корректируется.
     */
    @Transactional
    public void recalculateSentCount() {
        List<Customer> customers = customerRepository.findAll();
        for (Customer customer : customers) {
            int received = trackParcelRepository.countByCustomerIdAndStatus(customer.getId(), GlobalStatus.DELIVERED);
            int returned = trackParcelRepository.countByCustomerIdAndStatus(customer.getId(), GlobalStatus.RETURNED);
            int total = received + returned;
            if (total > customer.getSentCount()) {
                customer.setSentCount(total);
                customerRepository.save(customer);
                log.info("\uD83D\uDCC8 Обновлён sentCount для customerId={}: {}", customer.getId(), total);
            }
        }
    }
}
