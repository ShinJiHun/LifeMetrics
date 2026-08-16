package com.lifemetrics.backend.persona.repository;

import com.lifemetrics.backend.persona.entity.Education;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EducationRepository extends JpaRepository<Education, Long> {
    List<Education> findAllByOrderBySortOrderAsc();
}
