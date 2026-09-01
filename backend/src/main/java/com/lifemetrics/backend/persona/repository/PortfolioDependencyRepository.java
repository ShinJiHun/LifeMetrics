package com.lifemetrics.backend.persona.repository;

import com.lifemetrics.backend.persona.entity.PortfolioDependency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PortfolioDependencyRepository extends JpaRepository<PortfolioDependency, Long> {
    List<PortfolioDependency> findAllByOrderBySortOrderAsc();
}
