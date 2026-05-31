package com.lifemetrics.backend.repository;

import com.lifemetrics.backend.entity.DeviceInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface DeviceInfoRepository extends JpaRepository<DeviceInfo, Long> {

    List<DeviceInfo> findByOwnerUserIdAndIsActiveTrue(Long userId);

    // 활동 시점에 유효했던 BIKE. invariant: 한 user에게 active BIKE는 ≤1대.
    // 방어적으로 List 반환; 호출부는 stream().findFirst() 로 픽.
    @Query("""
        SELECT d FROM DeviceInfo d
        WHERE d.ownerUserId = :userId
          AND d.deviceType = 'BIKE'
          AND d.isActive = true
          AND (d.firstSeenAt IS NULL OR d.firstSeenAt <= :asOf)
          AND (d.lastSeenAt IS NULL OR d.lastSeenAt >= :asOf)
        ORDER BY d.firstSeenAt DESC
        """)
    List<DeviceInfo> findActiveBikesAt(@Param("userId") Long userId,
                                       @Param("asOf") LocalDateTime asOf);
}