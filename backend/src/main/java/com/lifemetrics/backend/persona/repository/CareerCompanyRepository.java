package com.lifemetrics.backend.persona.repository;

import com.lifemetrics.backend.persona.entity.CareerCompany;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CareerCompanyRepository extends JpaRepository<CareerCompany, Long> {
    List<CareerCompany> findAllByOrderBySortOrderAsc();
}
