package com.lifemetrics.backend.service;

import com.lifemetrics.backend.dto.ExerciseHistoryResponse;
import com.lifemetrics.backend.dto.ExerciseLogRequest;
import com.lifemetrics.backend.entity.*;
import com.lifemetrics.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExerciseLogService {

    private final ExerciseSessionRepository sessionRepository;
    private final ExerciseLogRepository logRepository;
    private final ExerciseSetRepository setRepository;
    private final ExerciseItemRepository itemRepository;
    private final ExerciseCategoryRepository categoryRepository;
    private final ExerciseHistoryRepository historyRepository;
    private final ExerciseMuscleMapRepository muscleMapRepository;
    private final MuscleGroupRepository muscleGroupRepository;

    // 카테고리별 MET 값
    private static final Map<String, Double> CATEGORY_MET = Map.ofEntries(
            Map.entry("가슴", 6.0),
            Map.entry("등", 5.5),
            Map.entry("어깨", 5.0),
            Map.entry("이두", 4.5),
            Map.entry("삼두", 4.5),
            Map.entry("전완", 4.0),
            Map.entry("하체", 6.5),
            Map.entry("복근", 4.0),
            Map.entry("코어", 4.0),
            Map.entry("전신", 6.0)
    );
    private static final double DEFAULT_MET = 5.5;
    private static final double DEFAULT_WEIGHT_KG = 75.0;
    private static final int SECONDS_PER_SET = 120; // 운동 60초 + 휴식 60초

    // ================================================================
    // saveExerciseLog
    // ================================================================
    @Transactional
    public void saveExerciseLog(ExerciseLogRequest request) {
        LocalDate sessionDate = LocalDate.parse(request.getSessionDate());

        ExerciseSession session = sessionRepository
                .findByUserIdAndSessionDate(request.getUserId(), sessionDate)
                .orElseGet(() -> {
                    ExerciseSession newSession = new ExerciseSession();
                    newSession.setUserId(request.getUserId());
                    newSession.setSessionDate(sessionDate);
                    newSession.setIsPT(request.getIsPT() != null ? request.getIsPT() : false);
                    newSession.setCreatedAt(LocalDateTime.now());
                    newSession.setUpdatedAt(LocalDateTime.now());
                    return sessionRepository.save(newSession);
                });

        if (request.getIsPT() != null) {
            session.setIsPT(request.getIsPT());
            session.setUpdatedAt(LocalDateTime.now());
            sessionRepository.save(session);
        }

        for (ExerciseLogRequest.ExerciseEntry entry : request.getExercises()) {
            ExerciseLog log = new ExerciseLog();
            log.setSessionId(session.getId());
            log.setExerciseItemId(entry.getExerciseItemId());
            log.setSets(entry.getSets().size());
            log.setDurationSec(entry.getRestTimeSec());
            log.setMemo(entry.getMemo());
            log.setCreatedAt(LocalDateTime.now());

            double avgWeight = entry.getSets().stream()
                    .mapToDouble(s -> s.getWeight() != null ? s.getWeight() : 0)
                    .average().orElse(0);
            int avgReps = (int) entry.getSets().stream()
                    .mapToInt(s -> s.getReps() != null ? s.getReps() : 0)
                    .average().orElse(0);

            log.setWeight(BigDecimal.valueOf(avgWeight));
            log.setReps(avgReps);

            ExerciseLog savedLog = logRepository.save(log);

            for (ExerciseLogRequest.SetEntry setEntry : entry.getSets()) {
                ExerciseSet exerciseSet = new ExerciseSet();
                exerciseSet.setLogId(savedLog.getId());
                exerciseSet.setSetNumber(setEntry.getSetNumber());
                exerciseSet.setWeight(setEntry.getWeight() != null
                        ? BigDecimal.valueOf(setEntry.getWeight()) : null);
                exerciseSet.setReps(setEntry.getReps());
                exerciseSet.setCreatedAt(LocalDateTime.now());
                setRepository.save(exerciseSet);
            }
        }
    }

    // ================================================================
    // getHistory - 근육 매핑 + 칼로리 포함 버전
    // ================================================================
    @Transactional(readOnly = true)
    public List<ExerciseHistoryResponse> getHistory(Long userId, String month) {
        String[] parts = month.split("-");
        int year = Integer.parseInt(parts[0]);
        int mon = Integer.parseInt(parts[1]);

        LocalDate startDate = LocalDate.of(year, mon, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());

        // ── 쿼리 1: 세션 목록 ──
        List<ExerciseSession> sessions = sessionRepository.findByUserIdAndMonth(
                userId, startDate, endDate);

        if (sessions.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> sessionIds = sessions.stream()
                .map(ExerciseSession::getId)
                .toList();

        // ── 쿼리 2: 로그 + 운동명 + 카테고리 (한 방) ──
        List<Object[]> logRows = historyRepository.findLogsWithItemsBySessionIds(sessionIds);

        // ── 쿼리 3: 세트 (한 방) ──
        List<ExerciseSet> allSets = historyRepository.findSetsBySessionIds(sessionIds);

        // ── 쿼리 4: 근육 매핑 (한 방) ──
        List<Long> exerciseItemIds = logRows.stream()
                .map(row -> ((ExerciseLog) row[0]).getExerciseItemId())
                .distinct()
                .toList();

        List<ExerciseMuscleMap> allMappings = exerciseItemIds.isEmpty()
                ? Collections.emptyList()
                : muscleMapRepository.findByExerciseItemIdIn(exerciseItemIds);

        Set<Long> muscleGroupIds = allMappings.stream()
                .map(ExerciseMuscleMap::getMuscleGroupId)
                .collect(Collectors.toSet());

        Map<Long, MuscleGroup> muscleGroupMap = muscleGroupIds.isEmpty()
                ? Collections.emptyMap()
                : muscleGroupRepository.findAllById(muscleGroupIds).stream()
                .collect(Collectors.toMap(MuscleGroup::getId, mg -> mg));

        Map<Long, List<ExerciseMuscleMap>> mappingsByExerciseId = allMappings.stream()
                .collect(Collectors.groupingBy(ExerciseMuscleMap::getExerciseItemId));

        Map<Long, List<ExerciseSet>> setsByLogId = allSets.stream()
                .collect(Collectors.groupingBy(ExerciseSet::getLogId));

        Map<Long, List<Object[]>> logsBySessionId = logRows.stream()
                .collect(Collectors.groupingBy(row -> ((ExerciseLog) row[0]).getSessionId()));

        // ── 조립 ──
        return sessions.stream().map(session -> {
            List<Object[]> sessionLogs = logsBySessionId.getOrDefault(
                    session.getId(), Collections.emptyList());

            List<ExerciseHistoryResponse.ExerciseLogDto> logDtos = sessionLogs.stream()
                    .map(row -> {
                        ExerciseLog log = (ExerciseLog) row[0];
                        String nameKo = (String) row[1];
                        String nameEn = (String) row[2];
                        String categoryName = (String) row[3];

                        List<ExerciseSet> logSets = setsByLogId.getOrDefault(
                                log.getId(), Collections.emptyList());

                        List<ExerciseHistoryResponse.ExerciseSetDto> setDtos = logSets.stream()
                                .map(set -> ExerciseHistoryResponse.ExerciseSetDto.builder()
                                        .id(set.getId())
                                        .setNumber(set.getSetNumber())
                                        .weight(set.getWeight() != null
                                                ? set.getWeight().doubleValue() : 0)
                                        .reps(set.getReps())
                                        .build())
                                .toList();

                        List<ExerciseMuscleMap> exerciseMappings = mappingsByExerciseId
                                .getOrDefault(log.getExerciseItemId(), Collections.emptyList());

                        List<ExerciseHistoryResponse.MuscleMappingDto> muscleMappingDtos =
                                exerciseMappings.stream()
                                        .map(emm -> {
                                            MuscleGroup mg = muscleGroupMap.get(emm.getMuscleGroupId());
                                            return ExerciseHistoryResponse.MuscleMappingDto.builder()
                                                    .muscleGroupId(emm.getMuscleGroupId())
                                                    .muscleName(mg != null ? mg.getNameKo() : "")
                                                    .role(emm.getRole() != null ? emm.getRole().name() : "PRIMARY")
                                                    .activationLevel(emm.getActivationLevel() != null
                                                            ? emm.getActivationLevel() : 100)
                                                    .build();
                                        })
                                        .toList();

                        return ExerciseHistoryResponse.ExerciseLogDto.builder()
                                .id(log.getId())
                                .exerciseItemId(log.getExerciseItemId())
                                .exerciseNameKo(nameKo)
                                .exerciseNameEn(nameEn)
                                .categoryName(categoryName)
                                .restTimeSec(log.getDurationSec())
                                .memo(log.getMemo())
                                .sets(setDtos)
                                .muscleMappings(muscleMappingDtos)
                                .build();
                    })
                    .toList();

            // ── 세션별 칼로리 계산 ──
            int sessionTotalSets = 0;
            double sessionTotalVolume = 0;
            double weightedMetSum = 0;

            for (ExerciseHistoryResponse.ExerciseLogDto log : logDtos) {
                int logSets = log.getSets().size();
                sessionTotalSets += logSets;

                // 카테고리별 MET 가중합
                double met = CATEGORY_MET.getOrDefault(log.getCategoryName(), DEFAULT_MET);
                weightedMetSum += met * logSets;

                for (ExerciseHistoryResponse.ExerciseSetDto set : log.getSets()) {
                    sessionTotalVolume += set.getWeight() * set.getReps();
                }
            }

            // 가중 평균 MET
            double avgMet = sessionTotalSets > 0 ? weightedMetSum / sessionTotalSets : DEFAULT_MET;

            // 시간 계산: 1세트 = 120초 (운동 60초 + 휴식 60초)
            int estimatedSeconds = sessionTotalSets * SECONDS_PER_SET;
            double estimatedHours = estimatedSeconds / 3600.0;
            int estimatedMinutes = estimatedSeconds / 60;

            // 칼로리 = MET × 체중(kg) × 시간(hour)
            double estimatedCalories = avgMet * DEFAULT_WEIGHT_KG * estimatedHours;

            return ExerciseHistoryResponse.builder()
                    .id(session.getId())
                    .sessionDate(session.getSessionDate().toString())
                    .isPT(session.getIsPT())
                    .memo(session.getMemo())
                    .logs(logDtos)
                    .estimatedCalories(Math.round(estimatedCalories * 10) / 10.0)
                    .totalVolume(Math.round(sessionTotalVolume * 10) / 10.0)
                    .totalSets(sessionTotalSets)
                    .estimatedMinutes(estimatedMinutes)
                    .build();
        }).toList();
    }
}