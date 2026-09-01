package com.lifemetrics.backend.persona.repository;

import com.lifemetrics.backend.persona.entity.PersonalProjectFeature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PersonalProjectFeatureRepository extends JpaRepository<PersonalProjectFeature, Long> {
    List<PersonalProjectFeature> findAllByOrderBySortOrderAsc();
    List<PersonalProjectFeature> findByProjectIdOrderBySortOrderAsc(Long projectId);
    void deleteByProjectId(Long projectId);
}
