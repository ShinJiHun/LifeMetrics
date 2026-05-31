package com.lifemetrics.backend.repository;

import com.lifemetrics.backend.entity.DeviceComponent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface DeviceComponentRepository extends JpaRepository<DeviceComponent, Long> {

    @Query("""
        SELECT c FROM DeviceComponent c
        WHERE c.deviceId = :deviceId
          AND c.effectiveFrom <= :date
          AND (c.effectiveTo IS NULL OR c.effectiveTo >= :date)
        """)
    List<DeviceComponent> findEffectiveAt(@Param("deviceId") Long deviceId,
                                          @Param("date") LocalDate date);
}
