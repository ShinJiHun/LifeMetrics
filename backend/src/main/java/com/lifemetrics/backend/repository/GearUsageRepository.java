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

    /**
     * 자전거·라이딩 날짜·앞/뒤 톱니수별 주행 거리(미터). 장비 변경 계획에서 체인링·스프라켓(코그)별
     * 사용 거리를 부품 장착일 이후로 잘라 합산할 때 쓴다. 지형(terrain)은 합친다.
     */
    @Query("SELECT a.bikeId AS bikeId, a.startTime AS rideStart, " +
            "g.frontGear AS frontGear, g.rearGear AS rearGear, SUM(g.distance) AS distance " +
            "FROM GearUsage g JOIN ActivityCore a ON a.id = g.activityCoreId " +
            "WHERE a.bikeId IN :bikeIds AND a.startTime IS NOT NULL " +
            "GROUP BY a.id, a.bikeId, a.startTime, g.frontGear, g.rearGear")
    List<GearDistanceRow> findGearDistancesByBikeIds(@Param("bikeIds") List<Long> bikeIds);
}
