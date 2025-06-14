package com.project.tracking_system.service.track;

import com.project.tracking_system.controller.WebSocketController;
import com.project.tracking_system.dto.TrackParcelDTO;
import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.entity.*;
import com.project.tracking_system.repository.StoreRepository;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.repository.UserRepository;
import com.project.tracking_system.repository.UserSubscriptionRepository;
import com.project.tracking_system.repository.StoreAnalyticsRepository;
import com.project.tracking_system.repository.PostalServiceStatisticsRepository;
import com.project.tracking_system.repository.StoreDailyStatisticsRepository;
import com.project.tracking_system.repository.PostalServiceDailyStatisticsRepository;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.service.analytics.DeliveryHistoryService;
import com.project.tracking_system.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import com.project.tracking_system.utils.DateParserUtils;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Сервис для управления посылками пользователей в системе отслеживания.
 * Этот класс отвечает за сохранение, обновление, поиск и удаление посылок пользователя, а также за управление их статусами.
 * Методы этого класса взаимодействуют с репозиторием для работы с базой данных и с другими сервисами для получения информации о посылках.
 * <p>
 * Основные функции:
 * - Сохранение или обновление информации о посылках пользователя.
 * - Поиск посылок по статусу и пользователю с поддержкой пагинации.
 * - Обновление истории отслеживания посылок асинхронно.
 * - Удаление посылок по номерам и идентификатору пользователя.
 * </p>
 * Взаимодействует с сервисами:
 * - {@link TypeDefinitionTrackPostService} для получения информации о типах посылок.
 * - {@link StatusTrackService} для обновления статусов посылок.
 *
 * @author Dmitriy Anisimov
 * @date Добавлено 07.01.2025
 */
@RequiredArgsConstructor
@Slf4j
@Service
public class TrackParcelService {

    private final WebSocketController webSocketController;
    private final TypeDefinitionTrackPostService typeDefinitionTrackPostService;
    private final StatusTrackService statusTrackService;
    private final SubscriptionService subscriptionService;
    private final DeliveryHistoryService deliveryHistoryService;
    private final UserService userService;
    private final UserSubscriptionRepository userSubscriptionRepository;
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
    public TrackInfoListDTO processTrack(String number, Long storeId, Long userId, boolean canSave) {
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
            save(number, trackInfo, storeId, userId);
            log.debug("✅ Посылка сохранена: {} (UserID={}, StoreID={})", number, userId, storeId);
        } else {
            log.info("⏳ Трек '{}' обработан, но не сохранён.", number);
        }

        return trackInfo;
    }

    /**
     * Сохраняет или обновляет посылку пользователя.
     * <p>
     * Этот метод сохраняет новую посылку или обновляет существующую, основываясь на номере посылки.
     * Статус и дата обновляются на основе информации о посылке, полученной из {@link TrackInfoListDTO}.
     * </p>
     *
     * @param number номер посылки
     * @param trackInfoListDTO информация о посылке
     * @param storeId идентификатор магазина
     * @param userId  идентификатор пользователя
     */
    @Transactional
    public void save(String number, TrackInfoListDTO trackInfoListDTO, Long storeId, Long userId) {
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
                throw new IllegalArgumentException("Вы не можете сохранить больше посылок, так как превышен лимит сохранённых посылок.");
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

        trackParcelRepository.save(trackParcel);

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

            // Postal service statistics (skip UNKNOWN)
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
                log.warn("⛔ Skipping analytics update for UNKNOWN service: {}", number);
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

            // Daily postal service statistics
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

    /**
     * Проверяет, принадлежит ли посылка пользователю.
     *
     * @param itemNumber Номер посылки.
     * @param userId     ID пользователя.
     * @return true, если посылка принадлежит пользователю.
     */
    public boolean userOwnsParcel(String itemNumber, Long userId) {
        return trackParcelRepository.existsByNumberAndUserId(itemNumber, userId);
    }

    /**
     * Находит посылки по магазинам с учётом пагинации.
     *
     * @param storeIds список идентификаторов магазинов
     * @param page     номер страницы
     * @param size     размер страницы
     * @param userId   идентификатор пользователя для определения часового пояса
     * @return страница с найденными посылками
     */
    @Transactional
    public Page<TrackParcelDTO> findByStoreTracks(List<Long> storeIds, int page, int size, Long userId) {
        Pageable pageable = PageRequest.of(page, size);
        Page<TrackParcel> trackParcels = trackParcelRepository.findByStoreIdIn(storeIds, pageable);
        ZoneId userZone = userService.getUserZone(userId);
        return trackParcels.map(track -> new TrackParcelDTO(track, userZone));
    }

    /**
     * Ищет посылки магазинов по статусу с поддержкой пагинации.
     * <p>
     * Возвращает страницу посылок выбранных магазинов по указанному статусу.
     * </p>
     *
     * @param storeIds список идентификаторов магазинов
     * @param status   статус посылки
     * @param page     номер страницы
     * @param size     размер страницы
     * @param userId   идентификатор пользователя для определения часового пояса
     * @return страница с посылками пользователя по статусу
     */
    @Transactional
    public Page<TrackParcelDTO> findByStoreTracksAndStatus(List<Long> storeIds, GlobalStatus status, int page, int size, Long userId) {
        Pageable pageable = PageRequest.of(page, size);
        Page<TrackParcel> trackParcels = trackParcelRepository.findByStoreIdInAndStatus(storeIds, status, pageable);
        ZoneId userZone = userService.getUserZone(userId);
        return trackParcels.map(track -> new TrackParcelDTO(track, userZone));
    }

    /**
     * Подсчитывает общее количество посылок в системе.
     *
     * @return количество всех сохранённых посылок
     */
    @Transactional
    public long countAllParcels() {
        return trackParcelRepository.count();
    }

    /**
     * Проверяет, является ли посылка новой для данного магазина.
     *
     * @param trackingNumber номер отслеживания
     * @param storeId ID магазина
     * @return true, если посылка отсутствует в магазине (новая), false, если уже существует
     */
    @Transactional
    public boolean isNewTrack(String trackingNumber, Long storeId) {
        if (storeId == null) {
            return true; // Если магазин не указан, считаем, что посылка новая
        }

        TrackParcel existing = trackParcelRepository.findByNumberAndStoreId(trackingNumber, storeId);
        return (existing == null);
    }


    /**
     * Увеличивает счётчик обновлений треков для пользователя.
     *
     * @param userId идентификатор пользователя
     * @param count  величина увеличения
     */
    @Transactional
    public void incrementUpdateCount(Long userId, int count) {
        userSubscriptionRepository.incrementUpdateCount(userId, count, LocalDate.now(ZoneOffset.UTC));
    }

    /**
     * Возвращает все посылки, принадлежащие конкретному магазину.
     *
     * @param storeId ID магазина
     * @return список всех посылок в магазине
     */
    @Transactional
    public List<TrackParcelDTO> findAllByStoreTracks(Long storeId, Long userId) {
        List<TrackParcel> trackParcels = trackParcelRepository.findByStoreId(storeId);
        ZoneId userZone = userService.getUserZone(userId);
        return trackParcels.stream()
                .map(track -> new TrackParcelDTO(track, userZone))
                .toList();
    }

    /**
     * Возвращает все посылки, принадлежащие конкретному пользователю.
     *
     * @param userId ID пользователя
     * @return список всех посылок пользователя
     */
    @Transactional
    public List<TrackParcelDTO> findAllByUserTracks(Long userId) {
        List<TrackParcel> trackParcels = trackParcelRepository.findByUserId(userId);
        ZoneId userZone = userService.getUserZone(userId);
        return trackParcels.stream()
                .map(track -> new TrackParcelDTO(track, userZone))
                .toList();
    }

    /**
     * Обновляет историю отслеживания посылок для всех магазинов пользователя.
     *
     * @param userId   ID пользователя, владеющего магазинами.
     */
    @Transactional
    public UpdateResult updateAllParcels(Long userId) {
        // Проверяем подписку
        if (!subscriptionService.canUseBulkUpdate(userId)) {
            String msg = "Обновление всех треков доступно только в премиум-версии.";
            log.warn("Отказано в доступе для пользователя ID: {}", userId);

            webSocketController.sendUpdateStatus(userId, msg, false);
            log.debug("📡 WebSocket отправлено: {}", msg);

            return new UpdateResult(false, 0, 0, msg);
        }

        // Получаем количество магазинов пользователя
        int count = storeRepository.countByOwnerId(userId);
        if (count == 0) {
            log.warn("У пользователя ID={} нет магазинов для обновления треков.", userId);
            return new UpdateResult(false, 0, 0, "У вас нет магазинов с посылками.");
        }

        // Получаем все треки пользователя
        List<TrackParcelDTO> allParcels = findAllByUserTracks(userId);

        // Фильтруем треки, исключая финальные статусы
        List<TrackParcelDTO> parcelsToUpdate = allParcels.stream()
                .filter(dto -> !GlobalStatus.fromDescription(dto.getStatus()).isFinal())
                .toList();

        log.info("📦 Запущено обновление всех {} треков для userId={}", parcelsToUpdate.size(), userId);

        // Отправляем уведомление о запуске
        webSocketController.sendUpdateStatus(userId, "Обновление всех треков запущено...", true);

        // Запуск асинхронного процесса
        processAllTrackUpdatesAsync(userId, parcelsToUpdate);

        return new UpdateResult(true, parcelsToUpdate.size(), allParcels.size(),
                "Запущено обновление " + parcelsToUpdate.size() + " треков из " + allParcels.size());
    }

    /**
     * Асинхронно обновляет все треки пользователя.
     * <p>
     * Метод выполняется в отдельном потоке благодаря {@link Async} и
     * обернут в транзакцию с помощью {@link Transactional}. После запуска
     * каждый трек обрабатывается параллельно, а по завершению формируется
     * итоговое сообщение с количеством успешно обновлённых треков.
     * </p>
     *
     * @param userId        идентификатор пользователя
     * @param parcelsToUpdate список DTO посылок для обновления
     */
    @Async("Post")
    @Transactional
    public void processAllTrackUpdatesAsync(Long userId, List<TrackParcelDTO> parcelsToUpdate) {
        try {
            AtomicInteger successfulUpdates = new AtomicInteger(0);

            List<CompletableFuture<Void>> futures = parcelsToUpdate.stream()
                    .map(trackParcelDTO -> CompletableFuture.runAsync(() -> {
                        try {
                            // Используем `processTrack()`, он уже включает определение типа и обновление!
                            TrackInfoListDTO trackInfo = processTrack(
                                    trackParcelDTO.getNumber(),
                                    trackParcelDTO.getStoreId(),
                                    userId,
                                    true // Позволяем обновление и сохранение
                            );

                            if (trackInfo != null && !trackInfo.getList().isEmpty()) {
                                successfulUpdates.incrementAndGet();
                                log.debug("Трек {} обновлён для пользователя ID={}", trackParcelDTO.getNumber(), userId);
                            } else {
                                log.warn("Нет данных по треку {} (userId={})", trackParcelDTO.getNumber(), userId);
                            }

                        } catch (IllegalArgumentException e) {
                            log.warn("Ошибка обновления трека {}: {}", trackParcelDTO.getNumber(), e.getMessage());
                        } catch (Exception e) {
                            log.error("Ошибка обработки трека {}: {}", trackParcelDTO.getNumber(), e.getMessage(), e);
                        }
                    }))
                    .toList();

            // Ждём завершения всех обновлений
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {
                int updatedCount = successfulUpdates.get();
                int totalCount = parcelsToUpdate.size();

                log.info("Итог обновления всех треков для userId={}: {} обновлено, {} не изменено",
                        userId, updatedCount, totalCount - updatedCount);

                // Формируем сообщение
                String message;
                if (updatedCount == 0) {
                    message = "Обновление завершено, но все треки уже были в финальном статусе.";
                } else {
                    message = "Обновление завершено! " + updatedCount + " из " + totalCount + " треков обновлено.";
                }

                // Отправляем финальное уведомление через WebSocket
                webSocketController.sendDetailUpdateStatus(
                        userId,
                        new UpdateResult(true, updatedCount, totalCount, message)
                );
            });

        } catch (Exception e) {
            log.error("Ошибка при обновлении всех треков для пользователя {}: {}", userId, e.getMessage());
            webSocketController.sendUpdateStatus(userId, "Ошибка при обновлении всех треков: " + e.getMessage(), false);
        }
    }

    /**
     * Обновляет выбранные посылки пользователя.
     *
     * @param userId          ID пользователя (для проверки подписки)
     * @param selectedNumbers список номеров посылок
     * @return результат обновления
     */
    @Transactional
    public UpdateResult updateSelectedParcels(Long userId, List<String> selectedNumbers) {
        // Загружаем посылки по номерам, игнорируя магазины
        List<TrackParcel> selectedParcels = trackParcelRepository.findByNumberInAndUserId(selectedNumbers, userId);

        // Считаем количество финальных и обновляемых треков
        int totalRequested = selectedParcels.size();
        List<TrackParcel> updatableParcels = selectedParcels.stream()
                .filter(parcel -> !parcel.getStatus().isFinal())
                .toList();
        int nonUpdatableCount = totalRequested - updatableParcels.size();

        log.info("Фильтрация завершена: {} треков можно обновить из {}, {} уже в финальном статусе",
                updatableParcels.size(), totalRequested, nonUpdatableCount);

        if (updatableParcels.isEmpty()) {
            String msg = "Все выбранные треки уже в финальном статусе, обновление не требуется.";
            log.warn(msg);
            webSocketController.sendUpdateStatus(userId, msg, true);
            return new UpdateResult(false, 0, selectedNumbers.size(), msg);
        }

        // Проверяем лимит подписки
        int remainingUpdates = subscriptionService.canUpdateTracks(userId, updatableParcels.size());

        if (remainingUpdates <= 0) {
            String msg = "Ваш лимит обновлений на сегодня исчерпан.";
            log.info("Лимит обновлений исчерпан для пользователя ID: {}", userId);
            webSocketController.sendUpdateStatus(userId, msg, true);
            return new UpdateResult(false, 0, updatableParcels.size(), msg);
        }

        int updatesToProcess = Math.min(updatableParcels.size(), remainingUpdates);
        List<TrackParcel> parcelsToUpdate = updatableParcels.subList(0, updatesToProcess);

        log.info("Запущено обновление {} треков для пользователя ID={}", updatesToProcess, userId);

        // Вызов асинхронного метода с `userId`
        processTrackUpdatesAsync(userId, parcelsToUpdate, totalRequested, nonUpdatableCount);

        return new UpdateResult(true, updatesToProcess, selectedNumbers.size(),
                "Обновление запущено...");
    }

    /**
     * Асинхронно обновляет выбранный список посылок пользователя.
     * <p>
     * Выполнение метода происходит в отдельном потоке благодаря аннотации
     * {@link Async}. Транзакция гарантирует целостность всех операций
     * обновления. После обработки формируется уведомление о количестве
     * обновлённых треков и учитывается лимит подписки.
     * </p>
     *
     * @param userId            идентификатор пользователя
     * @param parcelsToUpdate   список посылок для обновления
     * @param totalRequested    общее количество запрошенных треков
     * @param nonUpdatableCount количество треков в финальном статусе
     */
    @Async("Post")
    @Transactional
    public void processTrackUpdatesAsync(Long userId, List<TrackParcel> parcelsToUpdate, int totalRequested, int nonUpdatableCount) {
        try {
            AtomicInteger successfulUpdates = new AtomicInteger(0);

            log.info("Начато обновление {} треков для userId={}", parcelsToUpdate.size(), userId);

            List<CompletableFuture<Void>> futures = parcelsToUpdate.stream()
                    .map(parcel -> CompletableFuture.runAsync(() -> {
                        try {
                            TrackInfoListDTO trackInfo = processTrack(parcel.getNumber(), parcel.getStore().getId(), userId, true);
                            if (trackInfo != null && !trackInfo.getList().isEmpty()) {
                                successfulUpdates.incrementAndGet();
                            }
                        } catch (Exception e) {
                            log.error("Ошибка обновления трека {}: {}", parcel.getNumber(), e.getMessage());
                        }
                    }))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {
                int updatedCount = successfulUpdates.get();
                log.info("Итог обновления для userId={}: {} обновлено, {} в финальном статусе",
                        userId, updatedCount, nonUpdatableCount);

                // Если обновлено хотя бы 1 трек, обновляем лимит в подписке
                if (updatedCount > 0) {
                    log.info("Финальное обновление updateCount для userId={}, добавляем={}", userId, updatedCount);
                    incrementUpdateCount(userId, updatedCount);
                }

                // Формируем информативное сообщение
                String message;
                if (updatedCount == 0 && nonUpdatableCount == 0) {
                    message = "Все треки уже были обновлены ранее.";
                } else if (updatedCount == 0) {
                    message = "Обновление завершено, но все треки уже в финальном статусе.";
                } else {
                    message = "Обновление завершено! " + updatedCount + " из " + totalRequested + " треков обновлено.";
                    if (nonUpdatableCount > 0) {
                        message += " " + nonUpdatableCount + " треков уже были в финальном статусе.";
                    }
                }

                // Отправляем уведомление через WebSocket
                webSocketController.sendDetailUpdateStatus(
                        userId,
                        new UpdateResult(true, updatedCount, totalRequested, message)
                );
            });

        } catch (Exception e) {
            log.error("Ошибка при обновлении посылок для пользователя {}: {}", userId, e.getMessage());
            webSocketController.sendUpdateStatus(userId, "Ошибка обновления: " + e.getMessage(), false);
        }
    }

    /**
     * Удаляет посылки пользователя по номерам.
     * <p>
     * Этот метод удаляет посылки по номерам и идентификатору пользователя.
     * </p>
     *
     * @param numbers список номеров посылок
     * @param userId  идентификатор пользователя
     */
    @Transactional
    public void deleteByNumbersAndUserId(List<String> numbers, Long userId) {
        List<TrackParcel> parcelsToDelete = trackParcelRepository.findByNumberInAndUserId(numbers, userId);

        if (parcelsToDelete.isEmpty()) {
            log.warn("❌ Попытка удаления несуществующих посылок. userId={}, номера={}", userId, numbers);
            throw new RuntimeException("Нет посылок для удаления.");
        }

        // Обнуляем связь с DeliveryHistory, чтобы Hibernate не пытался сохранять зависимую сущность
        for (TrackParcel parcel : parcelsToDelete) {
            deliveryHistoryService.handleTrackParcelBeforeDelete(parcel);

            if (parcel.getDeliveryHistory() != null) {
                parcel.getDeliveryHistory().setTrackParcel(null); // Разрываем связь
                parcel.setDeliveryHistory(null); // Обнуляем с двух сторон
            }
        }

        // Удаляем все треки
        trackParcelRepository.deleteAll(parcelsToDelete);
        log.info("✅ Удалены {} посылок пользователя ID={}", parcelsToDelete.size(), userId);
    }

}