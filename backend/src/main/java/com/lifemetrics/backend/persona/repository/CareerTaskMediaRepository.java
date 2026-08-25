package com.lifemetrics.backend.persona.repository;

import com.lifemetrics.backend.persona.entity.CareerTaskMedia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CareerTaskMediaRepository extends JpaRepository<CareerTaskMedia, Long> {
    List<CareerTaskMedia> findAllByOrderBySortOrderAsc();
    List<CareerTaskMedia> findByTaskIdOrderBySortOrderAsc(Long taskId);
}
