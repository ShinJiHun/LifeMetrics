package com.lifemetrics.backend.persona.repository;

import com.lifemetrics.backend.persona.entity.DevExperience;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DevExperienceRepository extends JpaRepository<DevExperience, Long> {

    List<DevExperience> findAllByOrderByHappenedOnDescIdDesc();

    List<DevExperience> findByVisibilityOrderByHappenedOnDescIdDesc(String visibility);
}
