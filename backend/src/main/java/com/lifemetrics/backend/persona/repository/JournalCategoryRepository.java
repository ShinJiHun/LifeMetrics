package com.lifemetrics.backend.persona.repository;

import com.lifemetrics.backend.persona.entity.JournalCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JournalCategoryRepository extends JpaRepository<JournalCategory, Long> {

    List<JournalCategory> findByPersonaOrderBySortOrderAsc(String persona);

    int countByPersona(String persona);
}
