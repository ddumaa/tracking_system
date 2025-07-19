package com.project.tracking_system.service.track;

import com.project.tracking_system.entity.PostalServiceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

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
        return trackServiceClassifier.classify(tracks);
    }
}
