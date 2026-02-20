package com.lifemetrics.backend.lotto.repository;

import com.lifemetrics.backend.lotto.entity.LottoRecommendEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LottoRecommendRepository
        extends JpaRepository<LottoRecommendEntity, Long> {

    // ✅ 이 줄이 빠져 있었음
    List<LottoRecommendEntity> findByRound(Integer round);

    // (선택) 정렬까지 하고 싶으면 이게 더 좋음
    // List<LottoRecommendEntity> findByRoundOrderByGameNo(Integer round);
}
