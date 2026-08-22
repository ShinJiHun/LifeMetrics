package com.lifemetrics.backend.repository;

import com.lifemetrics.backend.entity.GearUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GearUsageRepository extends JpaRepository<GearUsage, Long> {

    List<GearUsage> findByActivityCoreId(Long activityCoreId);

    GearUsage findByActivityCoreIdAndFrontGearAndRearGearAndTerrain(
            Long activityCoreId, Integer frontGear, Integer rearGear, String terrain);
}
