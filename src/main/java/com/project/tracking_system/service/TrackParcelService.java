package com.project.tracking_system.service;

import com.project.tracking_system.dto.TrackParcelDTO;
import com.project.tracking_system.dto.TrackInfoDTO;
import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.model.GlobalStatus;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.repository.UserRepository;
import com.project.tracking_system.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

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

    private final TrackParcelRepository trackParcelRepository;
    private final UserService userService;
    private final UserRepository userRepository;
    private final TypeDefinitionTrackPostService typeDefinitionTrackPostService;
    private final StatusTrackService statusTrackService;

    private final Map<Long, AtomicBoolean> updateStatusMap = new ConcurrentHashMap<>(); // Храним статус по userId
    private final Map<Long, String> lastErrorMessages = new ConcurrentHashMap<>();

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
    public void save(String number, TrackInfoListDTO trackInfoListDTO, Long userId) {
        if (number == null || trackInfoListDTO == null) {
            throw new IllegalArgumentException("Отсутствует посылка");
        }

        List<TrackInfoDTO> trackInfoDTOList = trackInfoListDTO.getList();

        // Проверка на ограничения для бесплатных пользователей
        userService.validateFreeUserLimit(userId);

        // Ищем посылку по номеру отслеживания и пользователю
        TrackParcel trackParcel = trackParcelRepository.findByNumberAndUserId(number, userId);

        if (trackParcel == null) {
            trackParcel = new TrackParcel();
            trackParcel.setNumber(number);
            trackParcel.setUser(userRepository.getReferenceById(userId)); // Lazy загрузка пользователя
        }

        // Обновляем статус и дату посылки
        trackParcel.setStatus(statusTrackService.setStatus(trackInfoDTOList));
        trackParcel.setData(trackInfoDTOList.get(0).getTimex());

        // Сохраняем запись в базу данных
        trackParcelRepository.save(trackParcel);
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
    public List<TrackParcelDTO> findAllByUserTracks(Long userId) {
        List<TrackParcel> trackParcels = trackParcelRepository.findByUserId(userId);
        return convertToDTO(trackParcels);
    }

    public List<TrackParcelDTO> findByUserTracksByNumbers(Long userId, List<String> numbers) {
        List<TrackParcel> trackParcels = trackParcelRepository.findByNumberInAndUserId(numbers, userId);
        return convertToDTO(trackParcels);
    }

    public long countAllParcels() {
        return trackParcelRepository.count();
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

    // Метод для получения последней ошибки
    public String getLastErrorMessage(Long userId) {
        String error = lastErrorMessages.get(userId);
        log.debug("getLastErrorMessage: userId={}, error={}", userId, error); // ✅ Логируем, есть ли ошибка перед возвратом
        lastErrorMessages.remove(userId);
        return error;
    }

    /**
     * Обновляет историю отслеживания посылок пользователя асинхронно.
     * <p>
     * Этот метод обновляет статус и данные всех посылок пользователя, вызывая асинхронные запросы к сервису {@link TypeDefinitionTrackPostService}.
     * </p>
     *
     * @param name имя пользователя
     */
    public void updateHistory(Long userId) {
        // Проверяем, является ли пользователь платным
        boolean isPaidUser = userService.isUserPaid(userId);
        if (!isPaidUser) {
            throw new AccessDeniedException("Обновить всё - Только для платных пользователей.");
        }

        List<TrackParcelDTO> byUserTrack = findAllByUserTracks(userId);
        log.info("Запуск обновления истории посылок для пользователя с ID: {}", userId);

        List<CompletableFuture<Void>> futures = byUserTrack.stream()
                .filter(trackParcelDTO -> !(trackParcelDTO.getStatus().equals(GlobalStatus.DELIVERED.getDescription()) ||
                        trackParcelDTO.getStatus().equals(GlobalStatus.RETURNED_TO_SENDER.getDescription())))
                .map(trackParcelDTO -> typeDefinitionTrackPostService
                        .getTypeDefinitionTrackPostServiceAsync(userId, trackParcelDTO.getNumber())
                        .thenAccept(trackInfoListDTO -> {
                            List<TrackInfoDTO> list = trackInfoListDTO.getList();
                            TrackParcel trackParcel = trackParcelRepository.findByNumberAndUserId(trackParcelDTO.getNumber(), userId);
                            trackParcel.setStatus(statusTrackService.setStatus(list));
                            trackParcel.setData(list.get(0).getTimex());
                            trackParcelRepository.save(trackParcel);
                            log.info("Обновлен статус посылки {} для пользователя с ID: {}", trackParcelDTO.getNumber(), userId);
                        })
                ).toList();

        // Ожидание завершения всех асинхронных задач
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        log.info("Обновление истории посылок завершено для пользователя с ID: {}", userId);
    }

    public void updateSelectedParcels(Long userId, List<String> selectedNumbers) {
        lastErrorMessages.remove(userId);

        try {
            boolean isFreeUser = !userService.isUserPaid(userId);
            ZonedDateTime currentDate = ZonedDateTime.now(ZoneOffset.UTC);

            if (isFreeUser) {
                int updateCount = userService.getUpdateCount(userId);
                ZonedDateTime lastUpdate = userService.getLastUpdateDate(userId);

                if (lastUpdate != null && lastUpdate.toLocalDate().equals(currentDate.toLocalDate())) {
                    if (updateCount >= 10) {
                        log.warn("Лимит обновлений исчерпан для пользователя ID: {}", userId);
                        lastErrorMessages.put(userId, "Ваш бесплатный лимит в день исчерпан.");
                        throw new IllegalStateException("Ваш бесплатный лимит в день исчерпан.");
                    }
                } else {
                    userService.resetUpdateCount(userId);
                }
            }

            updateStatusMap.put(userId, new AtomicBoolean(false));

            CompletableFuture.runAsync(() -> {
                try {
                    List<TrackParcelDTO> selectedParcels = findByUserTracksByNumbers(userId, selectedNumbers);

                    List<CompletableFuture<Void>> futures = selectedParcels.stream()
                            .map(parcel -> typeDefinitionTrackPostService
                                    .getTypeDefinitionTrackPostServiceAsync(userId, parcel.getNumber())
                                    .thenAccept(trackInfoListDTO -> {
                                        List<TrackInfoDTO> list = trackInfoListDTO.getList();
                                        TrackParcel trackParcel = trackParcelRepository.findByNumberAndUserId(parcel.getNumber(), userId);

                                        if (trackParcel != null) {
                                            trackParcel.setStatus(statusTrackService.setStatus(list));
                                            trackParcel.setData(list.get(0).getTimex());
                                            trackParcelRepository.save(trackParcel);
                                        }
                                    })
                            ).toList();

                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {
                        updateStatusMap.get(userId).set(true);
                        lastErrorMessages.remove(userId); // Очистить ошибку при успешном обновлении
                        if (isFreeUser) {
                            userService.incrementUpdateCount(userId, selectedNumbers.size());
                        }
                    });

                } catch (Exception e) {
                    log.error("Ошибка при обновлении посылок пользователя {}: {}", userId, e.getMessage());
                    lastErrorMessages.put(userId, "Произошла ошибка обновления: " + e.getMessage());
                    updateStatusMap.get(userId).set(true);
                }
            });

        } catch (IllegalStateException e) {
            log.warn("Ошибка бизнес-логики (лимит) для пользователя {}: {}", userId, e.getMessage());
            lastErrorMessages.put(userId, e.getMessage());
            throw e;
        }
    }

    public boolean isUpdateCompleted(Long userId) {
        return updateStatusMap.getOrDefault(userId, new AtomicBoolean(true)).get();
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