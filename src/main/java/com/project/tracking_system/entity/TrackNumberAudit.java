package com.project.tracking_system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;

/**
 * Запись аудита изменения трек-номера.
 * <p>
 * Фиксирует исходное и новое значение номера, а также идентификатор пользователя,
 * выполнившего изменение. Используется для построения истории действий и
 * расследования спорных ситуаций.
 * </p>
 */
@Entity
@Table(name = "tb_track_number_audit")
public class TrackNumberAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Посылка, для которой выполнено изменение номера. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "track_parcel_id", nullable = false)
    private TrackParcel trackParcel;

    /** Номер до изменения. */
    @Column(name = "old_number", nullable = true, length = 50)
    private String oldNumber;

    /** Номер после изменения. */
    @Column(name = "new_number", nullable = false, length = 50)
    private String newNumber;

    /** Идентификатор пользователя, выполнившего изменение. */
    @Column(name = "changed_by", nullable = false)
    private Long changedBy;

    /** Момент изменения в часовом поясе UTC. */
    @Column(name = "changed_at", nullable = false)
    private ZonedDateTime changedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public TrackParcel getTrackParcel() {
        return trackParcel;
    }

    public void setTrackParcel(TrackParcel trackParcel) {
        this.trackParcel = trackParcel;
    }

    public String getOldNumber() {
        return oldNumber;
    }

    public void setOldNumber(String oldNumber) {
        this.oldNumber = oldNumber;
    }

    public String getNewNumber() {
        return newNumber;
    }

    public void setNewNumber(String newNumber) {
        this.newNumber = newNumber;
    }

    public Long getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(Long changedBy) {
        this.changedBy = changedBy;
    }

    public ZonedDateTime getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(ZonedDateTime changedAt) {
        this.changedAt = changedAt;
    }
}
