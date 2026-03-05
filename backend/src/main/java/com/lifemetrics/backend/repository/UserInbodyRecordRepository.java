package com.lifemetrics.backend.repository;

import com.lifemetrics.backend.entity.UserInbodyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface UserInbodyRecordRepository
        extends JpaRepository<UserInbodyRecord, Long> {

    List<UserInbodyRecord> findByUserIdOrderByRecordDate(Long userId);

    Optional<UserInbodyRecord> findTopByUserIdOrderByRecordDateDesc(Long userId);

    @Query("""
        select r
        from UserInbodyRecord r
        where r.userId = :userId
          and r.recordDate < :date
        order by r.recordDate desc
        """)
    Optional<UserInbodyRecord> findPrevious(
            @Param("userId") Long userId,
            @Param("date") LocalDate date
    );
}
