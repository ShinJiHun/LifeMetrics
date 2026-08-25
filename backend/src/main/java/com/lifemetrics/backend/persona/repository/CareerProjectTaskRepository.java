package com.lifemetrics.backend.persona.repository;

import com.lifemetrics.backend.persona.entity.CareerProjectTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CareerProjectTaskRepository extends JpaRepository<CareerProjectTask, Long> {
    List<CareerProjectTask> findAllByOrderBySortOrderAsc();
    List<CareerProjectTask> findByProjectIdOrderBySortOrderAsc(Long projectId);
}
