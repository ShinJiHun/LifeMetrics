package com.lifemetrics.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifemetrics.backend.dto.ActivityAnalysisResponse;
import com.lifemetrics.backend.dto.ExerciseAnalysisResponse;
import com.lifemetrics.backend.dto.ExerciseHistoryResponse;
import com.lifemetrics.backend.entity.AiAnalysis;
import com.lifemetrics.backend.entity.ActivityWeatherPoint;
import com.lifemetrics.backend.repository.AiAnalysisRepository;
import com.lifemetrics.backend.entity.ActivityCore;
import com.lifemetrics.backend.repository.ActivityCoreRepository;
import com.lifemetrics.backend.repository.ActivityWeatherPointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiAnalysisService {

    private final AiAnalysisRepository analysisRepo;
    private final ActivityCoreRepository activityRepo;
    private final ActivityWeatherPointRepository weatherPointRepo;
    private final ExerciseLogService exerciseLogService;
    private final ObjectMapper objectMapper;

    @Value("${anthropic.api-key:}")
    private String anthropicApiKey;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    // ================================================================
    // 라이딩 분석 (날씨 포함)
    // ================================================================

    public ActivityAnalysisResponse analyzeActivity(Long userId, Long activityId) {
        Optional<AiAnalysis> existing = analysisRepo
                .findByUserIdAndAnalysisTypeAndTargetId(userId, "activity", activityId);

        if (existing.isPresent()) {
            return toActivityResponse(existing.get());
        }

        ActivityCore activity = activityRepo.findById(activityId)
                .orElseThrow(() -> new RuntimeException("Activity not found: " + activityId));

        // 구간별 날씨 조회
        List<ActivityWeatherPoint> weatherPoints = weatherPointRepo
                .findByActivityCoreIdOrderBySeq(activityId);

        String prompt = buildActivityPrompt(activity, weatherPoints);
        String analysisJson = callClaudeApi(prompt);

        AiAnalysis analysis = new AiAnalysis();
        analysis.setUserId(userId);
        analysis.setAnalysisType("activity");
        analysis.setTargetId(activityId);
        analysis.setAnalysisData(analysisJson);
        analysisRepo.save(analysis);

        return toActivityResponse(analysis);
    }

    public int analyzeAllActivities(Long userId) {
        List<ActivityCore> activities = activityRepo.findByUserIdOrderByStartTimeDesc(userId);

        int count = 0;
        for (ActivityCore activity : activities) {
            Optional<AiAnalysis> existing = analysisRepo
                    .findByUserIdAndAnalysisTypeAndTargetId(userId, "activity", activity.getId());

            if (existing.isPresent()) {
                System.out.println("⏭️ 이미 분석됨: " + activity.getId());
                continue;
            }

            try {
                List<ActivityWeatherPoint> weatherPoints = weatherPointRepo
                        .findByActivityCoreIdOrderBySeq(activity.getId());

                String prompt = buildActivityPrompt(activity, weatherPoints);
                String analysisJson = callClaudeApi(prompt);

                AiAnalysis analysis = new AiAnalysis();
                analysis.setUserId(userId);
                analysis.setAnalysisType("activity");
                analysis.setTargetId(activity.getId());
                analysis.setAnalysisData(analysisJson);
                analysisRepo.save(analysis);

                count++;
                System.out.println("✅ 분석 완료: " + activity.getId() + " (" + count + "개)");
                Thread.sleep(1000);

            } catch (Exception e) {
                System.out.println("❌ 분석 실패: " + activity.getId() + " - " + e.getMessage());
            }
        }
        return count;
    }

    public ActivityAnalysisResponse getAnalysis(Long userId, Long activityId) {
        return analysisRepo
                .findByUserIdAndAnalysisTypeAndTargetId(userId, "activity", activityId)
                .map(this::toActivityResponse)
                .orElse(null);
    }

    // ================================================================
    // 운동 세션 분석
    // ================================================================

    public ExerciseAnalysisResponse analyzeExerciseSession(Long userId, Long sessionId) {
        Optional<AiAnalysis> existing = analysisRepo
                .findByUserIdAndAnalysisTypeAndTargetId(userId, "exercise_session", sessionId);

        if (existing.isPresent()) {
            return toExerciseResponse(existing.get());
        }

        ExerciseHistoryResponse session = findSessionById(userId, sessionId);
        if (session == null) {
            throw new RuntimeException("Exercise session not found: " + sessionId);
        }

        String prompt = buildExerciseSessionPrompt(session);
        String analysisJson = callClaudeApi(prompt);

        AiAnalysis analysis = new AiAnalysis();
        analysis.setUserId(userId);
        analysis.setAnalysisType("exercise_session");
        analysis.setTargetId(sessionId);
        analysis.setAnalysisData(analysisJson);
        analysisRepo.save(analysis);

        return toExerciseResponse(analysis);
    }

    public ExerciseAnalysisResponse getExerciseAnalysis(Long userId, Long sessionId) {
        return analysisRepo
                .findByUserIdAndAnalysisTypeAndTargetId(userId, "exercise_session", sessionId)
                .map(this::toExerciseResponse)
                .orElse(null);
    }

    public int analyzeAllExerciseSessions(Long userId) {
        int count = 0;

        for (int i = 0; i < 12; i++) {
            java.time.LocalDate date = java.time.LocalDate.now().minusMonths(i);
            String month = String.format("%d-%02d", date.getYear(), date.getMonthValue());

            List<ExerciseHistoryResponse> sessions = exerciseLogService.getHistory(userId, month);

            for (ExerciseHistoryResponse session : sessions) {
                Optional<AiAnalysis> existing = analysisRepo
                        .findByUserIdAndAnalysisTypeAndTargetId(userId, "exercise_session", session.getId());

                if (existing.isPresent()) {
                    System.out.println("⏭️ 이미 분석됨: " + session.getId() + " (" + session.getSessionDate() + ")");
                    continue;
                }

                try {
                    String prompt = buildExerciseSessionPrompt(session);
                    String analysisJson = callClaudeApi(prompt);

                    AiAnalysis analysis = new AiAnalysis();
                    analysis.setUserId(userId);
                    analysis.setAnalysisType("exercise_session");
                    analysis.setTargetId(session.getId());
                    analysis.setAnalysisData(analysisJson);
                    analysisRepo.save(analysis);

                    count++;
                    System.out.println("✅ 운동 분석 완료: " + session.getSessionDate() + " (" + count + "개)");
                    Thread.sleep(1500);

                } catch (Exception e) {
                    System.out.println("❌ 분석 실패: " + session.getSessionDate() + " - " + e.getMessage());
                }
            }
        }
        return count;
    }

    public ExerciseAnalysisResponse analyzeMonthlyExercise(Long userId, String month) {
        String analysisType = "exercise_monthly_" + month;
        Optional<AiAnalysis> existing = analysisRepo
                .findByUserIdAndAnalysisTypeAndTargetId(userId, analysisType, 0L);

        if (existing.isPresent()) {
            return toExerciseResponse(existing.get());
        }

        List<ExerciseHistoryResponse> sessions = exerciseLogService.getHistory(userId, month);
        if (sessions.isEmpty()) {
            throw new RuntimeException("해당 월에 운동 기록이 없습니다: " + month);
        }

        String prompt = buildMonthlyExercisePrompt(sessions, month);
        String analysisJson = callClaudeApi(prompt);

        AiAnalysis analysis = new AiAnalysis();
        analysis.setUserId(userId);
        analysis.setAnalysisType(analysisType);
        analysis.setTargetId(0L);
        analysis.setAnalysisData(analysisJson);
        analysisRepo.save(analysis);

        return toExerciseResponse(analysis);
    }

    // ================================================================
    // 프롬프트 빌더
    // ================================================================

    private String buildActivityPrompt(ActivityCore a, List<ActivityWeatherPoint> weatherPoints) {
        StringBuilder sb = new StringBuilder();
        sb.append("다음 사이클링 활동 데이터를 분석해주세요.\n\n");

        sb.append(String.format("""
                [기본 데이터]
                - 거리: %.1f km
                - 이동 시간: %d분
                - 평균 속도: %.1f km/h
                - 최고 속도: %.1f km/h
                - 획득 고도: %.0f m
                - 평균 심박: %.0f bpm
                - 최고 심박: %.0f bpm
                - 평균 파워: %.0f W
                - 칼로리: %.0f kcal
                
                """,
                safeDouble(a.getTotalDistance()) / 1000,
                safeInt(a.getMovingTime()) / 60,
                safeDouble(a.getAvgSpeed()),
                safeDouble(a.getMaxSpeed()),
                safeDouble(a.getTotalAscent()),
                safeDouble(a.getAvgHeartRate()),
                safeDouble(a.getMaxHeartRate()),
                safeDouble(a.getAvgPower()),
                safeInt(a.getCalories())
        ));

        // 구간별 날씨 데이터 추가
        if (!weatherPoints.isEmpty()) {
            sb.append("[구간별 날씨 (30분 간격)]\n");
            for (ActivityWeatherPoint wp : weatherPoints) {
                String time = wp.getPointTime() != null ? wp.getPointTime().format(TIME_FMT) : "?";
                String desc = wp.getWeatherDesc() != null ? wp.getWeatherDesc() : "";
                String windDir = windDegToDirection(wp.getWindDeg());

                sb.append(String.format("  %s - %.1f°C, 습도%d%%, %s %.1fm/s(%s), %s\n",
                        time,
                        wp.getTemperature() != null ? wp.getTemperature() : 0,
                        wp.getHumidity() != null ? wp.getHumidity().intValue() : 0,
                        windDir,
                        wp.getWindSpeed() != null ? wp.getWindSpeed() : 0,
                        windDir,
                        desc
                ));
            }
            sb.append("\n");
        }

        sb.append("""
                반드시 아래 JSON 형식으로만 응답하세요. 다른 텍스트 없이 JSON만:
                {
                  "summary": "한줄요약 (25자 이내)",
                  "intensity": "낮음/보통/높음/매우높음",
                  "weatherImpact": "날씨가 라이딩에 미친 영향 한줄 (없으면 빈문자열)",
                  "highlights": ["잘한점1", "잘한점2", "잘한점3"],
                  "suggestions": ["개선점1", "개선점2"],
                  "score": 85
                }
                
                분석 시 날씨 영향도 반영해주세요:
                - 맞바람/뒷바람이 속도/파워에 미치는 영향
                - 기온 변화에 따른 체력 소모
                - 비/흐림 등 기상 조건의 영향
                - 고온/저온 환경에서의 퍼포먼스
                """);

        return sb.toString();
    }

    private String buildExerciseSessionPrompt(ExerciseHistoryResponse session) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("다음은 %s 운동 세션 데이터입니다.\n\n", session.getSessionDate()));

        if (session.getIsPT()) {
            sb.append("⚡ PT 세션\n\n");
        }

        double totalVolume = 0;
        int totalSets = 0;

        for (ExerciseHistoryResponse.ExerciseLogDto log : session.getLogs()) {
            sb.append(String.format("### %s (%s) - %s\n",
                    log.getExerciseNameKo(), log.getExerciseNameEn(), log.getCategoryName()));

            if (log.getMuscleMappings() != null && !log.getMuscleMappings().isEmpty()) {
                String muscles = log.getMuscleMappings().stream()
                        .map(mm -> mm.getMuscleName() + "(" + mm.getRole() + ")")
                        .collect(Collectors.joining(", "));
                sb.append("타겟 근육: ").append(muscles).append("\n");
            }

            for (ExerciseHistoryResponse.ExerciseSetDto set : log.getSets()) {
                double volume = set.getWeight() * set.getReps();
                totalVolume += volume;
                totalSets++;
                sb.append(String.format("  %d세트: %.1fkg × %d회 = %.0fkg\n",
                        set.getSetNumber(), set.getWeight(), set.getReps(), volume));
            }

            if (log.getMemo() != null && !log.getMemo().isEmpty()) {
                sb.append("  메모: ").append(log.getMemo()).append("\n");
            }
            sb.append("\n");
        }

        sb.append(String.format("\n총 %d종목, %d세트, 총 볼륨 %.0fkg\n\n",
                session.getLogs().size(), totalSets, totalVolume));

        sb.append("""
                위 운동 세션을 분석해주세요. 반드시 아래 JSON 형식으로만 응답하세요:
                {
                  "summary": "오늘 운동 한줄평 (30자 이내)",
                  "targetMuscles": "주요 타겟 부위 (예: 어깨, 등)",
                  "volumeLevel": "낮음/적정/높음 중 하나",
                  "intensityLevel": "낮음/보통/높음/매우높음 중 하나",
                  "highlights": ["잘한 점 1", "잘한 점 2"],
                  "suggestions": ["개선점/다음에 이렇게 해보세요 1", "개선점 2"],
                  "score": 85
                }
                
                분석 기준:
                - 볼륨(총 무게×횟수)의 적정성
                - 운동 구성의 균형 (주동근/보조근 비율)
                - 세트/횟수 구성의 효율성
                - PT 세션이면 트레이너와 함께한 점 반영
                - score는 0~100 (종합 점수)
                """);

        return sb.toString();
    }

    private String buildMonthlyExercisePrompt(List<ExerciseHistoryResponse> sessions, String month) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s 월간 운동 기록 분석\n\n", month));
        sb.append(String.format("총 운동일: %d일\n", sessions.size()));
        sb.append(String.format("PT 횟수: %d회\n\n",
                sessions.stream().filter(ExerciseHistoryResponse::getIsPT).count()));

        Map<String, Integer> categorySetCount = new java.util.LinkedHashMap<>();
        Map<String, Integer> muscleSetCount = new java.util.LinkedHashMap<>();
        double monthlyVolume = 0;
        int monthlySets = 0;

        for (ExerciseHistoryResponse session : sessions) {
            sb.append(String.format("📅 %s%s\n", session.getSessionDate(),
                    session.getIsPT() ? " (PT)" : ""));

            for (ExerciseHistoryResponse.ExerciseLogDto log : session.getLogs()) {
                String category = log.getCategoryName();
                int sets = log.getSets().size();
                categorySetCount.merge(category, sets, Integer::sum);

                if (log.getMuscleMappings() != null) {
                    for (ExerciseHistoryResponse.MuscleMappingDto mm : log.getMuscleMappings()) {
                        if ("PRIMARY".equals(mm.getRole())) {
                            muscleSetCount.merge(mm.getMuscleName(), sets, Integer::sum);
                        }
                    }
                }

                for (ExerciseHistoryResponse.ExerciseSetDto set : log.getSets()) {
                    monthlyVolume += set.getWeight() * set.getReps();
                    monthlySets++;
                }

                sb.append(String.format("  - %s: %d세트\n", log.getExerciseNameKo(), sets));
            }
            sb.append("\n");
        }

        sb.append(String.format("\n📊 월간 요약: 총 %d세트, 총 볼륨 %.0fkg\n", monthlySets, monthlyVolume));
        sb.append("\n부위별 세트 수:\n");
        categorySetCount.forEach((cat, cnt) -> sb.append(String.format("  %s: %d세트\n", cat, cnt)));
        sb.append("\n주동근별 세트 수:\n");
        muscleSetCount.forEach((muscle, cnt) -> sb.append(String.format("  %s: %d세트\n", muscle, cnt)));

        sb.append("""
                
                위 월간 운동 기록을 종합 분석해주세요. 반드시 아래 JSON 형식으로만 응답하세요:
                {
                  "summary": "이번 달 운동 한줄평 (40자 이내)",
                  "targetMuscles": "가장 많이 운동한 부위",
                  "volumeLevel": "낮음/적정/높음",
                  "intensityLevel": "낮음/보통/높음/매우높음",
                  "highlights": ["잘한 점 1", "잘한 점 2", "잘한 점 3"],
                  "suggestions": ["부족한 부위나 개선점 1", "다음 달 추천 2", "추천 3"],
                  "score": 80
                }
                
                분석 기준:
                - 운동 빈도 (주 몇회?)
                - 부위별 균형 (상체/하체/코어 밸런스)
                - 점진적 과부하 여부
                - 충분한 볼륨인지
                - 약한 부위 추천
                """);

        return sb.toString();
    }

    // ================================================================
    // 풍향 변환
    // ================================================================

    private String windDegToDirection(Double deg) {
        if (deg == null) return "?";
        String[] dirs = {"북", "북북동", "북동", "동북동", "동", "동남동", "남동", "남남동",
                "남", "남남서", "남서", "서남서", "서", "서북서", "북서", "북북서"};
        int idx = (int) Math.round(deg / 22.5) % 16;
        return dirs[idx] + "풍";
    }

    // ================================================================
    // Claude API 호출
    // ================================================================

    private String callClaudeApi(String prompt) {
        if (anthropicApiKey == null || anthropicApiKey.isEmpty()) {
            return "{\"summary\":\"API 키 없음\",\"targetMuscles\":\"\",\"volumeLevel\":\"보통\",\"intensityLevel\":\"보통\",\"highlights\":[],\"suggestions\":[],\"score\":0}";
        }

        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", anthropicApiKey);
        headers.set("anthropic-version", "2023-06-01");

        Map<String, Object> body = Map.of(
                "model", "claude-sonnet-4-20250514",
                "max_tokens", 1000,
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                )
        );

        try {
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    "https://api.anthropic.com/v1/messages",
                    request,
                    String.class
            );

            JsonNode responseJson = objectMapper.readTree(response.getBody());
            String text = responseJson.get("content").get(0).get("text").asText();

            if (text.contains("```")) {
                text = text.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            }

            objectMapper.readTree(text);
            return text;

        } catch (Exception e) {
            System.out.println("❌ Claude API 에러: " + e.getMessage());
            return "{\"summary\":\"분석 실패\",\"targetMuscles\":\"\",\"volumeLevel\":\"보통\",\"intensityLevel\":\"보통\",\"highlights\":[],\"suggestions\":[],\"score\":0}";
        }
    }

    // ================================================================
    // 헬퍼 메서드
    // ================================================================

    private ExerciseHistoryResponse findSessionById(Long userId, Long sessionId) {
        for (int i = 0; i < 12; i++) {
            java.time.LocalDate date = java.time.LocalDate.now().minusMonths(i);
            String month = String.format("%d-%02d", date.getYear(), date.getMonthValue());
            List<ExerciseHistoryResponse> sessions = exerciseLogService.getHistory(userId, month);
            for (ExerciseHistoryResponse session : sessions) {
                if (session.getId().equals(sessionId)) {
                    return session;
                }
            }
        }
        return null;
    }

    private ActivityAnalysisResponse toActivityResponse(AiAnalysis analysis) {
        try {
            JsonNode json = objectMapper.readTree(analysis.getAnalysisData());
            return ActivityAnalysisResponse.builder()
                    .id(analysis.getId())
                    .activityId(analysis.getTargetId())
                    .model("claude-sonnet-4-20250514")
                    .createdAt(analysis.getCreatedAt().toString())
                    .summary(json.path("summary").asText())
                    .intensity(json.path("intensity").asText())
                    .highlights(jsonArrayToList(json.path("highlights")))
                    .suggestions(jsonArrayToList(json.path("suggestions")))
                    .score(json.path("score").asInt())
                    .build();
        } catch (JsonProcessingException e) {
            return ActivityAnalysisResponse.builder()
                    .id(analysis.getId())
                    .activityId(analysis.getTargetId())
                    .summary("파싱 실패")
                    .score(0)
                    .build();
        }
    }

    private ExerciseAnalysisResponse toExerciseResponse(AiAnalysis analysis) {
        try {
            JsonNode json = objectMapper.readTree(analysis.getAnalysisData());
            return ExerciseAnalysisResponse.builder()
                    .id(analysis.getId())
                    .sessionId(analysis.getTargetId())
                    .model("claude-sonnet-4-20250514")
                    .createdAt(analysis.getCreatedAt().toString())
                    .summary(json.path("summary").asText())
                    .targetMuscles(json.path("targetMuscles").asText())
                    .volumeLevel(json.path("volumeLevel").asText())
                    .intensityLevel(json.path("intensityLevel").asText())
                    .highlights(jsonArrayToList(json.path("highlights")))
                    .suggestions(jsonArrayToList(json.path("suggestions")))
                    .score(json.path("score").asInt())
                    .build();
        } catch (JsonProcessingException e) {
            return ExerciseAnalysisResponse.builder()
                    .id(analysis.getId())
                    .sessionId(analysis.getTargetId())
                    .summary("파싱 실패")
                    .score(0)
                    .build();
        }
    }

    private double safeDouble(Double value) {
        return value != null ? value : 0.0;
    }

    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    private List<String> jsonArrayToList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                list.add(item.asText());
            }
        }
        return list;
    }
}