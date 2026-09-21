package com.lifemetrics.backend.lotto.repository;

import com.lifemetrics.backend.lotto.entity.LottoTicketEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface LottoTicketRepository extends JpaRepository<LottoTicketEntity, Long> {

    List<LottoTicketEntity> findAllByOrderByCreatedAtDesc();

    /** 벌크 동기화용: id > after 를 id 오름차순으로. */
    @Query("select t from LottoTicketEntity t where t.id > :after order by t.id asc")
    List<LottoTicketEntity> findRowsAfter(@Param("after") long after, Pageable pageable);

    List<LottoTicketEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<LottoTicketEntity> findByRoundOrderByGameNo(Integer round);

    List<LottoTicketEntity> findByTicketGroupOrderByGameNo(String ticketGroup);

    /** 같은 회차 + 같은 발행일시로 이미 등록된 게임들 (중복 용지 판별용). */
    List<LottoTicketEntity> findByRoundAndIssuedAt(Integer round, LocalDateTime issuedAt);
}
