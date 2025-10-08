package com.project.tracking_system.service.analytics;

import com.project.tracking_system.dto.DeliveryDates;
import com.project.tracking_system.dto.TrackInfoDTO;
import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.entity.*;
import com.project.tracking_system.repository.DeliveryHistoryRepository;
import com.project.tracking_system.repository.StoreAnalyticsRepository;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.repository.PostalServiceStatisticsRepository;
import com.project.tracking_system.repository.StoreDailyStatisticsRepository;
import com.project.tracking_system.repository.PostalServiceDailyStatisticsRepository;
import com.project.tracking_system.service.track.StatusTrackService;
import com.project.tracking_system.service.track.TypeDefinitionTrackPostService;
import com.project.tracking_system.service.customer.CustomerService;
import com.project.tracking_system.service.customer.CustomerStatsService;
import com.project.tracking_system.service.telegram.TelegramNotificationService;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.model.subscription.FeatureKey;
import com.project.tracking_system.repository.CustomerNotificationLogRepository;
import com.project.tracking_system.entity.CustomerNotificationLog;
import com.project.tracking_system.entity.NotificationType;
import com.project.tracking_system.service.order.OrderEpisodeLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import com.project.tracking_system.utils.DateParserUtils;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * @author Dmitriy Anisimov
 * @date 15.03.2025
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class DeliveryHistoryService {

    private final StoreAnalyticsRepository storeAnalyticsRepository;
    private final DeliveryHistoryRepository deliveryHistoryRepository;
    private final TypeDefinitionTrackPostService typeDefinitionTrackPostService;
    private final StatusTrackService statusTrackService;
    private final TrackParcelRepository trackParcelRepository;
    private final PostalServiceStatisticsRepository postalServiceStatisticsRepository;
    private final StoreDailyStatisticsRepository storeDailyStatisticsRepository;
    private final PostalServiceDailyStatisticsRepository postalServiceDailyStatisticsRepository;
    private final CustomerService customerService;
    private final CustomerStatsService customerStatsService;
    private final TelegramNotificationService telegramNotificationService;
    private final CustomerNotificationLogRepository customerNotificationLogRepository;
    private final SubscriptionService subscriptionService;
    private final DeliveryMetricsRollbackService deliveryMetricsRollbackService;
    private final OrderEpisodeLifecycleService orderEpisodeLifecycleService;


    /**
     * Обновляет или создаёт запись {@link DeliveryHistory}, когда меняется статус посылки.
     * <p>
     * Полная история отслеживания из {@link TrackInfoListDTO} анализируется для извлечения
     * всех значимых дат (отправки, прибытия, получения, возврата).
     * Если новый статус считается финальным, метод передаёт управление в
     * {@link #registerFinalStatus(DeliveryHistory, GlobalStatus)}, чтобы обновить
     * накопительную статистику.
     * </p>
     * <p>
     * При первом сохранении трека сразу в финальном статусе уведомление в Telegram
     * не отправляется, чтобы избежать ложных оповещений без фактического изменения
     * состояния.
     * </p>
     *
     * <p><strong>Безопасность:</strong> не логируем персональные данные или токены.</p>
     *
     * @param trackParcel       посылка, у которой изменился статус
     * @param oldStatus         предыдущий статус посылки
     * @param newStatus         новый статус посылки
     * @param trackInfoListDTO  список событий трекинга, полученных от службы отслеживания
     */
    @Transactional
    public void updateDeliveryHistory(TrackParcel trackParcel, GlobalStatus oldStatus, GlobalStatus newStatus, TrackInfoListDTO trackInfoListDTO) {
        // Для PRE_REGISTERED используем debug, чтобы не засорять основное логирование
        if (newStatus == GlobalStatus.PRE_REGISTERED) {
            log.debug("Начало обновления истории доставки (PRE_REGISTERED) для трека {}", trackParcel.getNumber());
        } else {
            log.info("Начало обновления истории доставки для трека {}", trackParcel.getNumber());
        }

        // Получаем историю или создаём новую
        DeliveryHistory history = deliveryHistoryRepository.findByTrackParcelId(trackParcel.getId())
                .orElseGet(() -> {
                    log.info("Создаём новую запись истории для трека {}", trackParcel.getNumber());

                    // Определяем почтовую службу
                    PostalServiceType serviceType = typeDefinitionTrackPostService.detectPostalService(trackParcel.getNumber());
                    return new DeliveryHistory(trackParcel, trackParcel.getStore(), serviceType, null, null, null);
                });

        orderEpisodeLifecycleService.ensureEpisode(trackParcel);

        //  Если статус НЕ изменился — ничего не делаем
        if (oldStatus == null || !newStatus.equals(oldStatus)) {
            if (newStatus == GlobalStatus.PRE_REGISTERED) {
                log.debug("Новый трек или статус PRE_REGISTERED, обновляем историю...");
            } else {
                log.info("Новый трек или статус изменился, обновляем историю...");
            }
        } else {
            log.debug("Статус не изменился, обновление истории не требуется для {}", trackParcel.getNumber());
            return;
        }

        if (oldStatus != null && oldStatus.isFinal() && (newStatus == null || !newStatus.isFinal())) {
            deliveryMetricsRollbackService.rollbackFinalStatusMetrics(history, trackParcel, oldStatus);
            orderEpisodeLifecycleService.reopenEpisode(trackParcel);
        }

        //  Определяем часовой пояс пользователя и извлекаем даты из трека
        ZoneId userZone = ZoneId.of(trackParcel.getUser().getTimeZone());
        DeliveryDates deliveryDates = extractDatesFromTrackInfo(trackParcel, trackInfoListDTO, userZone);

        // Устанавливаем дату отправки, если она доступна
        ZonedDateTime sendDate = deliveryDates.sendDate();
        setHistoryDate("Дата отправки", history.getSendDate(), sendDate, history::setSendDate);

        if (newStatus != GlobalStatus.PRE_REGISTERED) {
            if (newStatus == GlobalStatus.DELIVERED) {
                setHistoryDate("Дата получения", history.getReceivedDate(), deliveryDates.receivedDate(), history::setReceivedDate);
            }

            if (newStatus == GlobalStatus.RETURNED) {
                setHistoryDate("Дата возврата", history.getReturnedDate(), deliveryDates.returnedDate(), history::setReturnedDate);
            }

            if (history.getArrivedDate() == null && deliveryDates.arrivedDate() != null) {
                // Фиксируем дату прибытия на пункт выдачи, даже если текущий статус уже финальный
                setHistoryDate(
                        "Дата прибытия на пункт выдачи",
                        history.getArrivedDate(),
                        deliveryDates.arrivedDate(),
                        history::setArrivedDate
                );
            }

            // Считаем и обновляем среднее время доставки только для финальных статусов
            if (newStatus.isFinal()) {
                // Предыдущий покупатель неизвестен в рамках обновления истории
                registerFinalStatus(history, newStatus, null);
            }
        }

        // Сохраняем историю, если что-то изменилось
        deliveryHistoryRepository.save(history);
        if (newStatus == GlobalStatus.PRE_REGISTERED) {
            log.debug("История доставки обновлена (PRE_REGISTERED): {}", trackParcel.getNumber());
        } else {
            log.info("История доставки обновлена: {}", trackParcel.getNumber());
        }

        // Отправляем уведомление в Telegram при выполнении условий
        // Уведомления стартуют только после выхода из предрегистрации
        boolean initialFinalStatus = isInitialFinalStatus(oldStatus, newStatus);
        if (initialFinalStatus) {
            log.debug(
                    "Пропускаем уведомление для трека {}: первый статус уже финальный ({})",
                    trackParcel.getNumber(),
                    newStatus
            );
        }

        if (!initialFinalStatus && newStatus != GlobalStatus.PRE_REGISTERED && shouldNotifyCustomer(trackParcel, newStatus)) {
            boolean notificationSent = telegramNotificationService.sendStatusUpdate(trackParcel, newStatus);
            if (notificationSent) {
                log.info("✅ Уведомление о статусе {} отправлено для трека {}", newStatus, trackParcel.getNumber());
                saveNotificationLog(trackParcel, newStatus);
            } else {
                log.debug("Уведомление о статусе {} не было отправлено для трека {}", newStatus, trackParcel.getNumber());
            }
        }
    }

    /**
     * Извлекает ключевые даты из списка статусов трекинга.
     *
     * @param trackParcel    посылка, для которой анализируем историю
     * @param trackInfoListDTO список событий трекинга
     * @param userZone       часовой пояс пользователя
     * @return набор извлечённых дат
     */
    private DeliveryDates extractDatesFromTrackInfo(TrackParcel trackParcel, TrackInfoListDTO trackInfoListDTO, ZoneId userZone) {
        List<TrackInfoDTO> trackInfoList = trackInfoListDTO.getList();

        if (trackInfoList.isEmpty()) {
            log.warn("⚠ Пустой список статусов для трека {}", trackParcel.getNumber());
            return new DeliveryDates(null, null, null);
        }

        PostalServiceType serviceType = typeDefinitionTrackPostService.detectPostalService(trackParcel.getNumber());
        ZonedDateTime sendDate = null, receivedDate = null, returnedDate = null, arrivedDate = null;

        //  Определяем дату отправки/регистрации
        if (serviceType == PostalServiceType.BELPOST) {
            // Для Белпочты берём последнюю запись
            sendDate = DateParserUtils.parse(trackInfoList.get(trackInfoList.size() - 1).getTimex(), userZone);
        } else if (serviceType == PostalServiceType.EVROPOST) {
            // Для Европочты дата отправки берётся из предпоследней записи,
            // однако если запись одна — это лишь регистрация, отправка не фиксируется
            if (trackInfoList.size() > 1) {
                sendDate = DateParserUtils.parse(
                        trackInfoList.get(trackInfoList.size() - 2).getTimex(),
                        userZone
                );
            } else {
                log.info(
                        "Европочта: единственная запись считается регистрацией, дата отправки не определена. Трек: {}",
                        trackParcel.getNumber()
                );
            }
        }

        // Определяем дату получения или возврата
        TrackInfoDTO latestStatus = trackInfoList.get(0);
        GlobalStatus finalStatus = statusTrackService.setStatus(List.of(latestStatus));

        if (finalStatus == GlobalStatus.DELIVERED) {
            receivedDate = DateParserUtils.parse(latestStatus.getTimex(), userZone);
        } else if (finalStatus == GlobalStatus.RETURNED) {
            returnedDate = DateParserUtils.parse(latestStatus.getTimex(), userZone);
        }

        // Поиск первого (по времени) статуса WAITING_FOR_CUSTOMER
        for (int i = trackInfoList.size() - 1; i >= 0; i--) {
            TrackInfoDTO info = trackInfoList.get(i);
            GlobalStatus status = statusTrackService.setStatus(List.of(info));
            if (status == GlobalStatus.WAITING_FOR_CUSTOMER) {
                arrivedDate = DateParserUtils.parse(info.getTimex(), userZone);
                log.info("Извлечена дата прибытия на пункт выдачи: {}", arrivedDate);
                break;
            }
        }

        return new DeliveryDates(sendDate, receivedDate, returnedDate, arrivedDate);
    }

    /**
     * Обрабатывает финальный статус доставки (DELIVERED или RETURNED) и обновляет накопительную статистику магазина.
     *
     * <p>Метод выполняет инкремент счётчиков доставленных или возвращённых посылок, а также
     * рассчитывает и накапливает общее время доставки и забора. Выполняется только один раз
     * для каждой посылки, после чего флаг {@code includedInStatistics} устанавливается в {@code true}.</p>
     *
     * Условия для учёта:
     * - Статус должен быть финальным (DELIVERED или RETURNED)
     * - Все необходимые даты (отправки, получения или возврата) должны быть заполнены
     * - Посылка не должна быть уже учтена в статистике
     *
     * @param history история доставки, содержащая даты и связанные данные
     * @param status  новый статус, достигнутый посылкой
     *
     * <p><strong>Безопасность:</strong> избегаем логирования персональных данных или токенов.</p>
     */
    @Transactional
    public void registerFinalStatus(DeliveryHistory history, GlobalStatus status) {
        // Дефолтная обёртка: предыдущий покупатель не передан
        registerFinalStatus(history, status, null);
    }

    /**
     * Учесть финальный статус посылки и при необходимости перерасчитать статистику покупателя.
     *
     * <p>Если посылка уже была включена в аналитику и клиент изменился, переданный
     * {@code previousCustomer} позволяет откатить показатели старого клиента и
     * начислить их новому. Без этого параметра изменение клиента не будет обнаружено.</p>
     *
     * @param history          история доставки, содержащая даты и связанные данные
     * @param status           новый финальный статус (DELIVERED или RETURNED)
     * @param previousCustomer покупатель, закреплённый за посылкой до изменения; {@code null}, если не применимо
     */
    @Transactional
    public void registerFinalStatus(DeliveryHistory history, GlobalStatus status, Customer previousCustomer) {
        // Предварительная регистрация не участвует в статистике
        if (status == GlobalStatus.PRE_REGISTERED) {
            log.debug("Статус PRE_REGISTERED не влияет на статистику");
            return;
        }

        TrackParcel trackParcel = history.getTrackParcel();

        // Пропускаем обновление аналитики для неизвестной почтовой службы
        if (history.getPostalService() == PostalServiceType.UNKNOWN) {
            log.warn("⛔ Skipping analytics update for UNKNOWN service: {}", trackParcel.getNumber());
            return;
        }

        // Флаг показывает, была ли посылка уже включена в агрегированную статистику
        boolean alreadyRegistered = trackParcel.isIncludedInStatistics();

        Store store = history.getStore();
        // Получаем статистику магазина или создаём новую запись
        StoreStatistics stats = storeAnalyticsRepository.findByStoreId(store.getId())
                .orElseGet(() -> {
                    StoreStatistics s = new StoreStatistics();
                    s.setStore(store);
                    // Сохраняем запись, чтобы атомарные инкременты прошли успешно
                    return storeAnalyticsRepository.save(s);
                });
        PostalServiceStatistics psStats = getOrCreateServiceStats(store, history.getPostalService());

        BigDecimal deliveryDays = null;
        BigDecimal pickupDays = null;
        LocalDate eventDate = null;

        if (status == GlobalStatus.DELIVERED) {
            if (history.getReceivedDate() != null) {
                // День получения используется как ключ для ежедневной статистики
                eventDate = history.getReceivedDate().toLocalDate();
            }

            if (history.getSendDate() != null && history.getArrivedDate() != null) {
                // Считаем время доставки от отправки до прибытия
                deliveryDays = BigDecimal.valueOf(
                        Duration.between(history.getSendDate(), history.getArrivedDate()).toHours() / 24.0);
            }

            if (history.getArrivedDate() != null && history.getReceivedDate() != null) {
                // Время ожидания клиента на пункте выдачи
                pickupDays = BigDecimal.valueOf(
                        Duration.between(history.getArrivedDate(), history.getReceivedDate()).toDays());
            }

            if (!alreadyRegistered) {
                int stUpd = storeAnalyticsRepository.incrementDelivered(
                        store.getId(),
                        1,
                        deliveryDays != null ? deliveryDays : BigDecimal.ZERO,
                        pickupDays != null ? pickupDays : BigDecimal.ZERO);
                if (stUpd == 0) {
                    stats.setTotalDelivered(stats.getTotalDelivered() + 1);
                    if (deliveryDays != null) {
                        stats.setSumDeliveryDays(stats.getSumDeliveryDays().add(deliveryDays));
                    }
                    if (pickupDays != null) {
                        stats.setSumPickupDays(stats.getSumPickupDays().add(pickupDays));
                    }
                    stats.setUpdatedAt(ZonedDateTime.now());
                    storeAnalyticsRepository.save(stats);
                }

                int psUpd = postalServiceStatisticsRepository.incrementDelivered(
                        store.getId(),
                        history.getPostalService(),
                        1,
                        deliveryDays != null ? deliveryDays : BigDecimal.ZERO,
                        pickupDays != null ? pickupDays : BigDecimal.ZERO);
                if (psUpd == 0) {
                    psStats.setTotalDelivered(psStats.getTotalDelivered() + 1);
                    if (deliveryDays != null) {
                        psStats.setSumDeliveryDays(psStats.getSumDeliveryDays().add(deliveryDays));
                    }
                    if (pickupDays != null) {
                        psStats.setSumPickupDays(psStats.getSumPickupDays().add(pickupDays));
                    }
                    psStats.setUpdatedAt(ZonedDateTime.now());
                    postalServiceStatisticsRepository.save(psStats);
                }
            }

        } else if (status == GlobalStatus.RETURNED) {
            if (history.getReturnedDate() != null) {
                eventDate = history.getReturnedDate().toLocalDate();
            }

            if (history.getSendDate() != null && history.getArrivedDate() != null) {
                // Считаем время доставки от отправки до прибытия
                deliveryDays = BigDecimal.valueOf(
                        Duration.between(history.getSendDate(), history.getArrivedDate()).toHours() / 24.0);
            }

            if (history.getArrivedDate() != null && history.getReturnedDate() != null) {
                // Возврат забран: считаем время от прибытия до возврата
                pickupDays = BigDecimal.valueOf(
                        Duration.between(history.getArrivedDate(), history.getReturnedDate()).toDays());
            }

            if (!alreadyRegistered) {
                int stUpd = storeAnalyticsRepository.incrementReturned(
                        store.getId(),
                        1,
                        deliveryDays != null ? deliveryDays : BigDecimal.ZERO,
                        BigDecimal.ZERO);
                if (stUpd == 0) {
                    stats.setTotalReturned(stats.getTotalReturned() + 1);
                    if (deliveryDays != null) {
                        stats.setSumDeliveryDays(stats.getSumDeliveryDays().add(deliveryDays));
                    }
                    stats.setUpdatedAt(ZonedDateTime.now());
                    storeAnalyticsRepository.save(stats);
                }

                int psUpd = postalServiceStatisticsRepository.incrementReturned(
                        store.getId(),
                        history.getPostalService(),
                        1,
                        deliveryDays != null ? deliveryDays : BigDecimal.ZERO,
                        BigDecimal.ZERO);
                if (psUpd == 0) {
                    psStats.setTotalReturned(psStats.getTotalReturned() + 1);
                    if (deliveryDays != null) {
                        psStats.setSumDeliveryDays(psStats.getSumDeliveryDays().add(deliveryDays));
                    }
                    psStats.setUpdatedAt(ZonedDateTime.now());
                    postalServiceStatisticsRepository.save(psStats);
                }
            }
        }

        if (!alreadyRegistered && eventDate != null) {
            updateDailyStats(store, history.getPostalService(), eventDate, status, deliveryDays, pickupDays);
        }

        // Текущий покупатель на треке (может быть новым после редактирования)
        Customer customer = trackParcel.getCustomer();

        // Проверяем, был ли трек ранее учтён и изменился ли покупатель
        boolean customerChanged = alreadyRegistered
                && previousCustomer != null
                && !Objects.equals(previousCustomer, customer);

        if (alreadyRegistered && customerChanged && previousCustomer != null) {
            // Если покупатель изменился, откатываем его предыдущую статистику
            TrackParcel oldParcel = new TrackParcel();
            oldParcel.setId(trackParcel.getId());
            oldParcel.setStatus(status);
            oldParcel.setCustomer(previousCustomer);
            customerService.rollbackStatsOnTrackDelete(oldParcel);
        }

        if (status == GlobalStatus.DELIVERED && customer != null && (!alreadyRegistered || customerChanged)) {
            // Инкрементируем показатели получения для нового покупателя
            customer = customerStatsService.incrementPickedUp(customer);
            trackParcel.setCustomer(customer);
            orderEpisodeLifecycleService.syncEpisodeCustomer(trackParcel);
        } else if (status == GlobalStatus.RETURNED && customer != null && (!alreadyRegistered || customerChanged)) {
            // Инкрементируем показатели возвратов для нового покупателя
            customer = customerStatsService.incrementReturned(customer);
            trackParcel.setCustomer(customer);
            orderEpisodeLifecycleService.syncEpisodeCustomer(trackParcel);
        }

        // Устанавливаем флаг только при первом учёте
        if (!alreadyRegistered) {
            trackParcel.setIncludedInStatistics(true);
            trackParcelRepository.save(trackParcel);
        }

        orderEpisodeLifecycleService.registerFinalOutcome(trackParcel, status);

        log.info("📊 Обновлена накопительная статистика по магазину: {}", store.getName());
    }

    /**
     * Проверить, имеет ли посылка финальный статус.
     *
     * @param parcelId идентификатор посылки
     * @return {@code true}, если статус финальный
     */
    @Transactional(readOnly = true)
    public boolean hasFinalStatus(Long parcelId) {
        return trackParcelRepository.findById(parcelId)
                .map(p -> p.getStatus().isFinal())
                .orElse(false);
    }

    /**
     * Зарегистрировать финальный статус для посылки по её идентификатору.
     * <p>
     * Если история доставки для посылки отсутствует, метод корректно завершится,
     * не выбрасывая исключение. Это позволяет безопасно вызывать метод даже
     * сразу после создания новой посылки без полной истории.
     * </p>
     *
     * @param parcelId идентификатор посылки
     */
    @Transactional
    public void registerFinalStatus(Long parcelId) {
        deliveryHistoryRepository.findByTrackParcelId(parcelId)
                .ifPresentOrElse(
                        history -> registerFinalStatus(history, history.getTrackParcel().getStatus(), null),
                        () -> log.debug("История доставки для посылки {} не найдена", parcelId)
                );
    }

    /**
     * Обновляет ежедневную статистику как для магазина, так и для почтовой службы.
     *
     * @param store        магазин, для которого ведётся статистика
     * @param serviceType  тип почтовой службы
     * @param eventDate    дата события доставки
     * @param status       финальный статус посылки
     * @param deliveryDays время доставки в днях
     * @param pickupDays   время выдачи посылки в днях
     */
    private void updateDailyStats(Store store,
                                  PostalServiceType serviceType,
                                  LocalDate eventDate,
                                  GlobalStatus status,
                                  BigDecimal deliveryDays,
                                  BigDecimal pickupDays) {
        // Пропускаем обновление статистики для неизвестной почтовой службы
        if (serviceType == PostalServiceType.UNKNOWN) {
            log.warn("⛔ Skipping daily stats update for UNKNOWN service: {}", store.getId());
            return;
        }
        // Сначала пытаемся атомарно увеличить счётчики
        if (status == GlobalStatus.DELIVERED) {
            int sdUpdated = storeDailyStatisticsRepository.incrementDelivered(
                    store.getId(),
                    eventDate,
                    1,
                    deliveryDays != null ? deliveryDays : BigDecimal.ZERO,
                    pickupDays != null ? pickupDays : BigDecimal.ZERO);
            if (sdUpdated == 0) {
                StoreDailyStatistics daily = storeDailyStatisticsRepository
                        .findByStoreIdAndDate(store.getId(), eventDate)
                        .orElseGet(() -> {
                            StoreDailyStatistics d = new StoreDailyStatistics();
                            d.setStore(store);
                            d.setDate(eventDate);
                            return d;
                        });
                daily.setDelivered(daily.getDelivered() + 1);
                if (deliveryDays != null) {
                    daily.setSumDeliveryDays(daily.getSumDeliveryDays().add(deliveryDays));
                }
                if (pickupDays != null) {
                    daily.setSumPickupDays(daily.getSumPickupDays().add(pickupDays));
                }
                daily.setUpdatedAt(ZonedDateTime.now());
                storeDailyStatisticsRepository.save(daily);
            }

            int psdUpdated = postalServiceDailyStatisticsRepository.incrementDelivered(
                    store.getId(),
                    serviceType,
                    eventDate,
                    1,
                    deliveryDays != null ? deliveryDays : BigDecimal.ZERO,
                    pickupDays != null ? pickupDays : BigDecimal.ZERO);
            if (psdUpdated == 0) {
                PostalServiceDailyStatistics psDaily = postalServiceDailyStatisticsRepository
                        .findByStoreIdAndPostalServiceTypeAndDate(store.getId(), serviceType, eventDate)
                        .orElseGet(() -> {
                            PostalServiceDailyStatistics d = new PostalServiceDailyStatistics();
                            d.setStore(store);
                            d.setPostalServiceType(serviceType);
                            d.setDate(eventDate);
                            return d;
                        });
                psDaily.setDelivered(psDaily.getDelivered() + 1);
                if (deliveryDays != null) {
                    psDaily.setSumDeliveryDays(psDaily.getSumDeliveryDays().add(deliveryDays));
                }
                if (pickupDays != null) {
                    psDaily.setSumPickupDays(psDaily.getSumPickupDays().add(pickupDays));
                }
                psDaily.setUpdatedAt(Instant.now());
                postalServiceDailyStatisticsRepository.save(psDaily);
            }

        } else if (status == GlobalStatus.RETURNED) {
            int sdUpdated = storeDailyStatisticsRepository.incrementReturned(
                    store.getId(),
                    eventDate,
                    1,
                    deliveryDays != null ? deliveryDays : BigDecimal.ZERO,
                    BigDecimal.ZERO);
            if (sdUpdated == 0) {
                StoreDailyStatistics daily = storeDailyStatisticsRepository
                        .findByStoreIdAndDate(store.getId(), eventDate)
                        .orElseGet(() -> {
                            StoreDailyStatistics d = new StoreDailyStatistics();
                            d.setStore(store);
                            d.setDate(eventDate);
                            return d;
                        });
                daily.setReturned(daily.getReturned() + 1);
                if (deliveryDays != null) {
                    daily.setSumDeliveryDays(daily.getSumDeliveryDays().add(deliveryDays));
                }
                daily.setUpdatedAt(ZonedDateTime.now());
                storeDailyStatisticsRepository.save(daily);
            }

            int psdUpdated = postalServiceDailyStatisticsRepository.incrementReturned(
                    store.getId(),
                    serviceType,
                    eventDate,
                    1,
                    deliveryDays != null ? deliveryDays : BigDecimal.ZERO,
                    BigDecimal.ZERO);
            if (psdUpdated == 0) {
                PostalServiceDailyStatistics psDaily = postalServiceDailyStatisticsRepository
                        .findByStoreIdAndPostalServiceTypeAndDate(store.getId(), serviceType, eventDate)
                        .orElseGet(() -> {
                            PostalServiceDailyStatistics d = new PostalServiceDailyStatistics();
                            d.setStore(store);
                            d.setPostalServiceType(serviceType);
                            d.setDate(eventDate);
                            return d;
                        });
                psDaily.setReturned(psDaily.getReturned() + 1);
                if (deliveryDays != null) {
                    psDaily.setSumDeliveryDays(psDaily.getSumDeliveryDays().add(deliveryDays));
                }
                psDaily.setUpdatedAt(Instant.now());
                postalServiceDailyStatisticsRepository.save(psDaily);
            }
        }
    }

    private PostalServiceStatistics getOrCreateServiceStats(Store store, PostalServiceType serviceType) {
        return postalServiceStatisticsRepository
                .findByStoreIdAndPostalServiceType(store.getId(), serviceType)
                .orElseGet(() -> {
                    PostalServiceStatistics stats = new PostalServiceStatistics();
                    stats.setStore(store);
                    stats.setPostalServiceType(serviceType);
                    return stats;
                });
    }

    /**
     * Обрабатывает удаление посылки и корректирует статистику, если посылка ещё не была учтена.
     *
     * <p>Если посылка не имела финального статуса и ещё не была включена в расчёты,
     * метод уменьшает значение {@code totalSent} в {@code StoreStatistics} на 1.</p>
     *
     * Это позволяет избежать искажения статистики при удалении черновиков и неактуальных треков.
     * Если статистика магазина отсутствует, метод аккуратно завершает работу, чтобы не блокировать удаление.
     *
     * @param parcel объект удаляемой посылки
     */
    @Transactional
    public void handleTrackParcelBeforeDelete(TrackParcel parcel) {
        log.info("Начало обработки удаления трека {}", parcel.getNumber());

        if (!parcel.getStatus().isFinal()) {
            customerService.rollbackStatsOnTrackDelete(parcel);
        }

        if (parcel.isIncludedInStatistics()) {
            log.debug("Удаляется уже учтённая в статистике посылка {}, статистику не трогаем", parcel.getNumber());
            return;
        }

        Store store = parcel.getStore();
        if (store == null) {
            log.warn("❌ У посылки {} отсутствует привязанный магазин. Статистика не будет скорректирована", parcel.getNumber());
            return;
        }

        Optional<StoreStatistics> storeStatsOpt = storeAnalyticsRepository.findByStoreId(store.getId());
        if (storeStatsOpt.isEmpty()) {
            log.warn("❌ Для магазина ID={} не найдена статистика. Пропускаем корректировку при удалении посылки {}", store.getId(), parcel.getNumber());
            return;
        }

        StoreStatistics stats = storeStatsOpt.get();
        // История или номер могут отсутствовать у черновых треков,
        // поэтому определяем службу максимально безопасно.
        PostalServiceType serviceType;
        if (parcel.getDeliveryHistory() != null) {
            serviceType = parcel.getDeliveryHistory().getPostalService();
        } else if (parcel.getNumber() != null) {
            serviceType = typeDefinitionTrackPostService.detectPostalService(parcel.getNumber());
        } else {
            serviceType = PostalServiceType.UNKNOWN; // Номер отсутствует — определить службу невозможно
        }
        PostalServiceStatistics psStats = null;
        boolean updatePostalStats = serviceType != PostalServiceType.UNKNOWN;
        if (updatePostalStats) {
            psStats = postalServiceStatisticsRepository
                    .findByStoreIdAndPostalServiceType(store.getId(), serviceType)
                    .orElse(null);
        }

        if (stats.getTotalSent() > 0) {
            stats.setTotalSent(stats.getTotalSent() - 1);
            stats.setUpdatedAt(ZonedDateTime.now());
            storeAnalyticsRepository.save(stats);
            log.info("➖ Уменьшили totalSent после удаления неучтённой посылки: {}", parcel.getNumber());
        } else {
            log.warn("Попытка уменьшить totalSent, но он уже 0. Посылка: {}", parcel.getNumber());
        }

        if (updatePostalStats && psStats != null && psStats.getTotalSent() > 0) {
            psStats.setTotalSent(psStats.getTotalSent() - 1);
            psStats.setUpdatedAt(ZonedDateTime.now());
            postalServiceStatisticsRepository.save(psStats);
        }

        LocalDate day = parcel.getTimestamp() != null ? parcel.getTimestamp().toLocalDate() : null;
        if (day != null) {
            StoreDailyStatistics daily = storeDailyStatisticsRepository
                    .findByStoreIdAndDate(store.getId(), day)
                    .orElse(null);
            if (daily != null && daily.getSent() > 0) {
                daily.setSent(daily.getSent() - 1);
                daily.setUpdatedAt(ZonedDateTime.now());
                storeDailyStatisticsRepository.save(daily);
            }

            if (updatePostalStats) {
                PostalServiceDailyStatistics psDaily = postalServiceDailyStatisticsRepository
                        .findByStoreIdAndPostalServiceTypeAndDate(store.getId(), serviceType, day)
                        .orElse(null);
                if (psDaily != null && psDaily.getSent() > 0) {
                    psDaily.setSent(psDaily.getSent() - 1);
                    psDaily.setUpdatedAt(Instant.now());
                    postalServiceDailyStatisticsRepository.save(psDaily);
                }
            }
        }

        log.info("Удаление трека {} из статистики завершено", parcel.getNumber());
    }


    /**
     * Устанавливает дату в истории, если она изменилась.
     */
    private void setHistoryDate(String logMessage, ZonedDateTime oldDate, ZonedDateTime newDate, Consumer<ZonedDateTime> setter) {
        if (newDate != null && !Objects.equals(oldDate, newDate)) {
            log.info("{}: {}", logMessage, newDate);
            setter.accept(newDate);
        }
    }

    /**
     * Проверяет, создан ли трек сразу с финальным статусом без исторических изменений.
     *
     * @param oldStatus предыдущий статус (может отсутствовать при создании трека)
     * @param newStatus актуальный статус после обновления
     * @return {@code true}, если посылка появилась сразу в финальном статусе
     */
    private boolean isInitialFinalStatus(GlobalStatus oldStatus, GlobalStatus newStatus) {
        return oldStatus == null && newStatus != null && newStatus.isFinal();
    }

    /**
     * Определяет, нужно ли отправлять уведомление покупателю о смене статуса.
     * Проверяет наличие идентификатора чата в Telegram, активную подписку
     * и факт отсутствия ранее отправленного уведомления по данному статусу.
     */
    private boolean shouldNotifyCustomer(TrackParcel parcel, GlobalStatus status) {
        Customer customer = parcel.getCustomer();
        if (customer == null || customer.getTelegramChatId() == null) {
            return false;
        }

        Long ownerId = parcel.getStore().getOwner().getId();
        boolean allowed = subscriptionService.isFeatureEnabled(ownerId, FeatureKey.TELEGRAM_NOTIFICATIONS);
        if (!allowed) {
            return false;
        }

        return !customerNotificationLogRepository.existsByParcelIdAndStatusAndNotificationType(
                parcel.getId(),
                status,
                NotificationType.INSTANT
        );
    }

    /**
     * Сохраняет запись об отправленном уведомлении, чтобы исключить повторные отправки
     * для одного и того же статуса.
     */
    private void saveNotificationLog(TrackParcel parcel, GlobalStatus status) {
        CustomerNotificationLog logEntry = new CustomerNotificationLog();
        logEntry.setCustomer(parcel.getCustomer());
        logEntry.setParcel(parcel);
        logEntry.setStatus(status);
        logEntry.setNotificationType(NotificationType.INSTANT);
        logEntry.setSentAt(ZonedDateTime.now(ZoneOffset.UTC));
        customerNotificationLogRepository.save(logEntry);
    }

}