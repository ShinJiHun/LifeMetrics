package com.lifemetrics.backend.repository;

import com.lifemetrics.backend.entity.UserInbodyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserInbodyRecordRepository
        extends JpaRepository<UserInbodyRecord, Long> {

    List<UserInbodyRecord> findByUserIdOrderByRecordDate(Long userId);

    Optional<UserInbodyRecord> findTopByUserIdOrderByRecordDateDesc(Long userId);
}
