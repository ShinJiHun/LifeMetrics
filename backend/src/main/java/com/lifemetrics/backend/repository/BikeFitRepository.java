package com.lifemetrics.backend.repository;

import com.lifemetrics.backend.entity.BikeFit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BikeFitRepository extends JpaRepository<BikeFit, Long> {

    List<BikeFit> findByBikeIdOrderByFitDateDescIdDesc(Long bikeId);
}
