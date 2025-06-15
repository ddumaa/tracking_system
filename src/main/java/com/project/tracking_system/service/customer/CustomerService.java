package com.project.tracking_system.service.customer;

import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.repository.CustomerRepository;
import com.project.tracking_system.utils.PhoneUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Сервис управления покупателями.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerService {

    private final CustomerRepository customerRepository;

    /**
     * Зарегистрировать нового покупателя или получить существующего по телефону.
     *
     * @param rawPhone телефон в произвольном формате
     * @return сущность покупателя
     */
    @Transactional
    public Customer registerOrGetByPhone(String rawPhone) {
        String phone = PhoneUtils.normalizePhone(rawPhone);
        Optional<Customer> existing = customerRepository.findByPhone(phone);
        if (existing.isPresent()) {
            return existing.get();
        }
        Customer customer = new Customer();
        customer.setPhone(phone);
        Customer saved = customerRepository.save(customer);
        log.info("Создан новый покупатель с номером {}", phone);
        return saved;
    }

    /**
     * Увеличить счётчик отправленных посылок для покупателя.
     *
     * @param track посылка, связанная с покупателем
     */
    @Transactional
    public void updateStatsOnTrackAdd(TrackParcel track) {
        if (track == null || track.getCustomer() == null) {
            return;
        }
        Customer customer = track.getCustomer();
        customer.setSentCount(customer.getSentCount() + 1);
        customer.recalculateReputation();
        customerRepository.save(customer);
    }

    /**
     * Увеличить счётчик забранных посылок при доставке.
     *
     * @param track посылка, связанная с покупателем
     */
    @Transactional
    public void updateStatsOnTrackDelivered(TrackParcel track) {
        if (track == null || track.getCustomer() == null) {
            return;
        }
        Customer customer = track.getCustomer();
        customer.setPickedUpCount(customer.getPickedUpCount() + 1);
        customer.recalculateReputation();
        customerRepository.save(customer);
    }

    /**
     * Откатить статистику при удалении посылки.
     *
     * @param track удаляемая посылка
     */
    @Transactional
    public void rollbackStatsOnTrackDelete(TrackParcel track) {
        if (track == null || track.getCustomer() == null) {
            return;
        }
        Customer customer = track.getCustomer();
        if (customer.getSentCount() > 0) {
            customer.setSentCount(customer.getSentCount() - 1);
        }
        if (track.getStatus() != null && track.getStatus().isFinal() && customer.getPickedUpCount() > 0) {
            customer.setPickedUpCount(customer.getPickedUpCount() - 1);
        }
        customer.recalculateReputation();
        customerRepository.save(customer);
    }
}
