package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.dto.TrackingResultAdd;
import com.project.tracking_system.entity.PostalServiceType;
import com.project.tracking_system.service.track.TrackConstants;
import com.project.tracking_system.service.track.TrackProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;

/**
 * Процессор обновления треков для службы Европочты.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EvropostTrackUpdateProcessor implements TrackUpdateProcessor {

    /**
     * Сервис низкого уровня, выполняющий обработку и сохранение треков.
     */
    private final TrackProcessingService trackProcessingService;

    /**
     * Асинхронный исполнитель для отправки запросов к сервису Европочты.
     */
    private final TaskExecutor batchUploadExecutor;

    /**
     * Максимально допустимое количество параллельных задач Европочты.
     */
    private static final int MAX_PARALLEL_SUBMISSIONS = 10;

    /**
     * Семафор ограничивает число одновременно выполняющихся задач,
     * что защищает пул потоков от перегрузки.
     */
    private final Semaphore submissionLimiter = new Semaphore(MAX_PARALLEL_SUBMISSIONS);

    /**
     * Возвращает тип почтовой службы, поддерживаемой данным процессором.
     */
    @Override
    public PostalServiceType supportedType() {
        return PostalServiceType.EVROPOST;
    }

    /**
     * Обрабатывает список треков асинхронно, используя европейский сервис.
     *
     * @param tracks список треков
     * @param userId идентификатор пользователя, инициировавшего обработку
     * @return список результатов обработки
     */
    @Override
    public List<TrackingResultAdd> process(List<TrackMeta> tracks, Long userId) {
        List<TrackingResultAdd> results = new ArrayList<>();
        if (tracks == null || tracks.isEmpty()) {
            return results;
        }
        List<CompletableFuture<TrackingResultAdd>> futures = tracks.stream()
                .map(meta -> submitTrackForProcessing(meta, userId))
                .toList();
        futures.forEach(f -> results.add(f.join()));
        return results;
    }

    /**
     * Обрабатывает один трек синхронно.
     *
     * @param meta метаданные трек-номера
     * @return результат обработки
     */
    @Override
    public TrackingResultAdd process(TrackMeta meta) {
        if (meta == null) {
            return new TrackingResultAdd(null, TrackConstants.NO_DATA_STATUS, new TrackInfoListDTO());
        }
        TrackInfoListDTO info = loadTrackInfo(meta, null);
        String status = resolveStatus(info);
        return new TrackingResultAdd(meta.number(), status, info);
    }

    /**
     * Отправляет задачу в пул с учётом ограничений по параллельности.
     * <p>
     * Если пул потоков перегружен и отклоняет задачу, обработка выполняется
     * синхронно в текущем потоке, чтобы не терять данные пользователя.
     * </p>
     *
     * @param meta   метаданные трека
     * @param userId идентификатор пользователя
     * @return будущий результат обработки
     */
    private CompletableFuture<TrackingResultAdd> submitTrackForProcessing(TrackMeta meta, Long userId) {
        acquireSubmissionPermit();
        try {
            return CompletableFuture.supplyAsync(() -> executeTrackTask(meta, userId), batchUploadExecutor)
                    .whenComplete((result, throwable) -> submissionLimiter.release());
        } catch (TaskRejectedException | RejectedExecutionException ex) {
            submissionLimiter.release();
            log.warn("Пул потоков обновления Европочты перегружен, выполняем задачу синхронно: {}", meta.number(), ex);
            return CompletableFuture.completedFuture(executeTrackTask(meta, userId));
        } catch (RuntimeException ex) {
            submissionLimiter.release();
            throw ex;
        }
    }

    /**
     * Выполняет обработку трека, формируя краткий результат для пакетного режима.
     *
     * @param meta   метаданные трека
     * @param userId идентификатор пользователя
     * @return результат с номером трека и финальным статусом
     */
    private TrackingResultAdd executeTrackTask(TrackMeta meta, Long userId) {
        TrackInfoListDTO info = loadTrackInfo(meta, userId);
        String status = resolveStatus(info);
        return new TrackingResultAdd(meta.number(), status);
    }

    /**
     * Получает данные по треку у сервиса обработки.
     *
     * @param meta   метаданные трека
     * @param userId идентификатор пользователя
     * @return dto с информацией по треку
     */
    private TrackInfoListDTO loadTrackInfo(TrackMeta meta, Long userId) {
        return trackProcessingService.processTrack(
                meta.number(), meta.storeId(), userId, meta.canSave(), meta.phone());
    }

    /**
     * Преобразует ответ сервиса в итоговый статус и пишет debug-лог.
     *
     * @param info dto Европочты
     * @return статус для отображения пользователю
     */
    private String resolveStatus(TrackInfoListDTO info) {
        boolean hasStatus = info != null && !info.getList().isEmpty();
        // Информируем о результате обработки без персональных данных
        log.debug(hasStatus ? "Статусы получены" : "Статусы отсутствуют");
        return hasStatus
                ? info.getList().get(0).getInfoTrack()
                : TrackConstants.NO_DATA_STATUS;
    }

    /**
     * Ожидает свободный слот для отправки задачи в пул потоков.
     */
    private void acquireSubmissionPermit() {
        try {
            submissionLimiter.acquire();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Поток прерван при ограничении параллельности обработки Европочты", ex);
        }
    }
}