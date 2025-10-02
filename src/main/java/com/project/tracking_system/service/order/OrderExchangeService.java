package com.project.tracking_system.service.order;

import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.OrderReturnRequest;
import com.project.tracking_system.entity.Store;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.User;
import com.project.tracking_system.repository.TrackParcelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Сервис создания обменных посылок.
 * <p>
 * Инкапсулирует логику копирования исходных данных и регистрации
 * обмена в жизненном цикле эпизода, чтобы соблюсти SRP: сервис
 * возвращает готовую сущность, а orchestration происходит во внешнем
 * слое.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderExchangeService {

    private final TrackParcelRepository trackParcelRepository;
    private final OrderEpisodeLifecycleService episodeLifecycleService;

    /**
     * Создаёт посылку-обмен на основании заявки.
     * <p>
     * Метод проверяет корректность входных данных, копирует магазин,
     * пользователя и покупателя из исходной посылки, создаёт новую
     * запись со статусом {@link GlobalStatus#PRE_REGISTERED}, а затем
     * делегирует регистрацию обмена {@link OrderEpisodeLifecycleService}.
     * </p>
     *
     * @param request заявка, по которой запускается обмен
     * @return сохранённая посылка обмена
     */
    @Transactional
    public TrackParcel createExchangeParcel(OrderReturnRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Не передана заявка на обмен");
        }
        TrackParcel originalParcel = Optional.ofNullable(request.getParcel())
                .orElseThrow(() -> new IllegalArgumentException("Заявка не содержит исходную посылку"));

        TrackParcel replacement = buildReplacementFrom(originalParcel);
        episodeLifecycleService.registerExchange(replacement, originalParcel);
        TrackParcel saved = trackParcelRepository.save(replacement);
        log.info("Создана обменная посылка {} для заявки {}", saved.getId(), request.getId());
        return saved;
    }

    /**
     * Подготавливает сущность обменной посылки на основе исходной.
     *
     * @param originalParcel исходная посылка, инициировавшая обмен
     * @return несохранённая сущность обмена
     */
    private TrackParcel buildReplacementFrom(TrackParcel originalParcel) {
        TrackParcel replacement = new TrackParcel();
        replacement.setStatus(GlobalStatus.PRE_REGISTERED);
        replacement.setNumber(null);

        Store store = originalParcel.getStore();
        if (store != null) {
            replacement.setStore(store);
        }
        User user = originalParcel.getUser();
        if (user != null) {
            replacement.setUser(user);
        }
        Customer customer = originalParcel.getCustomer();
        if (customer != null) {
            replacement.setCustomer(customer);
        }

        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        replacement.setLastUpdate(now);
        replacement.setTimestamp(now);
        replacement.setIncludedInStatistics(false);
        return replacement;
    }
}
