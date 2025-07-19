package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackingResultAdd;
import com.project.tracking_system.entity.PostalServiceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Распределяет сгруппированные треки между специализированными процессорами.
 */
@Service
@RequiredArgsConstructor
public class TrackUpdateDispatcherService {

    private final List<TrackUpdateProcessor> processors;

    /**
     * Передает каждую группу треков своему процессору.
     *
     * @param grouped карта «служба → список треков»
     * @param userId  идентификатор пользователя
     * @return список собранных результатов обработки
     */
    public List<TrackingResultAdd> dispatch(Map<PostalServiceType, List<TrackMeta>> grouped, Long userId) {
        List<TrackingResultAdd> results = new ArrayList<>();
        if (grouped == null || grouped.isEmpty()) {
            return results;
        }
        for (Map.Entry<PostalServiceType, List<TrackMeta>> entry : grouped.entrySet()) {
            processors.stream()
                    .filter(p -> p.supportedType() == entry.getKey())
                    .findFirst()
                    .ifPresent(p -> results.addAll(p.process(entry.getValue(), userId)));
        }
        return results;
    }
}
