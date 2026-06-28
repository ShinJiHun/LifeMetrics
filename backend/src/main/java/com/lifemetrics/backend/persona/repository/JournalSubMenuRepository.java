package com.lifemetrics.backend.persona.repository;

import com.lifemetrics.backend.persona.entity.JournalSubMenu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JournalSubMenuRepository extends JpaRepository<JournalSubMenu, Long> {

    List<JournalSubMenu> findByCategoryIdOrderBySortOrderAsc(Long categoryId);

    int countByCategoryId(Long categoryId);

    void deleteByCategoryId(Long categoryId);
}
