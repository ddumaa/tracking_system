package com.project.tracking_system.repository;

import com.project.tracking_system.entity.TrackStatusEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Репозиторий для чтения и сохранения событий истории статусов посылки.
 */
public interface TrackStatusEventRepository extends JpaRepository<TrackStatusEvent, Long> {

    /**
     * Возвращает события конкретной посылки в обратном хронологическом порядке.
     *
     * @param trackParcelId идентификатор посылки
     * @return список событий
     */
    List<TrackStatusEvent> findByTrackParcelIdOrderByEventTimeDesc(Long trackParcelId);

    /**
     * Удаляет все события, связанные с посылкой.
     *
     * @param trackParcelId идентификатор посылки
     */
    void deleteByTrackParcelId(Long trackParcelId);
}
