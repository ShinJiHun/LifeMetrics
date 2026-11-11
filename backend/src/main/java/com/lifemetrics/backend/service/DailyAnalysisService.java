package com.lifemetrics.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifemetrics.backend.dto.ExerciseHistoryResponse;
import com.lifemetrics.backend.entity.ActivityCore;
import com.lifemetrics.backend.entity.AiAnalysis;
import com.lifemetrics.backend.entity.UserBodyRecord;
import com.lifemetrics.backend.health.service.HealthDataQueryService;
import com.lifemetrics.backend.health.service.HealthDataQueryService.DateRange;
import com.lifemetrics.backend.repository.ActivityCoreRepository;
import com.lifemetrics.backend.repository.AiAnalysisRepository;
import com.lifemetrics.backend.repository.UserBodyRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 하루치 기록(활동량·운동·식단·피트니스·라이딩·체성분)을 묶어 AI 로 분석한다.
 * <p>
 * 분석에는 그 날짜의 기록만 넣는다. 입력의 해시를 함께 저장해 두어, 입력이 그대로인 날은 다시 호출하지 않고
 * 폰 동기화나 수기 입력이 늦게 들어와 입력이 바뀐 날만 다시 분석한다. 실패하면 기존 분석을 덮어쓰지 않는다.
 */
@Service
@RequiredArgsConstructor
public class DailyAnalysisService {

    static final String ANALYSIS_TYPE = "daily";
    /** ai_analysis.target_id 는 NOT NULL 이고, 기간 단위 분석은 0 을 쓴다. */
    static final long TARGET_ID = 0L;
    public static final ZoneId KST = ZoneId.of("Asia/Seoul");
    /**
     * 동기화·입력이 늦거나 호출이 실패한 날을 채우기 위해 어제부터 며칠을 다시 살핀다.
     * PT 운동일지는 구글 시트에 적었다가 며칠 뒤 sync_pt_sheet.py 로 들어오기도 해서 넉넉히 잡는다.
     * 입력 해시가 그대로인 날은 AI 를 다시 부르지 않으므로 기간을 늘려도 호출은 바뀐 날만큼만 는다.
     */
    static final int LOOKBACK_DAYS = 14;

    private final AiAnalysisRepository analysisRepo;
    private final AiAnalysisService aiAnalysisService;
    private final HealthDataQueryService healthData;
    private final ExerciseLogService exerciseLogService;
    private final ActivityCoreRepository activityRepo;
    private final UserBodyRecordRepository bodyRepo;
    private final ObjectMapper objectMapper;

    /** 분석 호출이 실패했을 때. 컨트롤러가 502 로 바꾼다. */
    public static class AnalysisFailedException extends RuntimeException {
        public AnalysisFailedException(String message) {
            super(message);
        }
    }

    /** 저장된 분석. 없으면 비어 있다. */
    public Optional<JsonNode> find(long userId, LocalDate date) {
        return findEntity(userId, date).map(a -> parse(a.getAnalysisData()));
    }

    /** 새벽 배치: 어제부터 {@link #LOOKBACK_DAYS}일을 살펴 입력이 바뀐 날만 분석한다. @return 새로 분석한 날 수 */
    public int runNightly(long userId, LocalDate today) {
        int analyzed = 0;
        for (int i = 1; i <= LOOKBACK_DAYS; i++) {
            LocalDate date = today.minusDays(i);
            try {
                if (analyzeIfChanged(userId, date)) analyzed++;
            } catch (Exception e) {
                // 한 날짜가 실패해도 나머지는 진행하고, 실패한 날은 다음 밤에 다시 시도된다
                System.out.println("❌ 일일 분석 실패: " + date + " - " + e.getMessage());
            }
        }
        return analyzed;
    }

    /** @return 실제로 분석했으면 true. 기록이 없거나 입력이 그대로면 false. */
    boolean analyzeIfChanged(long userId, LocalDate date) {
        Map<String, Object> input = collectInput(userId, date);
        if (isEmpty(input)) return false;

        String hash = hash(input);
        Optional<AiAnalysis> existing = findEntity(userId, date);
        if (existing.isPresent() && hash.equals(parse(existing.get().getAnalysisData()).path("inputHash").asText())) {
            return false;
        }
        save(userId, date, input, hash, existing);
        return true;
    }

    /** 관리자 재분석: 입력이 그대로여도 다시 호출한다. @return 저장된 분석, 그날 기록이 없으면 비어 있다. */
    public Optional<JsonNode> analyzeNow(long userId, LocalDate date) {
        Map<String, Object> input = collectInput(userId, date);
        if (isEmpty(input)) return Optional.empty();
        return Optional.of(parse(save(userId, date, input, hash(input), findEntity(userId, date))));
    }

    private String save(long userId, LocalDate date, Map<String, Object> input, String hash, Optional<AiAnalysis> existing) {
        String resultJson = aiAnalysisService.requestClaude(buildPrompt(date, toJson(input)));
        if (resultJson == null) {
            throw new AnalysisFailedException("AI 분석 호출에 실패했습니다: " + date);
        }

        Map<String, Object> stored = new LinkedHashMap<>();
        stored.put("date", date.toString());
        stored.put("analyzedAt", LocalDateTime.now(KST).withNano(0).toString());
        stored.put("inputHash", hash);
        stored.put("input", input);
        stored.put("result", parse(resultJson));
        String data = toJson(stored);

        AiAnalysis analysis = existing.orElseGet(() -> {
            AiAnalysis a = new AiAnalysis();
            a.setUserId(userId);
            a.setAnalysisType(ANALYSIS_TYPE);
            a.setTargetId(TARGET_ID);
            a.setTargetPeriod(date.toString());
            return a;
        });
        analysis.setAnalysisData(data);
        analysisRepo.save(analysis);
        System.out.println("✅ 일일 분석 저장: " + date);
        return data;
    }

    private Optional<AiAnalysis> findEntity(long userId, LocalDate date) {
        return analysisRepo.findByUserIdAndAnalysisTypeAndTargetIdAndTargetPeriod(
                userId, ANALYSIS_TYPE, TARGET_ID, date.toString());
    }

    // ── 입력 수집 ────────────────────────────────────

    Map<String, Object> collectInput(long userId, LocalDate date) {
        DateRange day = new DateRange(date, date);
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("dailyActivity", healthData.dailyActivity(userId, day));
        input.put("phoneExercises", healthData.exercises(userId, day));
        input.put("meals", healthData.allNutritionOfDay(userId, date));
        input.put("fitness", fitnessOf(userId, date));
        input.put("rides", ridesOf(userId, date));
        input.put("bodyRecords", bodyOf(userId, date));
        return input;
    }

    static boolean isEmpty(Map<String, Object> input) {
        return input.values().stream().allMatch(v -> ((List<?>) v).isEmpty());
    }

    private List<Map<String, Object>> fitnessOf(long userId, LocalDate date) {
        String month = date.toString().substring(0, 7);
        List<Map<String, Object>> result = new ArrayList<>();
        for (ExerciseHistoryResponse s : exerciseLogService.getHistory(userId, month)) {
            if (!date.toString().equals(s.getSessionDate())) continue;
            List<Map<String, Object>> logs = new ArrayList<>();
            for (ExerciseHistoryResponse.ExerciseLogDto log : s.getLogs()) {
                List<String> sets = new ArrayList<>();
                for (ExerciseHistoryResponse.ExerciseSetDto set : log.getSets()) {
                    sets.add(set.getWeight() + "kg x " + set.getReps());
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", log.getExerciseNameKo());
                m.put("category", log.getCategoryName());
                m.put("sets", sets);
                m.put("memo", log.getMemo());
                logs.add(m);
            }
            Map<String, Object> session = new LinkedHashMap<>();
            session.put("isPT", s.getIsPT());
            session.put("memo", s.getMemo());
            session.put("totalVolumeKg", s.getTotalVolume());
            session.put("totalSets", s.getTotalSets());
            session.put("estimatedMinutes", s.getEstimatedMinutes());
            session.put("estimatedKcal", s.getEstimatedCalories());
            session.put("exercises", logs);
            result.add(session);
        }
        return result;
    }

    private List<Map<String, Object>> ridesOf(long userId, LocalDate date) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ActivityCore a : activityRepo.findByUserIdAndDateRange(
                userId, date.atStartOfDay(), date.plusDays(1).atStartOfDay())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", a.getName());
            m.put("rideType", a.getRideType());
            m.put("startTime", a.getStartTime());
            m.put("distanceM", a.getTotalDistance());
            m.put("movingTimeSec", a.getMovingTime());
            m.put("ascentM", a.getTotalAscent());
            m.put("avgHeartRate", a.getAvgHeartRate());
            m.put("avgPowerW", a.getAvgPower());
            m.put("kcal", a.getCalories());
            result.add(m);
        }
        return result;
    }

    private List<Map<String, Object>> bodyOf(long userId, LocalDate date) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (UserBodyRecord r : bodyRepo.findAllByUserIdAndRecordDate(userId, date)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", r.getMeasurementType());
            m.put("weightKg", r.getWeight());
            m.put("bodyFatPercent", r.getBodyFatPercentage());
            m.put("bodyFatMassKg", r.getBodyFatMass());
            m.put("skeletalMuscleKg", r.getSkeletalMuscleMass());
            result.add(m);
        }
        return result;
    }

    // ── 프롬프트 ─────────────────────────────────────

    static String buildPrompt(LocalDate date, String inputJson) {
        return """
                당신은 라이더이자 웨이트 트레이닝을 병행하는 사람의 하루 기록을 분석하는 코치입니다.
                아래는 %s 하루치 기록입니다(JSON). 이 날의 기록만 근거로 분석하세요.
                다른 날과의 비교나 추세, 기록에 없는 수치를 추측해서 쓰지 마세요.

                데이터 해석 규칙:
                - dailyActivity: 폰에서 집계한 걸음 수, 거리, 칼로리. 값이 null 이면 측정하지 않은 것입니다. 수면과 안정시 심박은 재는 기기가 없어 보통 비어 있으니 언급하지 마세요.
                - phoneExercises: 폰에 기록된 운동 세션. kcal 은 기초대사량이 포함될 수 있어 부정확합니다. rides 와 시간이 겹치면 같은 운동이므로 rides 값을 우선하고 이중으로 세지 마세요.
                - rides: 자전거 컴퓨터로 기록한 라이딩. 가장 정확합니다.
                - fitness: 직접 입력한 웨이트 트레이닝.
                - meals: 식단 기록. 끼니가 일부만 입력됐을 수 있으니 합계를 하루 총 섭취량으로 단정하지 마세요.
                - bodyRecords: 이 날 측정한 체성분. 없으면 언급하지 마세요.
                - 이 사람은 랜도너스(200~1000km 장거리 자전거 대회)를 탑니다. 하루 10시간 이상, 1만kcal 이상 라이딩도 실제 대회 기록이니 이상치로 보지 말고 그대로 분석하세요.
                - 그 밖에 기기 오류로 보이는 값(같은 운동이 겹쳐 두 번 기록 등)만 dataNotes 에 적으세요.

                다음 JSON 형식으로만 답하세요. 설명이나 코드블록 없이 JSON 만 출력합니다.
                기록이 없는 항목의 값은 null 로 두세요. 모든 문장은 한국어로, 수치를 근거로 짧게 씁니다.
                {
                  "summary": "하루 총평 2~3문장",
                  "activity": "활동량과 유산소(라이딩 포함) 분석",
                  "strength": "웨이트 트레이닝 분석",
                  "nutrition": "식단 분석",
                  "body": "체성분 분석",
                  "goodPoints": ["잘한 점"],
                  "improvements": ["개선할 점"],
                  "dataNotes": ["데이터가 빠졌거나 이상해서 참고할 점"]
                }

                기록:
                %s
                """.formatted(date, inputJson);
    }

    // ── 유틸 ─────────────────────────────────────────

    private String hash(Map<String, Object> input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(toJson(input).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("일일 분석 JSON 변환 실패", e);
        }
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("저장된 일일 분석을 읽지 못했습니다", e);
        }
    }
}
