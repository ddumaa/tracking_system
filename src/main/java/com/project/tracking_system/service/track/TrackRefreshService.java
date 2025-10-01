package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackDetailsDto;
import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.service.SubscriptionService;
import com.project.tracking_system.service.admin.ApplicationSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.EntityNotFoundException;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис ручного обновления трека из модального окна.
 * <p>
 * Инкапсулирует проверку прав доступа, ограничений по времени и тарифным лимитам,
 * обеспечивая идемпотентный запуск обновления: повторные запросы к одному и тому же
 * треку выполняют единственный сетевой вызов.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrackRefreshService {

    private static final String CACHE_NAME = "track-details";

    private final TrackParcelService trackParcelService;
    private final TrackProcessingService trackProcessingService;
    private final TrackViewService trackViewService;
    private final ApplicationSettingsService applicationSettingsService;
    private final SubscriptionService subscriptionService;
    private final CacheManager cacheManager;

    /** Карта блокировок на идентификатор посылки для обеспечения идемпотентности. */
    private final Map<Long, Object> parcelLocks = new ConcurrentHashMap<>();

    /**
     * Выполняет обновление трека пользователя.
     *
     * @param trackId идентификатор посылки
     * @param userId  идентификатор владельца
     * @return обновлённый DTO для модального окна
     */
    public TrackDetailsDto refreshTrack(Long trackId, Long userId) {
        Object lock = parcelLocks.computeIfAbsent(trackId, id -> new Object());
        synchronized (lock) {
            try {
                return doRefresh(trackId, userId);
            } finally {
                parcelLocks.remove(trackId, lock);
            }
        }
    }

    /**
     * Содержит основную бизнес-логику: проверки и запуск обновления.
     */
    private TrackDetailsDto doRefresh(Long trackId, Long userId) {
        TrackParcel parcel = resolveOwnedParcel(trackId, userId);
        ensureTrackEditable(parcel);

        CooldownCheckResult cooldown = evaluateCooldown(parcel);
        if (!cooldown.allowed()) {
            return buildCooldownDetails(parcel, userId, cooldown.nextAllowedAt());
        }

        int allowedUpdates = subscriptionService.canUpdateTracks(userId, 1);
        if (allowedUpdates < 1) {
            log.info("Лимит обновлений исчерпан для userId={}", userId);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Лимит обновлений на сегодня исчерпан.");
        }

        log.info("♻️ Запущено ручное обновление трека {} (parcelId={}) для userId={}",
                parcel.getNumber(), parcel.getId(), userId);
        trackProcessingService.processTrack(parcel.getNumber(), parcel.getStore().getId(), userId, true);

        evictDetailsCache(trackId, userId);
        return trackViewService.getTrackDetails(trackId, userId);
    }

    /**
     * Находит посылку и убеждается, что она принадлежит пользователю.
     */
    private TrackParcel resolveOwnedParcel(Long trackId, Long userId) {
        return trackParcelService.findOwnedById(trackId, userId)
                .orElseGet(() -> {
                    if (trackParcelService.findById(trackId).isPresent()) {
                        throw new AccessDeniedException("Посылка не принадлежит пользователю");
                    }
                    log.warn("Не найдена посылка id={} для пользователя {} при попытке обновления", trackId, userId);
                    throw new EntityNotFoundException("Посылка не найдена");
                });
    }

    /**
     * Убеждается, что трек можно редактировать и у него задан корректный номер.
     */
    private void ensureTrackEditable(TrackParcel parcel) {
        if (parcel.getNumber() == null || parcel.getNumber().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Добавьте трек-номер, чтобы выполнить обновление.");
        }
        if (parcel.getStatus() != null && parcel.getStatus().isFinal()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Обновление недоступно для посылки в финальном статусе.");
        }
    }

    /**
     * Рассчитывает доступность ручного обновления без выбрасывания исключения.
     * <p>
     * Метод возвращает структуру, содержащую признак доступности и момент
     * следующей попытки. Такой подход позволяет отделить проверку бизнес-правил
     * от реакции контроллера, что соответствует принципам SRP и OCP.
     * </p>
     */
    private CooldownCheckResult evaluateCooldown(TrackParcel parcel) {
        ZonedDateTime lastUpdate = parcel.getLastUpdate();
        if (lastUpdate == null) {
            return new CooldownCheckResult(true, null);
        }
        int interval = applicationSettingsService.getTrackUpdateIntervalHours();
        ZonedDateTime nextAllowed = lastUpdate.plusHours(interval);
        boolean allowed = !nextAllowed.isAfter(ZonedDateTime.now(ZoneOffset.UTC));
        return new CooldownCheckResult(allowed, allowed ? null : nextAllowed);
    }

    /**
     * Формирует DTO с информацией о кулдауне без повторного обращения к базе.
     * <p>
     * Сначала получаем актуальные данные из сервиса чтения, а затем принудительно
     * обновляем флаги доступности, чтобы пользователь сразу увидел ограничения
     * без повторного запроса на фронтенде.
     * </p>
     */
    private TrackDetailsDto buildCooldownDetails(TrackParcel parcel, Long userId, ZonedDateTime nextAllowed) {
        TrackDetailsDto details = trackViewService.getTrackDetails(parcel.getId(), userId);
        if (nextAllowed == null) {
            return new TrackDetailsDto(
                    details.id(),
                    details.number(),
                    details.deliveryService(),
                    details.systemStatus(),
                    details.lastUpdateAt(),
                    details.currentStatus(),
                    details.history(),
                    false,
                    null,
                    details.canEditTrack(),
                    details.timeZone()
            );
        }

        ZoneId zone = details.timeZone() != null ? ZoneId.of(details.timeZone()) : ZoneOffset.UTC;
        String formattedNextRefresh = nextAllowed.withZoneSameInstant(zone)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        return new TrackDetailsDto(
                details.id(),
                details.number(),
                details.deliveryService(),
                details.systemStatus(),
                details.lastUpdateAt(),
                details.currentStatus(),
                details.history(),
                false,
                formattedNextRefresh,
                details.canEditTrack(),
                details.timeZone()
        );
    }

    /**
     * Инвалидирует кэш модального окна, чтобы вернуть пользователю свежие данные.
     */
    private void evictDetailsCache(Long trackId, Long userId) {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            cache.evict(userId + ":" + trackId);
        }
    }

    /**
     * Переносит результат проверки кулдауна в отдельную неизменяемую структуру.
     */
    private record CooldownCheckResult(boolean allowed, ZonedDateTime nextAllowedAt) {
    }
}
