package com.lifemetrics.backend.repository;

import com.lifemetrics.backend.entity.RandonneuringResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RandonneuringResultRepository extends JpaRepository<RandonneuringResult, Long> {

    List<RandonneuringResult> findByUserIdOrderByEventDateDescIdDesc(Long userId);
}
