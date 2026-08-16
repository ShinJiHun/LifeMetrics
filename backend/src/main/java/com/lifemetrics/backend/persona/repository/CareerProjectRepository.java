package com.lifemetrics.backend.persona.repository;

import com.lifemetrics.backend.persona.entity.CareerProject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CareerProjectRepository extends JpaRepository<CareerProject, Long> {
    List<CareerProject> findAllByOrderBySortOrderAsc();
    List<CareerProject> findByCompanyIdOrderBySortOrderAsc(Long companyId);
}
