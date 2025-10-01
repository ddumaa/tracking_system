package com.project.tracking_system.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;

/**
 * Событие истории статусов, сохранённое для последующего отображения в модалке.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tb_track_status_events")
public class TrackStatusEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Посылка, к которой относится событие.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "track_parcel_id", nullable = false)
    private TrackParcel trackParcel;

    /**
     * Момент наступления события в часовом поясе UTC.
     */
    @Column(name = "event_time", nullable = false)
    private ZonedDateTime eventTime;

    /**
     * Текстовое описание статуса от почтовой службы.
     */
    @Column(name = "description", nullable = false, length = 1000)
    private String description;
}
