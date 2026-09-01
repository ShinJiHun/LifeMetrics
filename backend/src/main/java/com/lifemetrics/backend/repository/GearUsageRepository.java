package com.lifemetrics.backend.repository;

import com.lifemetrics.backend.entity.GearUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GearUsageRepository extends JpaRepository<GearUsage, Long> {

    List<GearUsage> findByActivityCoreId(Long activityCoreId);

    GearUsage findByActivityCoreIdAndFrontGearAndRearGearAndTerrain(
            Long activityCoreId, Integer frontGear, Integer rearGear, String terrain);

    @Query("SELECT g.frontGear AS frontGear, g.rearGear AS rearGear, g.terrain AS terrain, " +
            "SUM(g.durationSec) AS durationSec, SUM(g.distance) AS distance " +
            "FROM GearUsage g " +
            "WHERE g.activityCoreId IN (SELECT a.id FROM ActivityCore a WHERE a.bikeId = :bikeId) " +
            "GROUP BY g.frontGear, g.rearGear, g.terrain")
    List<GearUsageSummary> summarizeByBikeId(@Param("bikeId") Long bikeId);
}
