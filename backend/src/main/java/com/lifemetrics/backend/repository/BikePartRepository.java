package com.lifemetrics.backend.repository;

import com.lifemetrics.backend.entity.BikePart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BikePartRepository extends JpaRepository<BikePart, Long> {
    List<BikePart> findByBikeIdInOrderByInstalledOnDesc(List<Long> bikeIds);
}
