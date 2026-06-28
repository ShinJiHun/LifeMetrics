package com.lifemetrics.backend.persona.repository;

import com.lifemetrics.backend.persona.entity.JournalPost;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface JournalPostRepository extends JpaRepository<JournalPost, Long> {

    List<JournalPost> findBySubIdOrderByCreatedAtDesc(Long subId);

    void deleteBySubId(Long subId);

    void deleteBySubIdIn(Collection<Long> subIds);
}
