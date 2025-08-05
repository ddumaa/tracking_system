package com.project.tracking_system.service.track;

import com.project.tracking_system.entity.PostalServiceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.EnumMap;

/**
 * Сервис группировки валидированных треков по почтовым службам.
 * <p>
 * Логика определения служб вынесена в {@link TrackServiceClassifier},
 * что упрощает тестирование и поддерживает принцип единственной ответственности.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class TrackUploadGroupingService {

    private final TrackServiceClassifier trackServiceClassifier;

    /**
     * Делегирует классификацию треков сервису {@link TrackServiceClassifier}.
     *
     * @param tracks список валидированных трек-метаданных
     * @return карта «служба → список треков» без UNKNOWN
     */
    public Map<PostalServiceType, List<TrackMeta>> group(List<TrackMeta> tracks) {
        Map<PostalServiceType, List<TrackMeta>> grouped = new EnumMap<>(PostalServiceType.class);
        for (TrackMeta meta : tracks) {
            PostalServiceType type = meta.postalServiceType();
            if (type == null) {
                type = trackServiceClassifier.detect(meta.number());
            }
            if (type == PostalServiceType.UNKNOWN) {
                continue;
            }
            grouped.computeIfAbsent(type, k -> new ArrayList<>()).add(meta);
        }
        return grouped;
    }

}