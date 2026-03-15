package com.lifemetrics.backend.repository;

import com.lifemetrics.backend.entity.DeviceInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeviceInfoRepository extends JpaRepository<DeviceInfo, Long> {
    List<DeviceInfo> findByOwnerUserIdAndIsActiveTrue(Long userId);
}
