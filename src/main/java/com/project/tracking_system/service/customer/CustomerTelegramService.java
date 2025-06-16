package com.project.tracking_system.service.customer;

import com.project.tracking_system.entity.BuyerReputation;
import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.repository.CustomerRepository;
import com.project.tracking_system.utils.PhoneUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сервис привязки Telegram-чатов к покупателям.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerTelegramService {

    private final CustomerRepository customerRepository;

    /**
     * Привязать чат Telegram к покупателю по номеру телефона.
     * <p>
     * Номер телефона нормализуется до формата 375XXXXXXXXX. Если покупатель с
     * таким номером существует и не имеет привязанного чата, чат будет сохранён.
     * При отсутствии покупателя создаётся новая запись с нейтральной репутацией.
     * Повторная привязка уже связанного покупателя игнорируется.
     * </p>
     *
     * @param phone  номер телефона в произвольном формате
     * @param chatId идентификатор чата Telegram
     * @return сущность покупателя после обновления
     */
    @Transactional
    public Customer linkTelegramToCustomer(String phone, Long chatId) {
        String normalized = PhoneUtils.normalizePhone(phone);
        log.info("🔗 Попытка привязки телефона {} к чату {}", normalized, chatId);

        Customer customer = customerRepository.findByPhone(normalized).orElse(null);

        if (customer != null) {
            // Проверяем, не привязан ли уже чат к покупателю
            if (customer.getTelegramChatId() != null) {
                log.warn("⚠️ Покупатель {} уже привязан к чату {}", customer.getId(), customer.getTelegramChatId());
                return customer;
            }
            customer.setTelegramChatId(chatId);
            Customer saved = customerRepository.save(customer);
            log.info("✅ Чат {} привязан к покупателю {}", chatId, saved.getId());
            return saved;
        }

        // Создаём нового покупателя с начальными значениями
        Customer newCustomer = new Customer();
        newCustomer.setPhone(normalized);
        newCustomer.setTelegramChatId(chatId);
        newCustomer.setSentCount(0);
        newCustomer.setPickedUpCount(0);
        newCustomer.setReputation(BuyerReputation.NEUTRAL);

        Customer saved = customerRepository.save(newCustomer);
        log.info("🆕 Создан покупатель {} и привязан чат {}", saved.getId(), chatId);
        return saved;
    }
}
