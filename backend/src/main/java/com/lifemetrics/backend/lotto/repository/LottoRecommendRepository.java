package com.lifemetrics.backend.lotto.repository;

import com.lifemetrics.backend.lotto.entity.LottoRecommendEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LottoRecommendRepository
        extends JpaRepository<LottoRecommendEntity, Long> {

    // ✅ 이 줄이 빠져 있었음
    List<LottoRecommendEntity> findByRound(Integer round);

    // (선택) 정렬까지 하고 싶으면 이게 더 좋음
    // List<LottoRecommendEntity> findByRoundOrderByGameNo(Integer round);

    /** 벌크 동기화용: id > after 를 id 오름차순으로. */
    @Query("select r from LottoRecommendEntity r where r.id > :after order by r.id asc")
    List<LottoRecommendEntity> findRowsAfter(@Param("after") long after, Pageable pageable);
}
