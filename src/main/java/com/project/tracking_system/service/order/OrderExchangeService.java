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
import java.time.format.DateTimeFormatter;
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

    private static final DateTimeFormatter SERVICE_TRACK_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

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

    /**
     * Отменяет обменную посылку, созданную по заявке, если она ещё не обработана.
     * <p>
     * Метод помечает обменную посылку как отменённую, чтобы она не участвовала в дальнейшем
     * обновлении статусов и была видна менеджеру как закрытая попытка обмена.
     * </p>
     *
     * @param request заявка, для которой требуется отменить обменную посылку
     */
    @Transactional
    public void cancelExchangeParcel(OrderReturnRequest request) {
        if (request == null) {
            return;
        }
        TrackParcel originalParcel = request.getParcel();
        if (originalParcel == null) {
            return;
        }
        TrackParcel replacement = trackParcelRepository.findTopByReplacementOfOrderByTimestampDesc(originalParcel);
        if (replacement == null) {
            return;
        }
        // Назначаем служебный трек до смены статуса, чтобы сохранить инвариант обязательности номера.
        if (replacement.getNumber() == null || replacement.getNumber().isBlank()) {
            replacement.setNumber(generateServiceTrackingNumber(request, replacement));
        }
        replacement.setStatus(GlobalStatus.REGISTRATION_CANCELLED);
        replacement.setLastUpdate(ZonedDateTime.now(ZoneOffset.UTC));
        trackParcelRepository.save(replacement);
        log.debug("Отменена обменная посылка {} для заявки {}", replacement.getId(), request.getId());
    }

    /**
     * Формирует служебный трек-номер для обменной посылки при отмене обмена.
     * <p>
     * Метод перезагружает сущность, если это возможно, чтобы работать с актуальными идентификаторами,
     * и формирует короткий детерминированный код, не зависящий от незаполненных полей заявки.
     * </p>
     *
     * @param request     заявка, по которой отменяется обмен
     * @param replacement обменная посылка, требующая служебного номера
     * @return строка служебного трек-номера
     */
    private String generateServiceTrackingNumber(OrderReturnRequest request, TrackParcel replacement) {
        TrackParcel freshReplacement = reloadReplacementIfPossible(replacement);
        Long replacementId = Optional.ofNullable(freshReplacement)
                .map(TrackParcel::getId)
                .orElse(null);
        Long originalId = Optional.ofNullable(freshReplacement)
                .map(TrackParcel::getReplacementOf)
                .map(TrackParcel::getId)
                .orElseGet(() -> Optional.ofNullable(request)
                        .map(OrderReturnRequest::getParcel)
                        .map(TrackParcel::getId)
                        .orElse(null));
        Long requestId = Optional.ofNullable(request)
                .map(OrderReturnRequest::getId)
                .orElse(null);

        String timestamp = ZonedDateTime.now(ZoneOffset.UTC).format(SERVICE_TRACK_FORMATTER);
        return String.format("SRV-%s-R%s-O%s-Q%s",
                timestamp,
                toSafeSegment(replacementId),
                toSafeSegment(originalId),
                toSafeSegment(requestId));
    }

    /**
     * Перезагружает обменную посылку из БД, если идентификатор известен.
     *
     * @param replacement обменная посылка
     * @return актуальная версия сущности или исходный объект
     */
    private TrackParcel reloadReplacementIfPossible(TrackParcel replacement) {
        if (replacement == null) {
            return null;
        }
        Long id = replacement.getId();
        if (id == null) {
            return replacement;
        }
        Optional<TrackParcel> fresh = trackParcelRepository.findById(id);
        return fresh != null ? fresh.orElse(replacement) : replacement;
    }

    /**
     * Преобразует идентификатор к безопасному сегменту для служебного номера.
     *
     * @param value исходное значение
     * @return числовое значение или «NA», если идентификатор отсутствует
     */
    private String toSafeSegment(Long value) {
        return value != null ? value.toString() : "NA";
    }
}
