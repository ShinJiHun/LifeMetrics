package com.lifemetrics.backend.ddareungi;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DdareungiStationRepository extends JpaRepository<DdareungiStation, String> {

    List<DdareungiStation> findByStationNo(String stationNo);

    List<DdareungiStation> findByStationName(String stationName);

    List<DdareungiStation> findByStationNameContaining(String keyword);
}
