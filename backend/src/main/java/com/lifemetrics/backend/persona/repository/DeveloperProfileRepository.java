package com.lifemetrics.backend.persona.repository;

import com.lifemetrics.backend.persona.entity.DeveloperProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeveloperProfileRepository extends JpaRepository<DeveloperProfile, Long> {
}
