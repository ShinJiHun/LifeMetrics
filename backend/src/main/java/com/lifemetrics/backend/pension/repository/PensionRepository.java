package com.lifemetrics.backend.pension.repository;

import com.lifemetrics.backend.pension.entity.PensionNumber;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PensionRepository extends JpaRepository<PensionNumber, Long> {

    // 회차번호로 조회
    Optional<PensionNumber> findByRoundNumber(Integer roundNumber);

    // 최신 회차 조회
    @Query("SELECT p FROM PensionNumber p ORDER BY p.roundNumber DESC LIMIT 1")
    Optional<PensionNumber> findLatestRound();

    // 가장 오래된 회차 조회
    @Query("SELECT p FROM PensionNumber p ORDER BY p.roundNumber ASC LIMIT 1")
    Optional<PensionNumber> findOldestRound();

    // 특정 회차 이상의 데이터만 조회
    @Query("SELECT p FROM PensionNumber p WHERE p.roundNumber >= :roundNumber ORDER BY p.roundNumber DESC")
    java.util.List<PensionNumber> findRecentRounds(@Param("roundNumber") Integer roundNumber);
}
