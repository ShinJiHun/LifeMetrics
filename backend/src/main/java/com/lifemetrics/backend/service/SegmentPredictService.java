package com.lifemetrics.backend.service;

import com.lifemetrics.backend.dto.SegmentPredictRequest;
import com.lifemetrics.backend.dto.SegmentPredictResponse;
import com.lifemetrics.backend.entity.ActivityCore;
import com.lifemetrics.backend.repository.ActivityCoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SegmentPredictService {

    private final ActivityCoreRepository activityRepo;

    public SegmentPredictResponse predict(SegmentPredictRequest req) {
        List<SegmentPredictResponse.SegmentResultDto> results = new ArrayList<>();
        int totalMinutes = 0;

        for (SegmentPredictRequest.SegmentDto seg : req.getSegments()) {
            SegmentPredictResponse.SegmentResultDto result = predictSegment(
                    req.getUserId(), seg
            );
            results.add(result);
            totalMinutes += result.getPredictedMinutes();
        }

        // 완주 예상 시각 계산
        String finishTime = calcFinishTime(req.getStartTime(), totalMinutes);

        return SegmentPredictResponse.builder()
                .eventName(req.getEventName())
                .startTime(req.getStartTime())
                .totalPredictedMinutes(totalMinutes)
                .totalPredictedTime(formatMinutes(totalMinutes))
                .estimatedFinishTime(finishTime)
                .segments(results)
                .build();
    }

    private SegmentPredictResponse.SegmentResultDto predictSegment(
            Long userId, SegmentPredictRequest.SegmentDto seg) {

        // 유사 기록 상위 3개 조회
        List<ActivityCore> similars = activityRepo.findSimilarActivities(
                userId, seg.getDistanceKm(), seg.getAscentM(),
                PageRequest.of(0, 3)
        );

        if (similars.isEmpty()) {
            // 유사 기록 없으면 평속 20km/h로 추정
            int predicted = (int) (seg.getDistanceKm() / 20.0 * 60);
            return SegmentPredictResponse.SegmentResultDto.builder()
                    .index(seg.getIndex())
                    .label(seg.getLabel())
                    .distanceKm(seg.getDistanceKm())
                    .ascentM(seg.getAscentM())
                    .predictedMinutes(predicted)
                    .predictedTime(formatMinutes(predicted))
                    .predictedAvgSpeed(20.0)
                    .build();
        }

        // 가장 유사한 기록 사용
        ActivityCore best = similars.get(0);

        // 유사도 점수 계산
        double simScore = Math.abs(best.getTotalDistance() / 1000.0 - seg.getDistanceKm()) / seg.getDistanceKm() * 0.4
                        + Math.abs(safeDouble(best.getTotalAscent()) - seg.getAscentM()) / seg.getAscentM() * 0.6;

        // 유사 기록의 elapsed_time 기준 평속 계산
        double actualDistKm = best.getTotalDistance() / 1000.0;
        double actualElapsedHours = best.getElapsedTime() / 3600.0;
        double actualAvgSpeed = actualDistKm / actualElapsedHours; // km/h

        // 고도 보정 평속 계산 (÷20 공식)
        double actualEffDist = actualDistKm + (safeDouble(best.getTotalAscent()) / 20.0);
        double adjustedSpeed = actualEffDist / actualElapsedHours;

        // 구간 예상시간 계산
        // 실효거리 기준으로 같은 보정평속 적용
        double segEffDist = seg.getDistanceKm() + (seg.getAscentM() / 20.0);
        double predictedHours = segEffDist / adjustedSpeed;
        int predictedMinutes = (int) Math.round(predictedHours * 60);
        double predictedAvgSpeed = seg.getDistanceKm() / predictedHours;

        String similarDate = best.getStartTime() != null
                ? best.getStartTime().toLocalDate().toString()
                : "unknown";

        log.info("구간[{}] 유사기록: {} ({}km, {}m) → 예상 {}분",
                seg.getLabel(), similarDate,
                Math.round(actualDistKm), Math.round(safeDouble(best.getTotalAscent())),
                predictedMinutes);

        return SegmentPredictResponse.SegmentResultDto.builder()
                .index(seg.getIndex())
                .label(seg.getLabel())
                .distanceKm(seg.getDistanceKm())
                .ascentM(seg.getAscentM())
                .similarActivityId(best.getId())
                .similarDate(similarDate)
                .similarDistanceKm(Math.round(actualDistKm * 10) / 10.0)
                .similarAscentM(Math.round(safeDouble(best.getTotalAscent())))
                .similarityScore(Math.round(simScore * 1000) / 1000.0)
                .predictedMinutes(predictedMinutes)
                .predictedTime(formatMinutes(predictedMinutes))
                .predictedAvgSpeed(Math.round(predictedAvgSpeed * 10) / 10.0)
                .adjustedSpeed(Math.round(adjustedSpeed * 10) / 10.0)
                .build();
    }

    // ── 유틸 ─────────────────────────────────────────────────────

    private String calcFinishTime(String startTime, int totalMinutes) {
        try {
            LocalTime start = LocalTime.parse(startTime, DateTimeFormatter.ofPattern("HH:mm"));
            LocalTime finish = start.plusMinutes(totalMinutes);
            return finish.format(DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception e) {
            return "계산 불가";
        }
    }

    private String formatMinutes(int minutes) {
        int h = minutes / 60;
        int m = minutes % 60;
        return String.format("%d시간 %02d분", h, m);
    }

    private double safeDouble(Double value) {
        return value != null ? value : 0.0;
    }
}
