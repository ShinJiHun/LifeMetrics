package com.lifemetrics.backend.repository;

import com.lifemetrics.backend.entity.Segment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SegmentRepository extends JpaRepository<Segment, Long> {

    Optional<Segment> findByStravaSegmentId(Long stravaSegmentId);

    List<Segment> findByIsActiveTrue();

    @Query("""
           SELECT s FROM Segment s
           WHERE s.isActive = true
             AND s.name = :name
             AND ABS(s.distance - :distance) < 10
             AND ABS(s.startLat - :startLat) < 0.001
             AND ABS(s.startLon - :startLon) < 0.001
           """)
    Optional<Segment> findByNameAndLocation(
            @Param("name") String name,
            @Param("distance") Double distance,
            @Param("startLat") Double startLat,
            @Param("startLon") Double startLon
    );
}
