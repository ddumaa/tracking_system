package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackParcelDTO;
import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.entity.DeliveryHistory;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.PostalServiceType;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.repository.UserSubscriptionRepository;
import com.project.tracking_system.repository.DeliveryHistoryRepository;
import com.project.tracking_system.service.user.UserService;
import com.project.tracking_system.service.track.TrackServiceClassifier;
import com.project.tracking_system.service.track.TrackNumberAuditService;
import com.project.tracking_system.utils.NameSearchUtils;
import com.project.tracking_system.utils.PhoneUtils;
import com.project.tracking_system.utils.TrackNumberUtils;
import com.project.tracking_system.exception.TrackNumberAlreadyExistsException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Базовый сервис для работы с посылками пользователя.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class TrackParcelService {

    private final UserService userService;
    private final TrackParcelRepository trackParcelRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final TrackServiceClassifier trackServiceClassifier;
    private final DeliveryHistoryRepository deliveryHistoryRepository;
    private final TrackNumberAuditService trackNumberAuditService;

    /**
     * Возвращает тип почтовой службы для сохранённой посылки.
     *
     * @param number номер посылки
     * @return тип службы или {@code null}, если посылка не найдена
     */
    @Transactional(readOnly = true)
    public PostalServiceType getPostalServiceType(String number) {
        TrackParcel parcel = trackParcelRepository.findByNumberWithStoreAndUser(number);
        if (parcel != null && parcel.getDeliveryHistory() != null) {
            return parcel.getDeliveryHistory().getPostalService();
        }
        return null;
    }

    /**
     * Возвращает все посылки пользователя в рамках указанного эпизода.
     * <p>
     * Метод инкапсулирует доступ к репозиторию и гарантирует, что в
     * результирующий список попадут только посылки владельца, что
     * упрощает повторное использование логики формирования цепочек (SRP).
     * </p>
     *
     * @param episodeId идентификатор эпизода заказа
     * @param userId    идентификатор пользователя
     * @return отсортированный список посылок эпизода или пустой список
     */
    @Transactional(readOnly = true)
    public List<TrackParcel> findEpisodeParcels(Long episodeId, Long userId) {
        if (episodeId == null || userId == null) {
            return List.of();
        }
        return trackParcelRepository.findByEpisodeIdAndUserIdOrderByTimestampAsc(episodeId, userId);
    }

    /**
     * Проверяет, принадлежит ли посылка пользователю.
     *
     * @param itemNumber номер посылки
     * @param userId     идентификатор пользователя
     * @return true, если посылка принадлежит пользователю
     */
    @Transactional(readOnly = true)
    public boolean userOwnsParcel(String itemNumber, Long userId) {
        return trackParcelRepository.existsByNumberAndUserId(itemNumber, userId);
    }

    /**
     * Находит посылки по магазинам с учётом пагинации.
     *
     * @param storeIds список идентификаторов магазинов
     * @param page     номер страницы
     * @param size     размер страницы
     * @param userId    идентификатор пользователя
     * @param sortOrder порядок сортировки: {@code "asc"} или {@code "desc"}
     * @return страница посылок указанного пользователя
     */
    @Transactional(readOnly = true)
    public Page<TrackParcelDTO> findByStoreTracks(List<Long> storeIds,
                                                  int page,
                                                  int size,
                                                  Long userId,
                                                  String sortOrder) {
        Sort sort = Sort.by("timestamp");
        sort = "asc".equalsIgnoreCase(sortOrder) ? sort.ascending() : sort.descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<TrackParcel> trackParcels = trackParcelRepository.findByStoreIdIn(storeIds, pageable);
        ZoneId userZone = userService.getUserZone(userId);
        return trackParcels.map(track -> toDto(track, userZone));
    }

    /**
     * Ищет посылки магазинов по статусу с поддержкой пагинации.
     *
     * @param storeIds список идентификаторов магазинов
     * @param status   статус посылки
     * @param page     номер страницы
     * @param size     размер страницы
     * @param userId    идентификатор пользователя
     * @param sortOrder порядок сортировки: {@code "asc"} или {@code "desc"}
     * @return страница посылок
     */
    @Transactional(readOnly = true)
    public Page<TrackParcelDTO> findByStoreTracksAndStatus(List<Long> storeIds,
                                                          GlobalStatus status,
                                                          int page,
                                                          int size,
                                                          Long userId,
                                                          String sortOrder) {
        Sort sort = Sort.by("timestamp");
        sort = "asc".equalsIgnoreCase(sortOrder) ? sort.ascending() : sort.descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<TrackParcel> trackParcels = trackParcelRepository.findByStoreIdInAndStatus(storeIds, status, pageable);
        ZoneId userZone = userService.getUserZone(userId);
        return trackParcels.map(track -> toDto(track, userZone));
    }

    /**
     * Возвращает страницу предзарегистрированных посылок.
     *
     * @param page      номер страницы
     * @param size      размер страницы
     * @param sortOrder порядок сортировки: {@code "asc"} или {@code "desc"}
     * @return страница предзарегистрированных посылок
     */
    @Transactional(readOnly = true)
    public Page<TrackParcelDTO> findPreRegistered(int page,
                                                  int size,
                                                  String sortOrder) {
        Sort sort = Sort.by("timestamp");
        sort = "asc".equalsIgnoreCase(sortOrder) ? sort.ascending() : sort.descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<TrackParcel> parcels = trackParcelRepository.findByPreRegisteredTrue(pageable);
        return parcels.map(track -> toDto(
                track,
                userService.getUserZone(track.getUser().getId())));
    }

    /**
     * Находит посылки со статусом {@link GlobalStatus#PRE_REGISTERED} или отмеченные как предзарегистрированные.
     * <p>
     * Метод объединяет результаты двух запросов:
     * <ul>
     *     <li>посылки выбранных магазинов в статусе {@code PRE_REGISTERED};</li>
     *     <li>посылки с флагом {@code preRegistered=true} тех же магазинов.</li>
     * </ul>
     * Результат сортируется по времени добавления и постранично возвращается пользователю.
     * </p>
     *
     * @param storeIds  идентификаторы магазинов
     * @param page      номер страницы
     * @param size      размер страницы
     * @param userId    идентификатор пользователя
     * @param sortOrder порядок сортировки: {@code "asc"} или {@code "desc"}
     * @return страница подходящих посылок
     */
    @Transactional(readOnly = true)
    public Page<TrackParcelDTO> findByStoreTracksWithPreRegistered(List<Long> storeIds,
                                                                   int page,
                                                                   int size,
                                                                   Long userId,
                                                                   String sortOrder) {
        Sort sort = Sort.by("timestamp");
        sort = "asc".equalsIgnoreCase(sortOrder) ? sort.ascending() : sort.descending();

        // Загружаем все посылки в статусе PRE_REGISTERED для указанных магазинов
        List<TrackParcel> statusParcels = trackParcelRepository
                .findByStoreIdInAndStatus(storeIds, GlobalStatus.PRE_REGISTERED, Pageable.unpaged())
                .getContent();

        // Загружаем все посылки с флагом preRegistered=true и фильтруем по магазинам
        List<TrackParcel> preRegisteredParcels = trackParcelRepository
                .findByPreRegisteredTrue(Pageable.unpaged())
                .stream()
                .filter(parcel -> storeIds.contains(parcel.getStore().getId()))
                .toList();

        // Объединяем результаты без дубликатов по идентификатору
        Map<Long, TrackParcel> mergedMap = new LinkedHashMap<>();
        statusParcels.forEach(parcel -> mergedMap.put(parcel.getId(), parcel));
        preRegisteredParcels.forEach(parcel -> mergedMap.put(parcel.getId(), parcel));
        List<TrackParcel> merged = new ArrayList<>(mergedMap.values());

        // Сортируем объединённый список
        Comparator<TrackParcel> comparator = Comparator.comparing(TrackParcel::getTimestamp);
        if (sort.isSorted() && sort.iterator().next().isDescending()) {
            comparator = comparator.reversed();
        }
        merged.sort(comparator);

        // Формируем страницу результатов
        int start = Math.min(page * size, merged.size());
        int end = Math.min(start + size, merged.size());
        ZoneId userZone = userService.getUserZone(userId);
        List<TrackParcelDTO> content = merged.subList(start, end)
                .stream()
                .map(track -> toDto(track, userZone))
                .toList();

        return new PageImpl<>(content, PageRequest.of(page, size, sort), merged.size());
    }

    /**
     * Возвращает посылки в указанном статусе без учёта магазина.
     *
     * @param status    статус посылки
     * @param page      номер страницы
     * @param size      размер страницы
     * @param sortOrder порядок сортировки: {@code "asc"} или {@code "desc"}
     * @return страница посылок в заданном статусе
     */
    @Transactional(readOnly = true)
    public Page<TrackParcelDTO> findByStatus(GlobalStatus status,
                                             int page,
                                             int size,
                                             String sortOrder) {
        Sort sort = Sort.by("timestamp");
        sort = "asc".equalsIgnoreCase(sortOrder) ? sort.ascending() : sort.descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<TrackParcel> parcels = trackParcelRepository.findByStatus(status, pageable);
        return parcels.map(track -> toDto(
                track,
                userService.getUserZone(track.getUser().getId())));
    }

    /**
     * Выполняет поиск посылок по номеру, телефону или ФИО покупателя.
     *
     * @param storeIds список магазинов
     * @param status   фильтр статуса (может быть {@code null})
     * @param query    строка поиска
     * @param page     номер страницы
     * @param size     размер страницы
     * @param userId    идентификатор пользователя
     * @param sortOrder порядок сортировки: {@code "asc"} или {@code "desc"}
     * @return страница найденных посылок
     */
    @Transactional(readOnly = true)
    public Page<TrackParcelDTO> searchByNumberPhoneOrName(List<Long> storeIds,
                                                          GlobalStatus status,
                                                          String query,
                                                          int page,
                                                          int size,
                                                          Long userId,
                                                          String sortOrder) {
        Sort sort = Sort.by("timestamp");
        sort = "asc".equalsIgnoreCase(sortOrder) ? sort.ascending() : sort.descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        String phoneDigits = PhoneUtils.extractDigits(query);
        List<String> nameTokens = NameSearchUtils.extractNameTokens(query);
        Page<TrackParcel> parcels = trackParcelRepository.searchByNumberPhoneOrName(
                storeIds,
                userId,
                status,
                query,
                phoneDigits,
                NameSearchUtils.getTokenOrEmpty(nameTokens, 0),
                NameSearchUtils.getTokenOrEmpty(nameTokens, 1),
                NameSearchUtils.getTokenOrEmpty(nameTokens, 2),
                pageable);
        ZoneId userZone = userService.getUserZone(userId);
        return parcels.map(track -> toDto(track, userZone));
    }

    /**
     * Подсчитывает общее количество посылок в системе.
     *
     * @return количество всех посылок
     */
    @Transactional(readOnly = true)
    public long countAllParcels() {
        return trackParcelRepository.count();
    }

    /**
     * Проверяет, является ли посылка новой для указанного магазина.
     *
     * @param trackingNumber номер отслеживания
     * @param storeId        идентификатор магазина (может быть {@code null})
     * @return {@code true}, если такой посылки ещё нет в магазине
     */
    @Transactional(readOnly = true)
    public boolean isNewTrack(String trackingNumber, Long storeId) {
        if (storeId == null) {
            return true;
        }
        TrackParcel existing = trackParcelRepository.findByNumberAndStoreId(trackingNumber, storeId);
        return (existing == null);
    }

    /**
     * Увеличивает счётчик обновлений треков для пользователя.
     *
     * @param userId идентификатор пользователя
     * @param count  величина увеличения счётчика
     */
    @Transactional
    public void incrementUpdateCount(Long userId, int count) {
        userSubscriptionRepository.incrementUpdateCount(userId, count, LocalDate.now(ZoneOffset.UTC));
    }

    /**
     * Возвращает посылку по номеру и пользователю.
     * <p>
     * Используется для проверки времени последнего обновления
     * и извлечения связанных данных.
     * </p>
     *
     * @param number номер посылки
     * @param userId идентификатор пользователя
     * @return посылка или {@code null}, если не найдена
     */
    @Transactional(readOnly = true)
    public TrackParcel findByNumberAndUserId(String number, Long userId) {
        return trackParcelRepository.findByNumberAndUserId(number, userId);
    }

    /**
     * Загружает посылку по идентификатору и проверяет принадлежность пользователю.
     * <p>
     * Метод возвращает {@link Optional}, чтобы вызывающая сторона могла явно
     * обработать отсутствие посылки или чужую собственность без выброса
     * исключений внутри сервиса (принцип ISP/DIP).
     * </p>
     *
     * @param parcelId идентификатор посылки
     * @param userId   идентификатор владельца
     * @return посылка, если принадлежит пользователю
     */
    @Transactional(readOnly = true)
    public Optional<TrackParcel> findOwnedById(Long parcelId, Long userId) {
        TrackParcel parcel = trackParcelRepository.findByIdWithStoreAndUser(parcelId);
        if (parcel == null || parcel.getUser() == null || !parcel.getUser().getId().equals(userId)) {
            return Optional.empty();
        }
        return Optional.of(parcel);
    }

    /**
     * Возвращает посылку по идентификатору без проверки владельца.
     * Используется для дополнительной валидации и не должен раскрывать
     * данные чужих пользователей наружу.
     */
    @Transactional(readOnly = true)
    public Optional<TrackParcel> findById(Long parcelId) {
        return Optional.ofNullable(trackParcelRepository.findByIdWithStoreAndUser(parcelId));
    }

    /**
     * Возвращает все посылки указанного магазина.
     *
     * @param storeId идентификатор магазина
     * @param userId  идентификатор пользователя
     * @return список посылок магазина
     */
    @Transactional(readOnly = true)
    public List<TrackParcelDTO> findAllByStoreTracks(Long storeId, Long userId) {
        List<TrackParcel> trackParcels = trackParcelRepository.findByStoreId(storeId);
        ZoneId userZone = userService.getUserZone(userId);
        return trackParcels.stream()
                .map(track -> toDto(track, userZone))
                .toList();
    }

    /**
     * Возвращает все посылки пользователя.
     *
     * @param userId идентификатор пользователя
     * @return список всех его посылок
     */
    @Transactional(readOnly = true)
    public List<TrackParcelDTO> findAllByUserTracks(Long userId) {
        List<TrackParcel> trackParcels = trackParcelRepository.findByUserId(userId);
        ZoneId userZone = userService.getUserZone(userId);
        return trackParcels.stream()
                .map(track -> toDto(track, userZone))
                .toList();
    }

    /**
     * Получить все посылки пользователя, отсортированные по дате создания.
     * <p>
     * Порядок сортировки задаётся параметром {@code sortOrder} и может быть
     * восходящим ({@code "asc"}) или нисходящим ({@code "desc"}).
     * </p>
     *
     * @param userId    идентификатор пользователя
     * @param sortOrder порядок сортировки: {@code "asc"} или {@code "desc"}
     * @return список отсортированных посылок
     */
    @Transactional(readOnly = true)
    public List<TrackParcelDTO> getParcelsSortedByDate(Long userId, String sortOrder) {
        Sort sort = Sort.by("timestamp");
        sort = "asc".equalsIgnoreCase(sortOrder) ? sort.ascending() : sort.descending();

        List<TrackParcel> parcels = trackParcelRepository.findByUserId(userId, sort);
        ZoneId userZone = userService.getUserZone(userId);

        return parcels.stream()
                .map(track -> toDto(track, userZone))
                .toList();
    }

    /**
     * Присваивает трек-номер предварительно зарегистрированной посылке пользователя.
     * <p>
     * Метод проверяет принадлежность посылки пользователю и наличие статуса
     * предварительной регистрации. Дополнительно выполняется проверка
     * корректности формата и типа почтовой службы, а также уникальности
     * трек-номера для данного пользователя. В случае отсутствия
     * посылки или несоответствия владельца выбрасывается
     * {@link EntityNotFoundException}, а при обнаружении дубликата номера —
     * {@link TrackNumberAlreadyExistsException}. При неверном формате
     * трек-номера выбрасывается {@link IllegalArgumentException}.
     * </p>
     *
     * @param parcelId идентификатор посылки
     * @param number   трек-номер
     * @param userId   идентификатор пользователя
     */
    @Transactional
    public void assignTrackNumber(Long parcelId, String number, Long userId) {
        TrackParcel parcel = trackParcelRepository.findByIdAndPreRegisteredTrue(parcelId);
        if (parcel == null || parcel.getUser() == null || !parcel.getUser().getId().equals(userId)) {
            throw new EntityNotFoundException("Посылка не найдена");
        }
        TrackNumberValidation validation = validateTrackNumber(number, userId);
        trackParcelRepository.updatePreRegisteredNumber(parcelId, validation.normalized());
    }

    /**
     * Обновляет трек-номер для посылки в статусах PRE_REGISTERED или ERROR.
     * <p>
     * Выполняет проверки принадлежности посылки, уникальности номера и корректности
     * формата, сохраняет изменение в аудите и синхронизирует почтовую службу в истории
     * доставки, чтобы аналитика учитывала актуальные данные.
     * </p>
     *
     * @param parcelId идентификатор посылки
     * @param userId   идентификатор владельца
     * @param number   новое значение трек-номера
     * @return обновлённая сущность посылки
     */
    @Transactional
    @CacheEvict(cacheNames = "track-details", key = "#userId + ':' + #parcelId")
    public TrackParcel updateTrackNumber(Long parcelId, Long userId, String number) {
        TrackParcel parcel = trackParcelRepository.findByIdWithStoreAndUser(parcelId);
        if (parcel == null) {
            throw new EntityNotFoundException("Посылка не найдена");
        }
        if (parcel.getUser() == null || !parcel.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("Посылка не принадлежит пользователю");
        }
        GlobalStatus status = parcel.getStatus();
        if (status != GlobalStatus.PRE_REGISTERED && status != GlobalStatus.ERROR) {
            throw new IllegalStateException("Редактирование недоступно для текущего статуса");
        }

        TrackNumberValidation validation = validateTrackNumber(number, userId);
        String normalized = validation.normalized();
        String previousNumber = parcel.getNumber();
        if (previousNumber != null && previousNumber.equals(normalized)) {
            throw new IllegalArgumentException("Новый трек-номер совпадает с текущим");
        }

        PostalServiceType type = validation.type();
        parcel.setNumber(normalized);
        parcel.setLastUpdate(ZonedDateTime.now(ZoneOffset.UTC));
        trackParcelRepository.save(parcel);

        updateDeliveryHistoryServiceType(parcel, type);
        trackNumberAuditService.recordChange(parcel, previousNumber, normalized, userId);
        return parcel;
    }

    /**
     * Преобразует посылку в DTO для указанного пользователя.
     *
     * @param parcel сущность посылки
     * @param userId идентификатор пользователя
     * @return DTO с учётом часового пояса пользователя
     */
    @Transactional(readOnly = true)
    public TrackParcelDTO mapToDto(TrackParcel parcel, Long userId) {
        ZoneId userZone = userService.getUserZone(userId);
        return toDto(parcel, userZone);
    }

    /**
     * Выполняет общую валидацию трек-номера и проверку уникальности.
     */
    private TrackNumberValidation validateTrackNumber(String number, Long userId) {
        String normalized = TrackNumberUtils.normalize(number);
        if (normalized == null || normalized.isBlank()) {
            throw new IllegalArgumentException("Трек-номер не может быть пустым");
        }
        PostalServiceType type = trackServiceClassifier.detect(normalized);
        if (type == PostalServiceType.UNKNOWN) {
            throw new IllegalArgumentException("Указан некорректный код посылки");
        }
        if (trackParcelRepository.existsByNumberAndUserId(normalized, userId)) {
            throw new TrackNumberAlreadyExistsException("Трек-номер уже привязан к другой посылке");
        }
        return new TrackNumberValidation(normalized, type);
    }

    /**
     * Обновляет почтовую службу в истории доставки, чтобы аналитика
     * опиралась на актуальный тип службы после изменения номера.
     */
    private void updateDeliveryHistoryServiceType(TrackParcel parcel, PostalServiceType type) {
        if (type == PostalServiceType.UNKNOWN) {
            return;
        }
        deliveryHistoryRepository.findByTrackParcelId(parcel.getId())
                .ifPresent(history -> synchronizeHistoryPostalService(history, type));
    }

    /**
     * Сохраняет обновлённую почтовую службу в истории доставки.
     */
    private void synchronizeHistoryPostalService(DeliveryHistory history, PostalServiceType type) {
        history.setPostalService(type);
        deliveryHistoryRepository.save(history);
    }

    /**
     * Вспомогательная запись для возврата результата валидации номера.
     */
    private record TrackNumberValidation(String normalized, PostalServiceType type) {
    }

    /**
     * Преобразует сущность TrackParcel в DTO с данными покупателя.
     *
     * @param track    исходная сущность
     * @param userZone часовой пояс пользователя
     * @return заполненный {@link TrackParcelDTO}
     */
    private TrackParcelDTO toDto(TrackParcel track, ZoneId userZone) {
        TrackParcelDTO dto = new TrackParcelDTO(track, userZone);
        Customer customer = track.getCustomer();
        if (customer != null) {
            dto.setCustomerName(customer.getFullName());
            dto.setCustomerPhone(customer.getPhone());
            dto.setNameSource(customer.getNameSource());
        }
        if (track.getStatus() != null) {
            dto.setIconHtml(track.getStatus().getIconHtml());
        }
        return dto;
    }

}