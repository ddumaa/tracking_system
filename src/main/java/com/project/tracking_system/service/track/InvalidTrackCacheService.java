package com.project.tracking_system.service.track;

import com.project.tracking_system.service.admin.ApplicationSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис-кэш для хранения некорректных треков, сгруппированных по пользователям и батчам.
 * <p>
 * Позволяет восстановить таблицу ошибок после обновления страницы.
 * Записи автоматически удаляются по истечении TTL, полученного из
 * {@link ApplicationSettingsService}.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class InvalidTrackCacheService {

    /** Сервис получения настроек приложения. */
    private final ApplicationSettingsService applicationSettingsService;

    /** Карта вида userId -> (batchId -> запись кэша). */
    private final Map<Long, Map<Long, BatchEntry>> cache = new ConcurrentHashMap<>();

    /**
     * Добавляет список некорректных треков для указанного батча пользователя.
     * <p>
     * Данные хранятся в памяти и автоматически удаляются
     * после истечения настроенного TTL. Если переданные аргументы {@code null}
     * или список пуст, метод ничего не делает.
     * </p>
     *
     * @param userId  идентификатор владельца
     * @param batchId идентификатор батча
     * @param tracks  список некорректных треков
     */
    public void addInvalidTracks(Long userId, Long batchId, List<InvalidTrack> tracks) {
        if (userId == null || batchId == null || tracks == null || tracks.isEmpty()) {
            return;
        }
        cache
                .computeIfAbsent(userId, id -> new ConcurrentHashMap<>())
                .computeIfAbsent(batchId, id -> new BatchEntry())
                .addAll(tracks);
    }

    /**
     * Возвращает сохранённые некорректные треки конкретного батча пользователя.
     * <p>
     * Если запись истекла по TTL, она будет очищена при следующей
     * плановой проверке и вернётся пустой список.
     * </p>
     *
     * @param userId  идентификатор пользователя
     * @param batchId идентификатор батча
     * @return список некорректных треков либо пустой список
     */
    public List<InvalidTrack> getInvalidTracks(Long userId, Long batchId) {
        if (userId == null || batchId == null) {
            return List.of();
        }
        Map<Long, BatchEntry> byBatch = cache.get(userId);
        if (byBatch == null) {
            return List.of();
        }
        BatchEntry entry = byBatch.get(batchId);
        return entry != null ? entry.snapshot() : List.of();
    }

    /**
     * Возвращает некорректные треки последнего загруженного пользователем батча.
     * <p>
     * Последний батч определяется по наибольшему идентификатору,
     * присутствующему в кэше. Если все записи истекли, будет возвращён
     * пустой список.
     * </p>
     *
     * @param userId идентификатор пользователя
     * @return список некорректных треков либо пустой список
     */
    public List<InvalidTrack> getLatestInvalidTracks(Long userId) {
        Map<Long, BatchEntry> byBatch = cache.get(userId);
        if (byBatch == null || byBatch.isEmpty()) {
            return List.of();
        }
        Long latestBatchId = byBatch.keySet().stream().max(Long::compareTo).orElse(null);
        if (latestBatchId == null) {
            return List.of();
        }
        return getInvalidTracks(userId, latestBatchId);
    }

    /**
     * Удаляет все сохранённые некорректные треки указанного пользователя.
     * <p>
     * Метод можно вызвать при уходе пользователя со страницы либо вручную.
     * Запланированная очистка также удалит записи после истечения TTL.
     * </p>
     *
     * @param userId идентификатор пользователя
     */
    public void clearInvalidTracks(Long userId) {
        if (userId != null) {
            cache.remove(userId);
        }
    }

    /**
     * Периодически удаляет записи, срок хранения которых истёк.
     * <p>
     * Очистка выполняется каждые 30 секунд, чтобы память не занимали
     * устаревшие данные.
     * </p>
     */
    @Scheduled(fixedDelay = 30_000)
    public void removeExpired() {
        long expiration = applicationSettingsService.getResultCacheExpirationMs();
        long threshold = System.currentTimeMillis() - expiration;
        cache.entrySet().removeIf(userEntry -> {
            Map<Long, BatchEntry> byBatch = userEntry.getValue();
            byBatch.entrySet().removeIf(e -> e.getValue().expired(threshold));
            return byBatch.isEmpty();
        });
    }

    /**
     * Контейнер с треками одного батча и отметкой последнего просмотра.
     */
    private static class BatchEntry {
        /** Список некорректных треков. */
        private final List<InvalidTrack> tracks = Collections.synchronizedList(new ArrayList<>());
        /** Временная метка последнего просмотра кэша. */
        private volatile long lastAccess;

        /** Признак того, что пользователь уже открыл список некорректных треков. */
        private volatile boolean viewed;

        BatchEntry() {
            lastAccess = System.currentTimeMillis();
            viewed = false;
        }

        /**
         * Добавляет список треков. Обновление времени будет произведено при обращении.
         */
        void addAll(List<InvalidTrack> list) {
            tracks.addAll(list);
        }

        /**
         * Возвращает копию списка и помечает запись просмотренной.
         */
        List<InvalidTrack> snapshot() {
            viewed = true;
            refresh();
            return new ArrayList<>(tracks);
        }

        /** Обновляет время последнего доступа текущим моментом. */
        void refresh() {
            lastAccess = System.currentTimeMillis();
        }

        /**
         * Проверяет, истекло ли время жизни записи относительно заданного порога.
         * <p>
         * Используется нестрогое сравнение: запись считается просроченной ровно
         * в момент окончания TTL. Это позволяет немедленно удалить запись,
         * когда время жизни равно нулю и пользователь уже просмотрел данные.
         * </p>
         *
         * @param threshold момент времени, с которым сравнивается {@code lastAccess}
         * @return {@code true}, если запись следует считать просроченной
         */
        boolean expired(long threshold) {
            // запись подлежит удалению только после просмотра пользователем
            return viewed && lastAccess <= threshold;
        }
    }

}
