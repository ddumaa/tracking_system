package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackingResultAdd;
import com.project.tracking_system.entity.PostalServiceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Координирует обновление треков различных почтовых служб.
 * <p>
 * Сервис группирует переданные {@link TrackMeta} по {@link PostalServiceType}
 * и передает их в {@link TrackUpdateDispatcherService}.
 * Используется как при загрузке из файла, так и в ручных сценариях.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class TrackUpdateCoordinatorService {

    private final TrackUploadGroupingService groupingService;
    private final TrackUpdateDispatcherService dispatcherService;

    /**
     * Обрабатывает набор треков для указанного пользователя.
     *
     * @param tracks список метаданных треков
     * @param userId идентификатор пользователя
     * @return список объединенных результатов
     */
    public List<TrackingResultAdd> process(List<TrackMeta> tracks, Long userId) {
        Map<PostalServiceType, List<TrackMeta>> grouped = groupingService.group(tracks);
        return dispatcherService.dispatch(grouped, userId);
    }
}
