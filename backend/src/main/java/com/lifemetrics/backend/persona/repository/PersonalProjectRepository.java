package com.lifemetrics.backend.persona.repository;

import com.lifemetrics.backend.persona.entity.PersonalProject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PersonalProjectRepository extends JpaRepository<PersonalProject, Long> {
    List<PersonalProject> findAllByOrderBySortOrderAsc();
}
