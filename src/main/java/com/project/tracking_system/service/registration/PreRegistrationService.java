package com.project.tracking_system.service.registration;

import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.repository.UserRepository;
import com.project.tracking_system.service.store.StoreService;
import com.project.tracking_system.service.customer.CustomerService;
import com.project.tracking_system.utils.PhoneUtils;
import com.project.tracking_system.utils.TrackNumberUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Сервис обработки предрегистрации отправлений.
 * <p>
 * Сохраняет минимальную информацию о посылке, полученную от пользователя,
 * позволяя позже дополнить данные трека.
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class PreRegistrationService {

    private final TrackParcelRepository trackParcelRepository;
    private final UserRepository userRepository;
    private final StoreService storeService;
    /** Сервис работы с покупателями. */
    private final CustomerService customerService;

    /**
     * Выполняет предрегистрацию номера и сохраняет базовые данные о посылке.
     *
     * @param number  основной трек-номер (может быть пустым)
     * @param storeId идентификатор магазина
     * @param userId  идентификатор пользователя
     * @return созданная сущность {@link TrackParcel}
     */
    @Transactional
    public TrackParcel preRegister(String number,
                                   Long storeId,
                                   Long userId) {
        // Делегируем методу с указанием телефона
        return preRegister(number, storeId, userId, null);
    }

    /**
     * Выполняет предрегистрацию и при наличии телефона
     * привязывает посылку к существующему либо новому покупателю.
     *
     * @param number  основной трек-номер (может быть пустым)
     * @param storeId идентификатор магазина
     * @param userId  идентификатор пользователя
     * @param phone   номер телефона покупателя (может быть {@code null})
     * @return созданная сущность {@link TrackParcel}
     */
    @Transactional
    public TrackParcel preRegister(String number,
                                   Long storeId,
                                   Long userId,
                                   String phone) {
        String normalized = normalizeNumber(number);
        Store store = loadStore(storeId, userId);
        User user = loadUser(userId);

        TrackParcel parcel = buildPreRegisteredParcel(normalized, store, user);

        // Привязываем покупателя, если указан телефон
        if (phone != null && !phone.isBlank()) {
            try {
                Customer customer = customerService.registerOrGetByPhone(phone);
                parcel.setCustomer(customer);
            } catch (ResponseStatusException ex) {
                // Логируем и пробрасываем далее, чтобы клиент получил ответ 400
                log.warn("Не удалось зарегистрировать покупателя по телефону {}: {}",
                        PhoneUtils.maskPhone(phone), ex.getReason());
                throw ex;
            }
        }

        TrackParcel saved = trackParcelRepository.save(parcel);
        // Обновляем статистику покупателя
        customerService.updateStatsOnTrackAdd(saved);
        log.debug("Предрегистрация посылки ID={} выполнена", saved.getId());
        return saved;
    }

    /**
     * Нормализует переданный номер трека.
     * Возвращает {@code null}, если номер отсутствует либо пуст.
     */
    private String normalizeNumber(String number) {
        String normalized = TrackNumberUtils.normalize(number);
        return (normalized == null || normalized.isBlank()) ? null : normalized;
    }

    /**
     * Загружает магазин и проверяет его принадлежность пользователю.
     */
    private Store loadStore(Long storeId, Long userId) {
        return storeService.getStore(storeId, userId);
    }

    /**
     * Получает пользователя по идентификатору.
     */
    private User loadUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
    }

    /**
     * Формирует сущность посылки со статусом предрегистрации.
     */
    private TrackParcel buildPreRegisteredParcel(String number, Store store, User user) {
        TrackParcel parcel = new TrackParcel();
        parcel.setStatus(GlobalStatus.PRE_REGISTERED); // флаг preRegistered выставится автоматически
        parcel.setNumber(number);
        parcel.setStore(store);
        parcel.setUser(user);
        return parcel;
    }
}
