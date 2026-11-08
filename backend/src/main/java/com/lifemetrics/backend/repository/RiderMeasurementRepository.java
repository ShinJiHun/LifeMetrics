package com.lifemetrics.backend.repository;

import com.lifemetrics.backend.entity.RiderMeasurement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RiderMeasurementRepository extends JpaRepository<RiderMeasurement, Long> {

    List<RiderMeasurement> findByUserIdOrderByMeasureDateDescIdDesc(Long userId);
}
