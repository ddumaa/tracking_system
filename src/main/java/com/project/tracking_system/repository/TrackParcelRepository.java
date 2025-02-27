package com.project.tracking_system.repository;

import com.project.tracking_system.entity.TrackParcel;
import com.project.tracking_system.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TrackParcelRepository extends JpaRepository<TrackParcel, Long> {

    List<TrackParcel> findByUserId(Long userId);

    List<TrackParcel> findByNumberInAndUserId(List<String> numbers, Long userId);

    Page<TrackParcel> findByUserId(Long userId, Pageable pageable);

    TrackParcel findByNumberAndUserId(String number, Long userId);

    Page<TrackParcel> findByUserIdAndStatus(Long userId, String status, Pageable pageable);

    long countByUser(User user);

    @Query("SELECT COUNT(t) FROM TrackParcel t WHERE t.user.id = :userId")
    int countByUserId(@Param("userId") Long userId);

}