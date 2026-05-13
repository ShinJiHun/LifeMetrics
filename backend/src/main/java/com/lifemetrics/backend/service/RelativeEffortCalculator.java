// service/RelativeEffortCalculator.java
package com.lifemetrics.backend.service;

import com.lifemetrics.backend.entity.ActivityPoint;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Strava 스타일의 Relative Effort 점수를 계산합니다.
 * <p>
 * 기본 알고리즘은 TRIMP(Training Impulse) 기반으로,
 * 각 심박존(HR Zone)에서 보낸 시간에 가중치를 곱한 누적 점수입니다.
 * <p>
 * 심박 데이터가 없을 경우 파워/시간/거리 기반 보조 추정도 제공합니다.
 * <p>
 * <b>입력 요구사항:</b> {@code points} 리스트는 {@code seq} 오름차순으로 정렬되어
 * 있어야 합니다. Repository 호출 시 {@code OrderBySeqAsc}를 붙이거나
 * Service에서 정렬해서 넘겨주세요.
 */
@Component
public class RelativeEffortCalculator {

    /**
     * 사용자의 안정시 심박(HRrest)과 최대심박(HRmax)이 필요합니다.
     * 추후 User 엔티티에서 가져오도록 확장하세요.
     */
    public Integer calculate(List<ActivityPoint> points,
                             int restingHr,
                             int maxHr) {
        if (points == null || points.isEmpty()) return null;
        if (maxHr <= restingHr) return null;

        double score = 0.0;

        for (int i = 1; i < points.size(); i++) {
            ActivityPoint cur = points.get(i);
            ActivityPoint prev = points.get(i - 1);

            if (cur.getHeartRate() == null) continue;
            if (cur.getPointTime() == null || prev.getPointTime() == null) continue;

            // 두 포인트 사이의 시간(초)
            long deltaSec = java.time.Duration.between(
                    prev.getPointTime(), cur.getPointTime()
            ).getSeconds();
            if (deltaSec <= 0 || deltaSec > 60) continue; // 일시정지 구간 제외

            double hr = cur.getHeartRate();
            // 심박 강도 비율 (HRR: Heart Rate Reserve)
            double hrr = (hr - restingHr) / (maxHr - restingHr);
            if (hrr <= 0) continue;
            if (hrr > 1.0) hrr = 1.0;

            // TRIMP 가중치 (Banister 공식 단순화 버전, 남성 기준 1.92)
            // 분 단위로 환산해서 곱함
            double weighted = (deltaSec / 60.0) * hrr * 0.64 * Math.exp(1.92 * hrr);

            score += weighted;
        }

        return (int) Math.round(score);
    }

    /**
     * 심박 데이터가 없을 때 사용하는 폴백 추정.
     * 이동시간(분) × 평균속도 가중치로 대략적 RE를 만듭니다.
     */
    public Integer estimateFromSpeed(int movingTimeSec, double avgSpeedKmh) {
        if (movingTimeSec <= 0 || avgSpeedKmh <= 0) return null;
        double minutes = movingTimeSec / 60.0;
        // 25km/h = 강도 1.0 기준
        double intensity = avgSpeedKmh / 25.0;
        return (int) Math.round(minutes * intensity * 1.5);
    }
}
