package com.project.tracking_system.service.customer;

import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.project.tracking_system.utils.PhoneUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Сервис для операций над покупателями в отдельной транзакции.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerTransactionalService {

    private final CustomerRepository customerRepository;

    /**
     * Сохранить покупателя в новой транзакции.
     *
     * @param customer покупатель для сохранения
     * @return сохранённый покупатель
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Customer saveCustomer(Customer customer) {
        log.debug("💾 Сохранение покупателя с телефоном {}",
                PhoneUtils.maskPhone(customer.getPhone()));
        return customerRepository.save(customer);
    }

    /**
     * Найти покупателя по телефону в новой транзакции.
     *
     * @param phone номер телефона покупателя
     * @return найденный покупатель или {@link Optional#empty()}
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<Customer> findByPhone(String phone) {
        log.debug("🔍 Поиск покупателя по телефону {}",
                PhoneUtils.maskPhone(phone));
        return customerRepository.findByPhone(phone);
    }
}
