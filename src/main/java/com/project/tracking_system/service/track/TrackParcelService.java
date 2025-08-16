package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackParcelDTO;
import com.project.tracking_system.entity.Customer;
import com.project.tracking_system.entity.GlobalStatus;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.PostalServiceType;
import com.project.tracking_system.repository.TrackParcelRepository;
import com.project.tracking_system.repository.UserSubscriptionRepository;
import com.project.tracking_system.service.user.UserService;
import com.project.tracking_system.utils.PhoneUtils;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
     * Выполняет поиск посылок по номеру или номеру телефона покупателя.
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
    public Page<TrackParcelDTO> searchByNumberOrPhone(List<Long> storeIds,
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
        Page<TrackParcel> parcels = trackParcelRepository.searchByNumberOrPhone(
                storeIds, userId, status, query, phoneDigits, pageable);
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
     * предварительной регистрации. В случае отсутствия посылки или
     * несоответствия владельца выбрасывается {@link EntityNotFoundException}.
     * </p>
     *
     * @param parcelId идентификатор посылки
     * @param number   трек-номер
     * @param userId   идентификатор пользователя
     */
    @Transactional
    public void assignTrackNumber(Long parcelId, String number, Long userId) {
        TrackParcel parcel = trackParcelRepository.findByIdAndPreRegisteredTrue(parcelId);
        if (parcel == null || !parcel.getUser().getId().equals(userId)) {
            throw new EntityNotFoundException("Посылка не найдена");
        }
        trackParcelRepository.updatePreRegisteredNumber(parcelId, number);
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
        return dto;
    }

}