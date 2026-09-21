package com.lifemetrics.backend.lotto.repository;

import com.lifemetrics.backend.lotto.dto.LottoRoundDto;
import com.lifemetrics.backend.lotto.entity.LottoNumberEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LottoNumberRepository
        extends JpaRepository<LottoNumberEntity, Integer> {

    @Query("select max(l.round) from LottoNumberEntity l")
    Integer findMaxRound();

    @Query("""
        select new com.lifemetrics.backend.lotto.dto.LottoRoundDto(
            l.round, l.drawDate
        )
        from LottoNumberEntity l
        order by l.round desc
    """)
    List<LottoRoundDto> findAllRounds();

    /** 벌크 동기화용: round > after 인 당첨번호를 회차 오름차순으로. */
    @Query("""
        select l
        from LottoNumberEntity l
        where l.round > :after
        order by l.round asc
    """)
    List<LottoNumberEntity> findRowsAfter(@Param("after") int after, Pageable pageable);
}
