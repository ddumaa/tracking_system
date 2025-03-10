package com.project.tracking_system.service;

import com.project.tracking_system.controller.WebSocketController;
import com.project.tracking_system.dto.TrackParcelDTO;
import com.project.tracking_system.dto.TrackInfoDTO;
import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.UpdateResult;
import com.project.tracking_system.model.GlobalStatus;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.repository.UserRepository;
import com.project.tracking_system.repository.UserSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
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
    private final TrackParcelRepository trackParcelRepository;
    private final UserRepository userRepository;
    private final TypeDefinitionTrackPostService typeDefinitionTrackPostService;
    private final StatusTrackService statusTrackService;
    private final SubscriptionService subscriptionService;
    private final UserSubscriptionRepository userSubscriptionRepository;

    /**
     * Сохраняет или обновляет посылку пользователя.
     * <p>
     * Этот метод сохраняет новую посылку или обновляет существующую, основываясь на номере посылки.
     * Статус и дата обновляются на основе информации о посылке, полученной из {@link TrackInfoListDTO}.
     * </p>
     *
     * @param number номер посылки
     * @param trackInfoListDTO информация о посылке
     * @param username имя пользователя
     */
    @Transactional
    public void save(String number, TrackInfoListDTO trackInfoListDTO, Long userId) {
        if (number == null || trackInfoListDTO == null) {
            throw new IllegalArgumentException("Отсутствует посылка");
        }

        // Проверяем, существует ли уже этот трек у пользователя
        TrackParcel trackParcel = trackParcelRepository.findByNumberAndUserId(number, userId);

        boolean isNewTrack = (trackParcel == null);

        int remainingTracks = subscriptionService.canSaveMoreTracks(userId, 1);

        // Если трек новый, проверяем лимиты
        if (isNewTrack) {

            if (remainingTracks <= 0) {
                throw new IllegalArgumentException("Вы не можете сохранить больше посылок, так как превышен лимит сохранённых посылок.");
            }

            // Создаём новый трек
            trackParcel = new TrackParcel();
            trackParcel.setNumber(number);
            trackParcel.setUser(userRepository.getReferenceById(userId)); // Lazy загрузка пользователя
        }

        // Обновляем статус и дату трека на основе нового содержимого
        trackParcel.setStatus(statusTrackService.setStatus(trackInfoListDTO.getList()));
        trackParcel.setData(trackInfoListDTO.getList().get(trackInfoListDTO.getList().size() - 1).getTimex());

        trackParcelRepository.save(trackParcel);

        log.info("✅ Обновлено: userId={}, трек={}, новый статус={}", userId, trackParcel.getNumber(), trackParcel.getStatus());

    }

    /**
     * Ищет посылки пользователя с поддержкой пагинации.
     * <p>
     * Этот метод возвращает страницу посылок пользователя по имени, используя пагинацию.
     * </p>
     *
     * @param username имя пользователя
     * @param page номер страницы
     * @param size размер страницы
     * @return страница с посылками пользователя
     */
    @Transactional
    public Page<TrackParcelDTO> findByUserTracks(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<TrackParcel> trackParcels = trackParcelRepository.findByUserId(userId, pageable);
        return trackParcels.map(TrackParcelDTO::new);
    }

    /**
     * Ищет посылки пользователя по статусу с поддержкой пагинации.
     * <p>
     * Этот метод возвращает страницу посылок пользователя по статусу и имени, с использованием пагинации.
     * </p>
     *
     * @param username имя пользователя
     * @param status статус посылки
     * @param page номер страницы
     * @param size размер страницы
     * @return страница с посылками пользователя по статусу
     */
    @Transactional
    public Page<TrackParcelDTO> findByUserTracksAndStatus(Long userId, GlobalStatus status, int page, int size) {
        String statusString = (status != null) ? status.getDescription() : null;
        Pageable pageable = PageRequest.of(page, size);
        Page<TrackParcel> trackParcels = trackParcelRepository.findByUserIdAndStatus(userId, statusString, pageable);
        return trackParcels.map(TrackParcelDTO::new);
    }

    /**
     * Ищет все посылки пользователя.
     * <p>
     * Этот метод возвращает список всех посылок пользователя по имени.
     * </p>
     *
     * @param username имя пользователя
     * @return список посылок пользователя
     */
    @Transactional
    public List<TrackParcelDTO> findAllByUserTracks(Long userId) {
        List<TrackParcel> trackParcels = trackParcelRepository.findByUserId(userId);
        return convertToDTO(trackParcels);
    }

    @Transactional
    public long countAllParcels() {
        return trackParcelRepository.count();
    }

    @Transactional
    public boolean isNewTrack(String trackingNumber, Long userId) {
        // Если null, считаем "нет пользователя" => аноним
        if (userId == null) {
            return true; // анонимному пользователю не сохраняем, но условно считаем "новым"
        }

        TrackParcel existing = trackParcelRepository.findByNumberAndUserId(trackingNumber, userId);
        return (existing == null);
    }

    @Transactional
    public void incrementUpdateCount(Long userId, int count) {
        userSubscriptionRepository.incrementUpdateCount(userId, count, LocalDate.now(ZoneOffset.UTC));
    }

    /**
     * Вспомогательный метод для преобразования посылок в DTO.
     * <p>
     * Этот метод преобразует список сущностей {@link TrackParcel} в список DTO {@link TrackParcelDTO}.
     * </p>
     *
     * @param parcels список посылок
     * @return список DTO посылок
     */
    private List<TrackParcelDTO> convertToDTO(List<TrackParcel> parcels) {
        List<TrackParcelDTO> dtoList = new ArrayList<>();
        for (TrackParcel parcel : parcels) {
            dtoList.add(new TrackParcelDTO(parcel));
        }
        return dtoList;
    }

    /**
     * Обновляет историю отслеживания посылок пользователя асинхронно.
     * <p>
     * Этот метод обновляет статус и данные всех посылок пользователя, вызывая асинхронные запросы к сервису {@link TypeDefinitionTrackPostService}.
     * </p>
     *
     * @param name имя пользователя
     */
    @Transactional
    public UpdateResult updateAllParcels(Long userId) {
        // Проверяем подписку
        if (!subscriptionService.canUseBulkUpdate(userId)) {
            String msg = "Обновление всех треков доступно только в премиум-версии.";
            log.warn("Отказано в доступе для пользователя ID: {}", userId);

            // Вместо исключения отправляем уведомление по WebSocket
            webSocketController.sendUpdateStatus(userId, msg, false);
            log.debug("📡 WebSocket отправлено: Обновление всех треков доступно только в премиум-версии.");

            return new UpdateResult(false, 0, 0, msg);
        }

        List<TrackParcelDTO> allParcels = findAllByUserTracks(userId);

        // Фильтруем треки, исключая те, что уже в финальном статусе
        List<TrackParcelDTO> parcelsToUpdate = allParcels.stream()
                .filter(dto -> !(dto.getStatus().equals(GlobalStatus.DELIVERED.getDescription()) ||
                        dto.getStatus().equals(GlobalStatus.RETURNED_TO_SENDER.getDescription())))
                .toList();

        log.info("Запущено обновление всех {} треков для userId={}", parcelsToUpdate.size(), userId);

        // Отправляем уведомление о запуске
        webSocketController.sendUpdateStatus(userId, "Обновление всех треков запущено...", true);

        // Запуск асинхронного процесса
        processAllTrackUpdatesAsync(userId, parcelsToUpdate);

        return new UpdateResult(true, parcelsToUpdate.size(), allParcels.size(),
                "Запущено обновление " + parcelsToUpdate.size() + " треков из " + allParcels.size());
    }

    @Async
    @Transactional
    public void processAllTrackUpdatesAsync(Long userId, List<TrackParcelDTO> parcelsToUpdate) {
        try {
            AtomicInteger successfulUpdates = new AtomicInteger(0);

            List<CompletableFuture<Void>> futures = parcelsToUpdate.stream()
                    .map(trackParcelDTO -> typeDefinitionTrackPostService
                            .getTypeDefinitionTrackPostServiceAsync(userId, trackParcelDTO.getNumber())
                            .thenAccept(trackInfoListDTO -> {
                                List<TrackInfoDTO> list = trackInfoListDTO.getList();
                                TrackParcel trackParcel = trackParcelRepository.findByNumberAndUserId(trackParcelDTO.getNumber(), userId);

                                if (trackParcel != null && !list.isEmpty()) {
                                    trackParcel.setStatus(statusTrackService.setStatus(list));
                                    trackParcel.setData(list.get(0).getTimex());
                                    trackParcelRepository.save(trackParcel);

                                    log.debug("✅ Обновлен статус посылки {} для пользователя ID: {}", trackParcelDTO.getNumber(), userId);

                                    // Увеличиваем счётчик успешных обновлений
                                    successfulUpdates.incrementAndGet();

                                }
                            })
                            .exceptionally(ex -> {
                                log.error("❌ Ошибка обновления трека {} для userId={}: {}", trackParcelDTO.getNumber(), userId, ex.getMessage());
                                return null;
                            })
                    )
                    .toList();

            // Ждём завершения всех обновлений
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {
                int updatedCount = successfulUpdates.get();
                int totalCount = parcelsToUpdate.size();

                log.info("✅ Итог обновления всех треков для userId={}: {} обновлено, {} не изменено",
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
            log.error("❌ Ошибка при обновлении всех треков для пользователя {}: {}", userId, e.getMessage());
            webSocketController.sendUpdateStatus(userId, "Ошибка при обновлении всех треков: " + e.getMessage(), false);
        }
    }

    @Transactional
    public UpdateResult updateSelectedParcels(Long userId, List<String> selectedNumbers) {
        // Загружаем посылки
        List<TrackParcel> selectedParcels = trackParcelRepository.findByNumberInAndUserId(selectedNumbers, userId);

        // Считаем количество финальных и обновляемых треков
        int totalRequested = selectedParcels.size();
        List<TrackParcel> updatableParcels = selectedParcels.stream()
                .filter(parcel -> !(parcel.getStatus().equals(GlobalStatus.DELIVERED.getDescription()) ||
                        parcel.getStatus().equals(GlobalStatus.RETURNED_TO_SENDER.getDescription())))
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

        // Проверяем лимит
        int remainingUpdates = subscriptionService.canUpdateTracks(userId, updatableParcels.size());

        if (remainingUpdates <= 0) {
            String msg = "Ваш лимит обновлений на сегодня исчерпан.";
            log.warn("Лимит обновлений исчерпан для пользователя ID: {}", userId);
            webSocketController.sendUpdateStatus(userId, msg, true);
            return new UpdateResult(false, 0, updatableParcels.size(), msg);
        }

        int updatesToProcess = Math.min(updatableParcels.size(), remainingUpdates);

        // Преобразуем в список номеров для передачи в асинхронный процесс
        List<String> updatableNumbers = updatableParcels.stream().map(TrackParcel::getNumber).toList();

        log.info("Запущено обновление {} треков для userId={}", updatesToProcess, userId);

        // Вызов асинхронного метода
        processTrackUpdatesAsync(userId, updatableNumbers.subList(0, updatesToProcess), totalRequested, nonUpdatableCount);
        return new UpdateResult(true, updatesToProcess, selectedNumbers.size(),
                "Обновление запущено...");
    }

    @Async
    @Transactional
    public void processTrackUpdatesAsync(Long userId, List<String> selectedNumbers, int totalRequested, int nonUpdatableCount) {
        try {
            List<TrackParcel> parcelsToUpdate = trackParcelRepository.findByNumberInAndUserId(selectedNumbers, userId);

            AtomicInteger successfulUpdates = new AtomicInteger(0);

            log.info(" Начато обновление {} треков для userId={}", parcelsToUpdate.size(), userId);

            List<CompletableFuture<Void>> futures = parcelsToUpdate.stream()
                    .map(parcel -> typeDefinitionTrackPostService
                            .getTypeDefinitionTrackPostServiceAsync(userId, parcel.getNumber())
                            .thenAccept(trackInfoListDTO -> {
                                List<TrackInfoDTO> list = trackInfoListDTO.getList();
                                TrackParcel trackParcel = trackParcelRepository.findByNumberAndUserId(parcel.getNumber(), userId);
                                if (trackParcel != null && !list.isEmpty()) {
                                    trackParcel.setStatus(statusTrackService.setStatus(list));
                                    trackParcel.setData(list.get(0).getTimex());
                                    trackParcelRepository.save(trackParcel);
                                    log.info(" Обновлено: userId={}, трек={}", userId, parcel.getNumber());

                                    // Увеличиваем успешный счётчик
                                    successfulUpdates.incrementAndGet();
                                }
                            })
                    )
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {
                int updatedCount = successfulUpdates.get();

                log.info("✅ Итог обновления для userId={}: {} обновлено, {} в финальном статусе",
                        userId, updatedCount, nonUpdatableCount);

                if (updatedCount > 0) {
                    log.info("🔄 Финальное обновление updateCount для userId={}, добавляем={}", userId, updatedCount);
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
            log.error("❌ Ошибка при обновлении посылок пользователя {}: {}", userId, e.getMessage());
            webSocketController.sendUpdateStatus(userId,"Ошибка обновления: " + e.getMessage(), false);
        }
    }

    /**
     * Удаляет посылки пользователя по номерам.
     * <p>
     * Этот метод удаляет посылки по номерам и идентификатору пользователя.
     * </p>
     *
     * @param numbers список номеров посылок
     * @param userId идентификатор пользователя
     */
    public void deleteByNumbersAndUserId(List<String> numbers, Long userId) {
        List<TrackParcel> parcelsToDelete = trackParcelRepository.findByNumberInAndUserId(numbers, userId);
        if (parcelsToDelete.isEmpty()) {
            throw new RuntimeException("Нет посылок для удаления.");
        }
        trackParcelRepository.deleteAll(parcelsToDelete);
    }
}