package com.project.tracking_system.service.track;

import com.project.tracking_system.dto.TrackInfoListDTO;
import com.project.tracking_system.dto.TrackingResultAdd;
import com.project.tracking_system.entity.PostalServiceType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Processor for updating tracks served by Evropost postal service.
 */
@Service
@RequiredArgsConstructor
public class EvropostTrackUpdateProcessor implements TrackUpdateProcessor {

    private final TrackFacade trackFacade;
    private final TaskExecutor batchUploadExecutor;

    @Override
    public PostalServiceType supportedType() {
        return PostalServiceType.EVROPOST;
    }

    @Override
    public List<TrackingResultAdd> process(List<TrackMeta> tracks, Long userId) {
        List<TrackingResultAdd> results = new ArrayList<>();
        if (tracks == null || tracks.isEmpty()) {
            return results;
        }
        List<CompletableFuture<TrackingResultAdd>> futures = tracks.stream()
                .map(meta -> CompletableFuture.supplyAsync(() -> {
                    TrackInfoListDTO info = trackFacade.processTrack(
                            meta.number(), meta.storeId(), userId, meta.canSave(), meta.phone());
                    String status = info.getList().isEmpty()
                            ? TrackConstants.NO_DATA_STATUS
                            : info.getList().get(0).getInfoTrack();
                    return new TrackingResultAdd(meta.number(), status);
                }, batchUploadExecutor))
                .toList();
        futures.forEach(f -> results.add(f.join()));
        return results;
    }
}
