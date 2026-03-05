package com.lifemetrics.backend.service;

import com.lifemetrics.backend.dto.MuscleHeatmapResponse;
import com.lifemetrics.backend.entity.ExerciseMuscleMap;
import com.lifemetrics.backend.entity.ExerciseSet;
import com.lifemetrics.backend.entity.MuscleGroup;
import com.lifemetrics.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MuscleHeatmapService {

    private final ExerciseSessionRepository sessionRepository;
    private final ExerciseLogRepository logRepository;
    private final ExerciseSetRepository setRepository;
    private final ExerciseMuscleMapRepository muscleMapRepository;
    private final MuscleGroupRepository muscleGroupRepository;
    private final ExerciseHistoryRepository historyRepository;

    /**
     * 근육 히트맵 데이터 조회
     *
     * @param userId 사용자 ID
     * @param month  "2026-02" 형식
     * @return 근육별 활성화 점수
     */
    @Transactional(readOnly = true)
    public MuscleHeatmapResponse getHeatmap(Long userId, String month) {
        // 1. 기간 계산
        String[] parts = month.split("-");
        int year = Integer.parseInt(parts[0]);
        int mon = Integer.parseInt(parts[1]);
        LocalDate startDate = LocalDate.of(year, mon, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());

        // 2. 해당 월 세션 조회
        var sessions = sessionRepository.findByUserIdAndMonth(userId, startDate, endDate);
        if (sessions.isEmpty()) {
            return buildEmptyResponse(month);
        }

        List<Long> sessionIds = sessions.stream()
                .map(s -> s.getId())
                .toList();

        // 3. 로그 조회 (exerciseItemId별 세트 정보)
        List<Object[]> logRows = historyRepository.findLogsWithItemsBySessionIds(sessionIds);
        List<ExerciseSet> allSets = historyRepository.findSetsBySessionIds(sessionIds);

        // 세트를 logId별 그룹핑
        Map<Long, List<ExerciseSet>> setsByLogId = allSets.stream()
                .collect(Collectors.groupingBy(ExerciseSet::getLogId));

        // 4. exerciseItemId별 볼륨/세트 수 집계
        //    볼륨 = sum(weight * reps) per exercise
        Map<Long, Double> volumeByExerciseId = new HashMap<>();
        Map<Long, Integer> setsByExerciseId = new HashMap<>();

        for (Object[] row : logRows) {
            var log = (com.lifemetrics.backend.entity.ExerciseLog) row[0];
            Long exerciseItemId = log.getExerciseItemId();

            List<ExerciseSet> logSets = setsByLogId.getOrDefault(log.getId(), Collections.emptyList());
            int setCount = logSets.size();
            double volume = logSets.stream()
                    .mapToDouble(s -> {
                        double w = s.getWeight() != null ? s.getWeight().doubleValue() : 0;
                        int r = s.getReps() != null ? s.getReps() : 0;
                        return w * r;
                    })
                    .sum();

            volumeByExerciseId.merge(exerciseItemId, volume, Double::sum);
            setsByExerciseId.merge(exerciseItemId, setCount, Integer::sum);
        }

        // 5. 근육 매핑 조회
        List<Long> exerciseItemIds = new ArrayList<>(volumeByExerciseId.keySet());
        List<ExerciseMuscleMap> mappings = muscleMapRepository.findByExerciseItemIdIn(exerciseItemIds);

        // 6. 근육별 점수 계산
        //    근육 점수 = sum(운동볼륨 * activation_level / 100)
        Map<Long, Double> muscleVolume = new HashMap<>();
        Map<Long, Integer> muscleSets = new HashMap<>();

        for (ExerciseMuscleMap mapping : mappings) {
            Long exerciseId = mapping.getExerciseItemId();
            Long muscleId = mapping.getMuscleGroupId();
            double activationRate = (mapping.getActivationLevel() != null ? mapping.getActivationLevel() : 100) / 100.0;

            double vol = volumeByExerciseId.getOrDefault(exerciseId, 0.0) * activationRate;
            int sets = (int) (setsByExerciseId.getOrDefault(exerciseId, 0) * activationRate);

            muscleVolume.merge(muscleId, vol, Double::sum);
            muscleSets.merge(muscleId, sets, Integer::sum);
        }

        // 7. 정규화 (0~100 스코어)
        double maxVolume = muscleVolume.values().stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(1.0);

        // 8. 세부 근육만 조회 (parent_id != null)
        List<MuscleGroup> detailMuscles = muscleGroupRepository.findByParentIdIsNotNullOrderBySortOrder();

        List<MuscleHeatmapResponse.MuscleScore> scores = detailMuscles.stream()
                .map(mg -> {
                    double vol = muscleVolume.getOrDefault(mg.getId(), 0.0);
                    int sets = muscleSets.getOrDefault(mg.getId(), 0);
                    int score = maxVolume > 0 ? (int) Math.round(vol / maxVolume * 100) : 0;

                    return MuscleHeatmapResponse.MuscleScore.builder()
                            .muscleGroupId(mg.getId())
                            .nameKo(mg.getNameKo())
                            .nameEn(mg.getNameEn())
                            .parentId(mg.getParentId())
                            .bodyPart(mg.getBodyPart() != null ? mg.getBodyPart().name() : null)
                            .totalSets(sets)
                            .totalVolume(Math.round(vol * 10) / 10.0)
                            .score(score)
                            .build();
                })
                .toList();

        return MuscleHeatmapResponse.builder()
                .muscles(scores)
                .period(month)
                .build();
    }

    private MuscleHeatmapResponse buildEmptyResponse(String month) {
        List<MuscleGroup> detailMuscles = muscleGroupRepository.findByParentIdIsNotNullOrderBySortOrder();

        List<MuscleHeatmapResponse.MuscleScore> scores = detailMuscles.stream()
                .map(mg -> MuscleHeatmapResponse.MuscleScore.builder()
                        .muscleGroupId(mg.getId())
                        .nameKo(mg.getNameKo())
                        .nameEn(mg.getNameEn())
                        .parentId(mg.getParentId())
                        .bodyPart(mg.getBodyPart() != null ? mg.getBodyPart().name() : null)
                        .totalSets(0)
                        .totalVolume(0)
                        .score(0)
                        .build())
                .toList();

        return MuscleHeatmapResponse.builder()
                .muscles(scores)
                .period(month)
                .build();
    }
}