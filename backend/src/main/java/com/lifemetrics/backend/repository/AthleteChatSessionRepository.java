package com.lifemetrics.backend.repository;

import com.lifemetrics.backend.entity.AthleteChatSession;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AthleteChatSessionRepository extends JpaRepository<AthleteChatSession, Long> {

    List<AthleteChatSession> findByUserIdOrderByUpdatedAtDescIdDesc(Long userId, Pageable pageable);

    Optional<AthleteChatSession> findByIdAndUserId(Long id, Long userId);
}
