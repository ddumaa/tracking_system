package com.project.tracking_system.repository;

import com.project.tracking_system.entity.TrackParcel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


public interface TrackParcelRepository extends JpaRepository<TrackParcel, Long> {

    List<TrackParcel> findByUserId(Long userId);

    Page<TrackParcel> findByUserId(Long userId, Pageable pageable);

    TrackParcel findByNumberAndUserId(String number, Long userId);

    Page<TrackParcel> findByUserIdAndStatus(Long userId, String status, Pageable pageable);

}