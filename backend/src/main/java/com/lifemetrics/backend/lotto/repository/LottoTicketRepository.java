package com.lifemetrics.backend.lotto.repository;

import com.lifemetrics.backend.lotto.entity.LottoTicketEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LottoTicketRepository extends JpaRepository<LottoTicketEntity, Long> {

    List<LottoTicketEntity> findAllByOrderByCreatedAtDesc();

    List<LottoTicketEntity> findByRoundOrderByGameNo(Integer round);

    List<LottoTicketEntity> findByTicketGroupOrderByGameNo(String ticketGroup);
}
