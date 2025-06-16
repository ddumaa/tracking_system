package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.entity.*;
import com.project.tracking_system.repository.*;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.service.analytics.DeliveryHistoryService;
import com.project.tracking_system.service.customer.CustomerService;
import com.project.tracking_system.service.customer.CustomerStatsService;
import com.project.tracking_system.service.user.UserService;
import com.project.tracking_system.utils.DateParserUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;

/**
 * Сервис обработки треков и их сохранения.
 * <p>
 * Отвечает за получение информации о посылке и сохранение/обновление
 * данных в системе.
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class TrackProcessingService {

    private final TypeDefinitionTrackPostService typeDefinitionTrackPostService;
    private final StatusTrackService statusTrackService;
    private final SubscriptionService subscriptionService;
    private final DeliveryHistoryService deliveryHistoryService;
    private final CustomerService customerService;
    private final CustomerStatsService customerStatsService;
    private final UserService userService;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final TrackParcelRepository trackParcelRepository;
    private final StoreAnalyticsRepository storeAnalyticsRepository;
    private final PostalServiceStatisticsRepository postalServiceStatisticsRepository;
    private final StoreDailyStatisticsRepository storeDailyStatisticsRepository;
    private final PostalServiceDailyStatisticsRepository postalServiceDailyStatisticsRepository;

    /**
     * Обрабатывает номер посылки: получает информацию и при необходимости сохраняет её.
     *
     * @param number  номер посылки
     * @param storeId идентификатор магазина
     * @param userId  идентификатор пользователя
     * @param canSave признак возможности сохранения
     * @return данные о посылке
     */
    @Transactional
    public TrackInfoListDTO processTrack(String number,
                                         Long storeId,
                                         Long userId,
                                         boolean canSave) {
        return processTrack(number, storeId, userId, canSave, null);
    }

    /**
     * Обрабатывает номер посылки и связывает его с покупателем при наличии телефона.
     *
     * @param number  номер посылки
     * @param storeId идентификатор магазина
     * @param userId  идентификатор пользователя
     * @param canSave признак возможности сохранения
     * @param phone   номер телефона покупателя (может быть null)
     * @return данные о посылке
     */
    @Transactional
    public TrackInfoListDTO processTrack(String number,
                                         Long storeId,
                                         Long userId,
                                         boolean canSave,
                                         String phone) {
        if (number == null) {
            throw new IllegalArgumentException("Номер посылки не может быть null");
        }
        number = number.toUpperCase(); // Приводим к верхнему регистру

        log.info("Обработка трека: {} (Пользователь ID={}, Магазин ID={})", number, userId, storeId);

        // Получаем данные о треке
        TrackInfoListDTO trackInfo = typeDefinitionTrackPostService.getTypeDefinitionTrackPostService(userId, number);

        if (trackInfo == null || trackInfo.getList().isEmpty()) {
            log.warn("Данных по треку {} не найдено", number);
            return trackInfo;
        }

        // Сохраняем трек, если пользователь авторизован и разрешено сохранять
        if (userId != null && canSave) {
            save(number, trackInfo, storeId, userId, phone);
            log.debug("✅ Посылка сохранена: {} (UserID={}, StoreID={})", number, userId, storeId);
        } else {
            log.info("⏳ Трек '{}' обработан, но не сохранён.", number);
        }

        return trackInfo;
    }

    /**
     * Сохраняет или обновляет посылку пользователя.
     *
     * @param number номер посылки
     * @param trackInfoListDTO информация о посылке
     * @param storeId идентификатор магазина
     * @param userId  идентификатор пользователя
     */
    @Transactional
    public void save(String number, TrackInfoListDTO trackInfoListDTO, Long storeId, Long userId) {
        save(number, trackInfoListDTO, storeId, userId, null);
    }

    /**
     * Сохраняет или обновляет посылку пользователя и привязывает её к покупателю.
     *
     * @param number номер посылки
     * @param trackInfoListDTO информация о посылке
     * @param storeId идентификатор магазина
     * @param userId идентификатор пользователя
     * @param phone телефон покупателя (может быть null)
     */
    @Transactional
    public void save(String number,
                     TrackInfoListDTO trackInfoListDTO,
                     Long storeId,
                     Long userId,
                     String phone) {
        log.info("Начало сохранения трека {} для пользователя ID={}", number, userId);
        if (number == null || trackInfoListDTO == null) {
            throw new IllegalArgumentException("Отсутствует посылка");
        }

        // Ищем трек по номеру и пользователю независимо от магазина
        TrackParcel trackParcel = trackParcelRepository.findByNumberAndUserId(number, userId);
        boolean isNewParcel = (trackParcel == null);
        GlobalStatus oldStatus = (!isNewParcel) ? trackParcel.getStatus() : null;
        ZonedDateTime previousDate = null; // дата отправления старого трека
        Long previousStoreId = null;       // магазин, в котором хранился трек ранее

        // Если трек новый, проверяем лимиты
        if (isNewParcel) {
            int remainingTracks = subscriptionService.canSaveMoreTracks(userId, 1);
            if (remainingTracks <= 0) {
                throw new IllegalArgumentException(
                        "Вы не можете сохранить больше посылок, так как превышен лимит сохранённых посылок.");
            }

            // Используем getReferenceById()
            Store store = storeRepository.getReferenceById(storeId);
            User user = userRepository.getReferenceById(userId);

            // Создаём новый трек
            trackParcel = new TrackParcel();
            trackParcel.setNumber(number);
            trackParcel.setStore(store);
            trackParcel.setUser(user);
            log.info("Создан новый трек {} для пользователя ID={}", number, userId);

        } else {
            // Запоминаем предыдущие значения для корректировки статистики
            previousStoreId = trackParcel.getStore().getId();
            previousDate = trackParcel.getData();
        }
        // Если трек уже существует, проверяем, соответствует ли магазин выбранному пользователем
        if (!trackParcel.getStore().getId().equals(storeId)) {
            // Загружаем новый магазин
            Long oldStoreId = trackParcel.getStore().getId();
            Store newStore = storeRepository.getReferenceById(storeId);

            // Обновляем магазин у трека
            trackParcel.setStore(newStore);
            log.info("Обновление магазина для трека {}: с магазина {} на магазин {}", number, oldStoreId, storeId);
        }

        // Обновляем статус и дату трека на основе нового содержимого
        GlobalStatus newStatus = statusTrackService.setStatus(trackInfoListDTO.getList());

        trackParcel.setStatus(newStatus);

        String lastDate = trackInfoListDTO.getList().get(0).getTimex();
        ZoneId userZone = userService.getUserZone(userId);
        ZonedDateTime zonedDateTime = DateParserUtils.parse(lastDate, userZone);
        trackParcel.setData(zonedDateTime);

        // Привязываем покупателя, если указан телефон
        Customer customer = null;
        if (phone != null && !phone.isBlank()) {
            customer = customerService.registerOrGetByPhone(phone);
            trackParcel.setCustomer(customer);
        }

        trackParcelRepository.save(trackParcel);

        if (isNewParcel && customer != null) {
            customerStatsService.incrementSent(customer);
        }

        boolean storeChanged = !isNewParcel && previousStoreId != null && !previousStoreId.equals(storeId);

        PostalServiceType serviceType = typeDefinitionTrackPostService.detectPostalService(number);

        // Инкрементируем статистику нового магазина при добавлении новой посылки или смене магазина
        if (isNewParcel || storeChanged) {
            StoreStatistics statistics = storeAnalyticsRepository.findByStoreId(storeId)
                    .orElseThrow(() -> new IllegalStateException("Статистика не найдена"));

            // Атомарное обновление счётчика отправлений
            int updated = storeAnalyticsRepository.incrementTotalSent(storeId, 1);
            if (updated == 0) {
                statistics.setTotalSent(statistics.getTotalSent() + 1);
                statistics.setUpdatedAt(ZonedDateTime.now(ZoneOffset.UTC));
                storeAnalyticsRepository.save(statistics);
            }

            // Статистика почтовой службы (пропустить UNKNOWN)
            if (serviceType != PostalServiceType.UNKNOWN) {
                int psUpdated = postalServiceStatisticsRepository.incrementTotalSent(storeId, serviceType, 1);
                if (psUpdated == 0) {
                    PostalServiceStatistics psStats = postalServiceStatisticsRepository
                            .findByStoreIdAndPostalServiceType(storeId, serviceType)
                            .orElseGet(() -> {
                                PostalServiceStatistics s = new PostalServiceStatistics();
                                s.setStore(statistics.getStore());
                                s.setPostalServiceType(serviceType);
                                return s;
                            });
                    psStats.setTotalSent(psStats.getTotalSent() + 1);
                    psStats.setUpdatedAt(ZonedDateTime.now(ZoneOffset.UTC));
                    postalServiceStatisticsRepository.save(psStats);
                }
            } else {
                log.warn("⛔ Пропуск обновления аналитики для UNKNOWN службы: {}", number);
            }

            // Ежедневная статистика магазина
            LocalDate day = zonedDateTime.toLocalDate();
            int dailyUpdated = storeDailyStatisticsRepository.incrementSent(storeId, day, 1);
            if (dailyUpdated == 0) {
                StoreDailyStatistics daily = storeDailyStatisticsRepository
                        .findByStoreIdAndDate(storeId, day)
                        .orElseGet(() -> {
                            StoreDailyStatistics d = new StoreDailyStatistics();
                            d.setStore(statistics.getStore());
                            d.setDate(day);
                            return d;
                        });
                daily.setSent(daily.getSent() + 1);
                daily.setUpdatedAt(ZonedDateTime.now(ZoneOffset.UTC));
                storeDailyStatisticsRepository.save(daily);
            }

            // Ежедневная статистика почтовой службы
            if (serviceType != PostalServiceType.UNKNOWN) {
                int psdUpdated = postalServiceDailyStatisticsRepository.incrementSent(storeId, serviceType, day, 1);
                if (psdUpdated == 0) {
                    PostalServiceDailyStatistics psDaily = postalServiceDailyStatisticsRepository
                            .findByStoreIdAndPostalServiceTypeAndDate(storeId, serviceType, day)
                            .orElseGet(() -> {
                                PostalServiceDailyStatistics d = new PostalServiceDailyStatistics();
                                d.setStore(statistics.getStore());
                                d.setPostalServiceType(serviceType);
                                d.setDate(day);
                                return d;
                            });
                    psDaily.setSent(psDaily.getSent() + 1);
                    psDaily.setUpdatedAt(ZonedDateTime.now(ZoneOffset.UTC));
                    postalServiceDailyStatisticsRepository.save(psDaily);
                }
            }
        }

        // Декрементируем статистику старого магазина, если посылка перенесена
        if (storeChanged) {
            StoreStatistics oldStats = storeAnalyticsRepository.findByStoreId(previousStoreId)
                    .orElseThrow(() -> new IllegalStateException("Статистика не найдена"));
            if (oldStats.getTotalSent() > 0) {
                oldStats.setTotalSent(oldStats.getTotalSent() - 1);
                oldStats.setUpdatedAt(ZonedDateTime.now(ZoneOffset.UTC));
                storeAnalyticsRepository.save(oldStats);
            }

            if (serviceType != PostalServiceType.UNKNOWN) {
                PostalServiceStatistics oldPs = postalServiceStatisticsRepository
                        .findByStoreIdAndPostalServiceType(previousStoreId, serviceType)
                        .orElse(null);
                if (oldPs != null && oldPs.getTotalSent() > 0) {
                    oldPs.setTotalSent(oldPs.getTotalSent() - 1);
                    oldPs.setUpdatedAt(ZonedDateTime.now(ZoneOffset.UTC));
                    postalServiceStatisticsRepository.save(oldPs);
                }
            }

            if (previousDate != null) {
                LocalDate prevDay = previousDate.toLocalDate();
                StoreDailyStatistics oldDaily = storeDailyStatisticsRepository
                        .findByStoreIdAndDate(previousStoreId, prevDay)
                        .orElse(null);
                if (oldDaily != null && oldDaily.getSent() > 0) {
                    oldDaily.setSent(oldDaily.getSent() - 1);
                    oldDaily.setUpdatedAt(ZonedDateTime.now(ZoneOffset.UTC));
                    storeDailyStatisticsRepository.save(oldDaily);
                }

                if (serviceType != PostalServiceType.UNKNOWN) {
                    PostalServiceDailyStatistics oldPsDaily = postalServiceDailyStatisticsRepository
                            .findByStoreIdAndPostalServiceTypeAndDate(previousStoreId, serviceType, prevDay)
                            .orElse(null);
                    if (oldPsDaily != null && oldPsDaily.getSent() > 0) {
                        oldPsDaily.setSent(oldPsDaily.getSent() - 1);
                        oldPsDaily.setUpdatedAt(ZonedDateTime.now(ZoneOffset.UTC));
                        postalServiceDailyStatisticsRepository.save(oldPsDaily);
                    }
                }
            }
        }

        // Обновляем историю доставки
        deliveryHistoryService.updateDeliveryHistory(trackParcel, oldStatus, newStatus, trackInfoListDTO);

        log.info("Обновлено: userId={}, storeId={}, трек={}, новый статус={}",
                userId, storeId, trackParcel.getNumber(), newStatus);
    }
}
