package com.project.tracking_system.service.customer;

import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.entity.NameSource;
import com.project.tracking_system.entity.Role;
import com.project.tracking_system.utils.PhoneUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сервис управления ФИО покупателя, полученного от магазина.
 * <p>
 * Обновляет профиль покупателя и фиксирует событие {@code PENDING},
 * которое может быть подтверждено пользователем в дальнейшем.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerNameService {

    private final CustomerService customerService;

    /**
     * Обновить или добавить ФИО покупателя на основании данных из магазина.
     * <p>
     * При отсутствии покупателя он создаётся автоматически. Для успешных
     * обновлений в журнал записывается событие со статусом {@code PENDING}.
     * </p>
     *
     * @param rawPhone  телефон покупателя в произвольном формате
     * @param fullName  предложенное магазином ФИО
     */
    @Transactional
    public void upsertFromStore(String rawPhone, String fullName) {
        if (rawPhone == null || rawPhone.isBlank() || fullName == null || fullName.isBlank()) {
            return;
        }
        try {
            String phone = PhoneUtils.normalizePhone(rawPhone);
            Customer customer = customerService.registerOrGetByPhone(phone);
            customerService.updateCustomerName(customer, fullName, NameSource.MERCHANT_PROVIDED, Role.ROLE_USER);
        } catch (Exception e) {
            log.warn("Не удалось обновить ФИО для телефона {}: {}", rawPhone, e.getMessage());
        }
    }
}
