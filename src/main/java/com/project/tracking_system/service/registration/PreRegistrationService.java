package com.project.tracking_system.service.registration;

import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.entity.OrderEpisode;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.repository.UserRepository;
import com.project.tracking_system.service.store.StoreService;
import com.project.tracking_system.service.customer.CustomerService;
import com.project.tracking_system.service.track.PreRegistrationMeta;
import com.project.tracking_system.service.track.TrackTypeDetector;
import com.project.tracking_system.entity.PostalServiceType;
import com.project.tracking_system.service.order.OrderEpisodeLifecycleService;
import com.project.tracking_system.utils.PhoneUtils;
import com.project.tracking_system.utils.TrackNumberUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
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
    /** Определение почтового сервиса по номеру трека. */
    private final TrackTypeDetector trackTypeDetector;
    /** Управление жизненным циклом эпизодов заказа. */
    private final OrderEpisodeLifecycleService orderEpisodeLifecycleService;

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

        // Определяем тип почтового сервиса до создания эпизода, чтобы не порождать лишних записей
        PreRegistrationMeta meta = new PreRegistrationMeta(normalized, storeId, phone);
        PostalServiceType type = trackTypeDetector.detect(meta);
        if (normalized != null && type == PostalServiceType.UNKNOWN) {
            log.warn("Не удалось определить сервис для трека {}", meta.getTrackNumber());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось определить сервис для трека");
        }

        Customer customer = resolveCustomerByPhone(phone);
        OrderEpisode episode = orderEpisodeLifecycleService.createEpisode(store, customer);

        TrackParcel parcel = buildPreRegisteredParcel(normalized, store, user, customer, episode);

        TrackParcel saved = trackParcelRepository.save(parcel);
        orderEpisodeLifecycleService.syncEpisodeCustomer(saved);
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
    private TrackParcel buildPreRegisteredParcel(String number,
                                                 Store store,
                                                 User user,
                                                 Customer customer,
                                                 OrderEpisode episode) {
        TrackParcel parcel = new TrackParcel();
        parcel.setStatus(GlobalStatus.PRE_REGISTERED); // флаг preRegistered выставится автоматически
        parcel.setNumber(number);
        parcel.setStore(store);
        parcel.setUser(user);
        parcel.setCustomer(customer);
        parcel.setEpisode(episode);
        parcel.setExchange(false);
        parcel.setReplacementOf(null);
        return parcel;
    }

    /**
     * Находит либо создаёт покупателя по номеру телефона.
     *
     * @param phone телефон в произвольном формате
     * @return покупатель либо {@code null}, если телефон не указан
     */
    private Customer resolveCustomerByPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        try {
            return customerService.registerOrGetByPhone(phone);
        } catch (ResponseStatusException ex) {
            log.warn("Не удалось зарегистрировать покупателя по телефону {}: {}",
                    PhoneUtils.maskPhone(phone), ex.getReason());
            throw ex;
        }
    }
}
