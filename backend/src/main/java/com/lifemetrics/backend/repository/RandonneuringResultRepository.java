package com.lifemetrics.backend.repository;

import com.lifemetrics.backend.entity.RandonneuringResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RandonneuringResultRepository extends JpaRepository<RandonneuringResult, Long> {

    List<RandonneuringResult> findByUserIdOrderByEventDateDescIdDesc(Long userId);

    Optional<RandonneuringResult> findBySourceId(String sourceId);

    Optional<RandonneuringResult> findByUserIdAndEventDateAndEventName(Long userId, LocalDate eventDate, String eventName);

    long countByUserIdAndEventDate(Long userId, LocalDate eventDate);
}
