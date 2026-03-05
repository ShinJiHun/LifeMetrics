package com.lifemetrics.backend.repository;

import com.lifemetrics.backend.entity.MuscleGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MuscleGroupRepository extends JpaRepository<MuscleGroup, Long> {

    List<MuscleGroup> findByParentIdIsNullOrderBySortOrder();

    List<MuscleGroup> findByParentIdOrderBySortOrder(Long parentId);

    List<MuscleGroup> findByParentIdIsNotNullOrderBySortOrder();
}