package com.lifemetrics.backend.repository;

import com.lifemetrics.backend.entity.AthleteChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AthleteChatMessageRepository extends JpaRepository<AthleteChatMessage, Long> {

    List<AthleteChatMessage> findBySessionIdOrderByIdAsc(Long sessionId);

    /** 하루 동안 남긴 메시지(role = user 면 내가 한 질문). created_at 은 KST 로 저장된다. */
    List<AthleteChatMessage> findByRoleAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByIdAsc(
            String role, LocalDateTime from, LocalDateTime to);
}
