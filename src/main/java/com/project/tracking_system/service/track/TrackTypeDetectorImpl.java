package com.project.tracking_system.service.track;

import com.project.tracking_system.entity.PostalServiceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Реализация {@link TrackTypeDetector}, использующая
 * {@link TypeDefinitionTrackPostService} для определения
 * сервиса доставки по номеру трека.
 */
@Component
@RequiredArgsConstructor
public class TrackTypeDetectorImpl implements TrackTypeDetector {

    private final TypeDefinitionTrackPostService typeDefinitionTrackPostService;

    @Override
    public PostalServiceType detect(PreRegistrationMeta meta) {
        return typeDefinitionTrackPostService.detectPostalService(meta.getTrackNumber());
    }
}

