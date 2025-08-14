package com.project.tracking_system.service.registration;

import org.springframework.stereotype.Service;

/**
 * Сервис обработки предрегистрации отправлений.
 * <p>
 * Отвечает за сохранение данных, полученных от пользователя при предрегистрации.
 * Текущая реализация выступает заглушкой и может быть расширена в будущем.
 * </p>
 */
@Service
public class PreRegistrationService {

    /**
     * Выполняет предрегистрацию номера.
     *
     * @param number              основной трек-номер
     * @param registrationSource  источник регистрации
     * @param storeId             идентификатор магазина
     * @param userId              идентификатор пользователя
     */
    public void preRegister(String number,
                            String registrationSource,
                            Long storeId,
                            Long userId) {
        // Заглушка. Реализация зависит от интеграции с внешними системами.
    }
}
