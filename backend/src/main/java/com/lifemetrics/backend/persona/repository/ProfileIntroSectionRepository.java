package com.lifemetrics.backend.persona.repository;

import com.lifemetrics.backend.persona.entity.ProfileIntroSection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProfileIntroSectionRepository extends JpaRepository<ProfileIntroSection, Long> {
    List<ProfileIntroSection> findAllByOrderBySortOrderAsc();
}
