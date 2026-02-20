package com.lifemetrics.backend.lotto.repository;

import com.lifemetrics.backend.lotto.dto.LottoRoundDto;
import com.lifemetrics.backend.lotto.entity.LottoNumberEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
}
