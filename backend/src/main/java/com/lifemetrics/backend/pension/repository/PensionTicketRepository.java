package com.lifemetrics.backend.pension.repository;

import com.lifemetrics.backend.pension.entity.PensionTicketEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PensionTicketRepository extends JpaRepository<PensionTicketEntity, Long> {

    /**
     * 특정 회차의 모든 티켓 조회
     */
    List<PensionTicketEntity> findByRound(Integer round);

    /**
     * 최신 티켓 조회
     */
    @Query("SELECT p FROM PensionTicketEntity p ORDER BY p.createdAt DESC LIMIT 1")
    Optional<PensionTicketEntity> findLatestTicket();

    /**
     * 중복 확인: 같은 회차 + 같은 번호 조합
     */
    @Query("SELECT p FROM PensionTicketEntity p WHERE p.round = :round AND p.n1 = :n1 AND p.n2 = :n2 AND p.n3 = :n3 AND p.n4 = :n4 AND p.n5 = :n5 AND p.n6 = :n6 AND p.jo = :jo")
    Optional<PensionTicketEntity> findDuplicate(Integer round, Integer n1, Integer n2, Integer n3, Integer n4, Integer n5, Integer n6, Integer jo);

    /**
     * 최근 N개 티켓 조회 (최신순)
     */
    @Query("SELECT p FROM PensionTicketEntity p ORDER BY p.createdAt DESC LIMIT :limit")
    List<PensionTicketEntity> findRecentTickets(Integer limit);
}
