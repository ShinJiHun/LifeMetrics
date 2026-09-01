package com.lifemetrics.backend.persona.repository;

import com.lifemetrics.backend.persona.entity.PortfolioTroubleshoot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PortfolioTroubleshootRepository extends JpaRepository<PortfolioTroubleshoot, Long> {
    List<PortfolioTroubleshoot> findAllByOrderBySortOrderAsc();
}
