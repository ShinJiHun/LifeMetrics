package com.lifemetrics.backend.repository;

import com.lifemetrics.backend.entity.AthleteChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AthleteChatMessageRepository extends JpaRepository<AthleteChatMessage, Long> {

    List<AthleteChatMessage> findBySessionIdOrderByIdAsc(Long sessionId);
}
