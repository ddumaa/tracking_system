package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.entity.*;
import com.project.tracking_system.repository.*;
import com.project.tracking_system.service.analytics.TrackStatisticsUpdater;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.service.analytics.DeliveryHistoryService;
import com.project.tracking_system.service.customer.CustomerService;
import com.project.tracking_system.service.customer.CustomerStatsService;
import com.project.tracking_system.service.user.UserService;
import com.project.tracking_system.utils.DateParserUtils;
import com.project.tracking_system.utils.PhoneUtils;
import com.project.tracking_system.utils.TrackNumberUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
    private final TrackStatisticsUpdater trackStatisticsUpdater;

    /** Менеджер сущностей для синхронизации состояния покупателя с базой данных. */
    @PersistenceContext
    private EntityManager entityManager;

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
        number = TrackNumberUtils.normalize(number); // Приводим к единообразному виду

        log.debug("Начата обработка трека");

        // Получаем данные о треке
        TrackInfoListDTO trackInfo = typeDefinitionTrackPostService.getTypeDefinitionTrackPostService(userId, number);

        if (trackInfo == null || trackInfo.getList().isEmpty()) {
            log.debug("Информация по треку отсутствует");
            return trackInfo;
        }

        // Сохраняем трек, если пользователь авторизован и разрешено сохранять
        if (userId != null && canSave) {
            save(number, trackInfo, storeId, userId, phone);
            log.debug("Посылка сохранена");
        } else {
            log.debug("Трек обработан без сохранения");
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
        // Делегируем основной логике, где номер будет нормализован
        save(number, trackInfoListDTO, storeId, userId, null);
    }

    /**
     * Сохраняет или обновляет посылку пользователя и привязывает её к покупателю.
     * <p>
     * Номер предварительно нормализуется: приводится к верхнему регистру
     * и обрезаются пробелы по краям.
     * </p>
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
        log.debug("Начало сохранения трека");
        if (number == null || trackInfoListDTO == null) {
            throw new IllegalArgumentException("Отсутствует посылка");
        }

        // Приведение номера к единому виду
        number = TrackNumberUtils.normalize(number);

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
            log.debug("Создан новый трек");

        } else {
            // Запоминаем предыдущие значения для корректировки статистики
            previousStoreId = trackParcel.getStore().getId();
            previousDate = trackParcel.getTimestamp();
        }
        // Если трек уже существует, проверяем, соответствует ли магазин выбранному пользователем
        if (!trackParcel.getStore().getId().equals(storeId)) {
            // Загружаем новый магазин
            Long oldStoreId = trackParcel.getStore().getId();
            Store newStore = storeRepository.getReferenceById(storeId);

            // Обновляем магазин у трека
            trackParcel.setStore(newStore);
            log.debug("Обновлён магазин трека");
        }

        // Для предварительно зарегистрированного трека без статусов сохраняем текущее состояние
        if (!isNewParcel && trackParcel.isPreRegistered() && trackInfoListDTO.getList().isEmpty()) {
            log.debug("Статусы не получены, изменения не применены");
            return;
        }

        // Обновляем статус и дату трека на основе нового содержимого
        GlobalStatus newStatus = statusTrackService.setStatus(trackInfoListDTO.getList());

        trackParcel.setStatus(newStatus);

        String lastDate = trackInfoListDTO.getList().get(0).getTimex();
        ZoneId userZone = userService.getUserZone(userId);
        ZonedDateTime zonedDateTime = DateParserUtils.parse(lastDate, userZone);
        trackParcel.setTimestamp(zonedDateTime);
        // фиксируем время обновления в UTC
        trackParcel.setLastUpdate(ZonedDateTime.now(ZoneOffset.UTC));

        // Привязываем покупателя, если указан телефон
        Customer previousCustomer = trackParcel.getCustomer();
        Customer customer = null;
        if (phone != null && !phone.isBlank()) {
            try {
                customer = customerService.registerOrGetByPhone(phone);
                trackParcel.setCustomer(customer);
            } catch (ResponseStatusException ex) {
                // Логируем и пробрасываем исключение для корректного ответа клиенту
                log.warn("Ошибочный телефон {} при сохранении трека: {}",
                        PhoneUtils.maskPhone(phone), ex.getReason());
                throw ex;
            }
        }

        trackParcelRepository.save(trackParcel);

        // Если изменён покупатель и посылка имеет финальный статус,
        // нужно пересчитать статистику покупателя
        boolean customerChanged = customer != null && (previousCustomer == null || !previousCustomer.getId().equals(customer.getId()));
        if (customerChanged && deliveryHistoryService.hasFinalStatus(trackParcel.getId())) {
            deliveryHistoryService.registerFinalStatus(trackParcel.getId());
        }

        if (isNewParcel && customer != null) {
            customerStatsService.incrementSent(customer);
            // Обновляем данные покупателя, чтобы получить актуальные счётчики
            entityManager.refresh(customer);
        }

        trackStatisticsUpdater.updateStatistics(trackParcel, isNewParcel, previousStoreId, previousDate);

        // Обновляем историю доставки
        deliveryHistoryService.updateDeliveryHistory(trackParcel, oldStatus, newStatus, trackInfoListDTO);

        log.debug("Трек обновлён");
    }

}