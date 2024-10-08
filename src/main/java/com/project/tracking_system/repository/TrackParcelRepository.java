package com.project.tracking_system.repository;

import com.project.tracking_system.entity.TrackParcel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrackParcelRepository extends JpaRepository<TrackParcel, Long> {

    List<TrackParcel> findByUserId(Long userId);

    TrackParcel findByNumberAndUserId(String number, Long userId);

    List<TrackParcel> findByUserIdAndStatus(Long userId, String status);

}