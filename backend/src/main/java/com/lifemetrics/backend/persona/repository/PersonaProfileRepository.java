package com.lifemetrics.backend.persona.repository;

import com.lifemetrics.backend.persona.entity.PersonaProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PersonaProfileRepository extends JpaRepository<PersonaProfile, Long> {

    Optional<PersonaProfile> findTopByOrderByGeneratedAtDesc();
}