package com.lifemetrics.backend.lotto.repository;

import com.lifemetrics.backend.lotto.entity.LottoTicketEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface LottoTicketRepository extends JpaRepository<LottoTicketEntity, Long> {

    List<LottoTicketEntity> findAllByOrderByCreatedAtDesc();

    List<LottoTicketEntity> findByRoundOrderByGameNo(Integer round);

    List<LottoTicketEntity> findByTicketGroupOrderByGameNo(String ticketGroup);

    /** 같은 회차 + 같은 발행일시로 이미 등록된 게임들 (중복 용지 판별용). */
    List<LottoTicketEntity> findByRoundAndIssuedAt(Integer round, LocalDateTime issuedAt);
}
