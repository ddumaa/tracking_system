package com.project.tracking_system.service.customer;

import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сервис привязки Telegram чатов к покупателям.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerRegistrationService {

    private final CustomerService customerService;
    private final CustomerRepository customerRepository;

    /**
     * Связать чат Telegram с покупателем по номеру телефона.
     *
     * @param chatId идентификатор чата Telegram
     * @param phone  нормализованный номер телефона
     */
    @Transactional
    public void linkTelegramToCustomer(Long chatId, String phone) {
        Customer customer = customerService.registerOrGetByPhone(phone);
        customer.setTelegramChatId(chatId);
        customerRepository.save(customer);
        log.info("Чат {} привязан к покупателю {}", chatId, customer.getId());
    }
}
