package com.lifemetrics.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifemetrics.backend.dto.*;
import com.lifemetrics.backend.entity.AiAnalysis;
import com.lifemetrics.backend.entity.ActivityWeatherPoint;
import com.lifemetrics.backend.entity.DeviceInfo;
import com.lifemetrics.backend.repository.*;
import com.lifemetrics.backend.entity.ActivityCore;
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
    private final DeviceInfoRepository deviceInfoRepo;
    private final ObjectMapper objectMapper;
    private final UserBodyRecordRepository bodyRecordRepo;

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

        List<ActivityWeatherPoint> weatherPoints = weatherPointRepo
                .findByActivityCoreIdOrderBySeq(activityId);

        String prompt = buildActivityPrompt(activity, weatherPoints, userId);
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

                String prompt = buildActivityPrompt(activity, weatherPoints, userId);
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

    public ActivityAnalysisResponse reAnalyzeActivity(Long userId, Long activityId) {
        analysisRepo.findByUserIdAndAnalysisTypeAndTargetId(userId, "activity", activityId)
                .ifPresent(analysisRepo::delete);
        return analyzeActivity(userId, activityId);
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
    // 브레베 분석
    // ================================================================

    public BrevetAnalysisResponse analyzeBrevetPlan(BrevetAnalysisRequest req) {
        Optional<AiAnalysis> existing = analysisRepo
                .findByUserIdAndAnalysisTypeAndTargetIdAndTargetPeriod(
                        req.getUserId(), "brevet_plan", 0L, req.getEventDate());

        if (existing.isPresent()) {
            return toBrevetResponse(existing.get(), req.getEventName(), req.getEventDate());
        }

        String prompt = buildBrevetPrompt(req);
        String analysisJson = callClaudeApi(prompt);

        AiAnalysis analysis = new AiAnalysis();
        analysis.setUserId(req.getUserId());
        analysis.setAnalysisType("brevet_plan");
        analysis.setTargetId(0L);
        analysis.setTargetPeriod(req.getEventDate());
        analysis.setAnalysisData(analysisJson);
        analysisRepo.save(analysis);

        return toBrevetResponse(analysis, req.getEventName(), req.getEventDate());
    }

    // ================================================================
    // 프롬프트 빌더 — 라이딩 (★ 강화 버전)
    // ================================================================

    private String buildActivityPrompt(ActivityCore a, List<ActivityWeatherPoint> weatherPoints, Long userId) {
        StringBuilder sb = new StringBuilder();

        // ── 시스템 페르소나 ──
        sb.append("""
                ## 당신의 역할
                
                당신은 한국의 베테랑 사이클링 코치입니다. KOM 다수 보유, 1200km 브레베 완주 경험,
                UCI 코칭 자격증을 가지고 있습니다. 데이터 분석을 통해 라이더의 페이싱, 강도 관리,
                훈련 스트레스를 평가하고 구체적인 코칭을 제공합니다.
                
                톤: 전문적이되 친근하게. "~네요", "~합니다" 정중체. 과장 금지, 구체적 수치 인용.
                절대 모호한 칭찬("좋았어요", "잘했어요") 금지. 반드시 데이터 근거 제시.
                
                ---
                
                """);

        // ── 라이드 메타 데이터 ──
        boolean hasPower = Boolean.TRUE.equals(a.getHasPower());
        double weightKg = getLatestWeight(userId);

        double displayPower;
        String powerLabel;
        if (hasPower && safeDouble(a.getAvgPower()) > 0) {
            displayPower = safeDouble(a.getAvgPower());
            powerLabel = "(파워미터 실측)";
        } else {
            double speedKmh = safeDouble(a.getAvgSpeed());
            double ascentM = safeDouble(a.getTotalAscent());
            int movingTimeSec = safeInt(a.getMovingTime());
            double flatPower = speedKmh * weightKg * 0.04;
            double climbPower = movingTimeSec > 0 ? (weightKg * 9.8 * ascentM / movingTimeSec) : 0;
            displayPower = Math.round(flatPower + climbPower);
            powerLabel = "(가상 추정)";
        }

        double distanceKm = safeDouble(a.getTotalDistance()) / 1000.0;
        int movingMin = safeInt(a.getMovingTime()) / 60;
        double avgHr = safeDouble(a.getAvgHeartRate());
        double maxHr = safeDouble(a.getMaxHeartRate());
        double avgCadence = safeDouble(a.getAvgCadence());
        double ascent = safeDouble(a.getTotalAscent());
        double avgSpeed = safeDouble(a.getAvgSpeed());
        double maxSpeed = safeDouble(a.getMaxSpeed());

        // 추정 FTP 기반 IF/TSS 계산 (체중 × 3.0 W/kg 가정)
        double estimatedFtp = weightKg * 3.0;
        double normalizedPower = a.getNormalizedPower() != null
                ? a.getNormalizedPower() : displayPower;
        double intensityFactor = estimatedFtp > 0 ? normalizedPower / estimatedFtp : 0;
        double durationHours = movingMin / 60.0;
        int tss = (int) Math.round(durationHours * intensityFactor * intensityFactor * 100);

        // 심박존 추정 (HRR 기반, 안정시 60 / 최대 190 가정)
        int restingHr = 60;
        int maxHrEstimate = 190;
        double hrr = (avgHr - restingHr) / (double) (maxHrEstimate - restingHr);
        String hrZone;
        if (hrr < 0.6) hrZone = "Z2 (지구력)";
        else if (hrr < 0.7) hrZone = "Z3 (템포)";
        else if (hrr < 0.8) hrZone = "Z4 (역치)";
        else if (hrr < 0.9) hrZone = "Z5 (VO2max)";
        else hrZone = "Z6 (무산소)";

        // 케이던스 평가
        String cadenceComment;
        if (avgCadence < 60) cadenceComment = "낮음 (60 미만, 무릎 부담)";
        else if (avgCadence < 80) cadenceComment = "다소 낮음 (60~80)";
        else if (avgCadence <= 95) cadenceComment = "이상적 (80~95)";
        else cadenceComment = "높음 (95 초과)";

        // 등반 강도 (m/km)
        double climbPerKm = distanceKm > 0 ? ascent / distanceKm : 0;
        String climbCategory;
        if (climbPerKm < 5) climbCategory = "평지 위주";
        else if (climbPerKm < 12) climbCategory = "중간 기복";
        else if (climbPerKm < 20) climbCategory = "산악 라이딩";
        else climbCategory = "고난이도 산악";

        sb.append(String.format("""
                ## 라이딩 데이터
                
                [기본 메트릭]
                - 거리: %.1f km
                - 이동 시간: %d시간 %d분 (%.2f h)
                - 평균 속도: %.1f km/h / 최고 %.1f km/h
                - 획득 고도: %.0f m (%.1f m/km, %s)
                
                [심박]
                - 평균: %.0f bpm / 최고 %.0f bpm
                - 추정 심박존: %s (HRR %.0f%%)
                
                [파워] %s
                - 평균 파워: %.0f W (%.2f W/kg)
                - Normalized Power: %.0f W
                - 추정 FTP: %.0f W (체중 %.1fkg × 3.0)
                - Intensity Factor (IF): %.2f
                - Training Stress Score (TSS): %d
                
                [케이던스]
                - 평균: %.0f rpm — %s
                
                [에너지]
                - 칼로리: %d kcal
                
                """,
                distanceKm,
                movingMin / 60, movingMin % 60, durationHours,
                avgSpeed, maxSpeed,
                ascent, climbPerKm, climbCategory,
                avgHr, maxHr,
                hrZone, hrr * 100,
                powerLabel,
                displayPower, weightKg > 0 ? displayPower / weightKg : 0,
                normalizedPower,
                estimatedFtp, weightKg,
                intensityFactor,
                tss,
                avgCadence, cadenceComment,
                safeInt(a.getCalories())
        ));

        // 기기 정보
        sb.append(buildDeviceContext(userId, a.getStartTime()));

        // 구간별 날씨
        if (!weatherPoints.isEmpty()) {
            sb.append("[구간별 날씨 (30분 간격)]\n");
            double avgTemp = 0, avgWind = 0;
            int countWp = 0;
            for (ActivityWeatherPoint wp : weatherPoints) {
                String time = wp.getPointTime() != null ? wp.getPointTime().format(TIME_FMT) : "?";
                String desc = wp.getWeatherDesc() != null ? wp.getWeatherDesc() : "";
                String windDir = windDegToDirection(wp.getWindDeg());
                sb.append(String.format("  %s - %.1f°C, 습도%d%%, %s %.1fm/s, %s\n",
                        time,
                        wp.getTemperature() != null ? wp.getTemperature() : 0,
                        wp.getHumidity() != null ? wp.getHumidity().intValue() : 0,
                        windDir,
                        wp.getWindSpeed() != null ? wp.getWindSpeed() : 0,
                        desc
                ));
                if (wp.getTemperature() != null) {
                    avgTemp += wp.getTemperature();
                    countWp++;
                }
                if (wp.getWindSpeed() != null) avgWind += wp.getWindSpeed();
            }
            if (countWp > 0) {
                sb.append(String.format("  → 평균 기온 %.1f°C, 평균 풍속 %.1f m/s\n",
                        avgTemp / countWp, avgWind / countWp));
            }
            sb.append("\n");
        }

        // ── 분석 가이드 ──
        sb.append("""
                ## 분석 가이드
                
                아래 항목을 정량적으로 평가하세요. 모든 항목은 위 데이터 수치를 직접 인용하여 근거 제시.
                
                1. **페이싱 (pacingAnalysis)**: IF/TSS와 거리/시간을 종합하여 페이스 적정성 평가.
                   - IF 0.5~0.65: 회복/지구력 라이딩
                   - IF 0.65~0.8: 템포/유산소
                   - IF 0.8~0.95: 역치 (LT)
                   - IF 0.95~1.05: VO2max
                   - IF 1.05+: 무산소 (단시간만 지속 가능)
                   장거리(100km+)에서 IF가 0.75 이상이면 과도한 페이스로 판단.
                
                2. **생리학적 부담 (physiologyAnalysis)**: 심박과 파워 관계를 분석.
                   심박 대비 파워가 낮으면 누적 피로/탈수, 심박 대비 파워가 높으면 효율적 컨디션.
                
                3. **케이던스 (cadenceAnalysis)**: 이상 범위(80~95) 대비 평가.
                   낮으면 근피로 누적/무릎 부담, 너무 높으면 비효율적 페달링 가능성.
                
                4. **날씨 영향 (weatherImpact)**: 평균 풍속 4m/s 이상이면 영향 명시.
                   기온 25°C 초과 또는 5°C 미만 시 체온 관리 코멘트.
                
                5. **회복 처방 (recoveryAdvice)**: TSS 기준
                   - TSS < 150: 다음 날 정상 훈련 가능
                   - TSS 150~300: 24시간 회복 또는 가벼운 라이딩
                   - TSS 300~450: 48시간 회복 권장
                   - TSS 450+: 3일 이상 회복, 영양/수면 집중
                
                6. **점수 (score)**: 페이싱 + 강도 적정성 + 데이터 완전성 종합 0~100.
                
                ---
                
                ## 출력 형식
                
                반드시 아래 JSON만 출력. 다른 텍스트 절대 금지. 마크다운 코드블록 금지.
                
                {
                  "summary": "한줄 진단 (40자 이내, 구체적 수치 포함 권장)",
                  "intensity": "낮음/보통/높음/매우높음",
                  "score": 85,
                  "pacingGrade": "양호/불균형/후반약화/전반과속 중 하나",
                  "intensityFactor": 0.72,
                  "trainingStress": 245,
                  "pacingAnalysis": "페이싱 평가 (60자 이내, 데이터 근거)",
                  "physiologyAnalysis": "심박/파워 관계 분석 (60자 이내)",
                  "cadenceAnalysis": "케이던스 평가 (60자 이내)",
                  "weatherImpact": "날씨 영향 (60자 이내, 영향 없으면 빈 문자열)",
                  "highlights": ["잘한 점 1 (수치 인용)", "잘한 점 2", "잘한 점 3"],
                  "suggestions": ["개선점 1 (구체적)", "개선점 2"],
                  "recoveryAdvice": "회복 권장 (40자 이내, 시간 명시)",
                  "nextRideTip": "다음 라이딩 처방 (80자 이내, 강도/거리/케이던스 등 구체적)"
                }
                
                주의:
                - 파워가 가상 추정값일 경우 IF/TSS 신뢰도가 낮음을 highlight나 suggestion에 한 번 명시
                - 심박계 없으면 심박 기반 분석 생략하고 속도/고도 기반 코멘트
                - 모호한 표현("적당히", "잘") 금지. 반드시 수치/존/시간 인용.
                """);

        return sb.toString();
    }

    // 기기 컨텍스트
    private String buildDeviceContext(Long userId, java.time.LocalDateTime activityTime) {
        List<DeviceInfo> devices = deviceInfoRepo.findByOwnerUserIdAndIsActiveTrue(userId)
                .stream()
                .filter(d -> d.getFirstSeenAt() == null ||
                        !d.getFirstSeenAt().toLocalDate().isAfter(
                                activityTime.toLocalDate()))
                .toList();

        if (devices.isEmpty()) return "";

        StringBuilder sb = new StringBuilder("[보유 기기]\n");

        boolean hasHr = false;
        boolean hasPower = false;
        boolean hasSpeed = false;
        boolean hasCadence = false;

        for (DeviceInfo d : devices) {
            String type = d.getDeviceType() != null ? d.getDeviceType() : "";
            String label = d.getUserLabel() != null ? d.getUserLabel()
                    : d.getManufacturer() + " " + d.getModel();
            sb.append(String.format("  - %s (%s)\n", label, deviceTypeKo(type)));

            switch (type) {
                case "HEART_RATE"     -> hasHr = true;
                case "POWER_METER"    -> hasPower = true;
                case "SPEED_SENSOR"   -> hasSpeed = true;
                case "CADENCE_SENSOR" -> hasCadence = true;
            }
        }

        sb.append("[센서 보유 현황]\n");
        sb.append(String.format("  - 심박계: %s\n", hasHr ? "있음 (실측값)" : "없음 → 심박 미수집"));
        sb.append(String.format("  - 파워미터: %s\n", hasPower ? "있음 (실측값)" : "없음 → 가상 파워 추정값"));
        sb.append(String.format("  - 속도 센서: %s\n", hasSpeed ? "있음" : "없음 (GPS 기반)"));
        sb.append(String.format("  - 케이던스 센서: %s\n\n", hasCadence ? "있음" : "없음"));

        return sb.toString();
    }

    private String deviceTypeKo(String type) {
        return switch (type) {
            case "HEAD_UNIT"      -> "헤드유닛";
            case "HEART_RATE"     -> "심박계";
            case "POWER_METER"    -> "파워미터";
            case "SPEED_SENSOR"   -> "속도 센서";
            case "CADENCE_SENSOR" -> "케이던스 센서";
            default               -> type;
        };
    }

    // ================================================================
    // 프롬프트 빌더 — 운동 (기존 유지)
    // ================================================================

    private String buildExerciseSessionPrompt(ExerciseHistoryResponse session) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("다음은 %s 운동 세션 데이터입니다.\n\n", session.getSessionDate()));

        if (session.getIsPT()) sb.append("⚡ PT 세션\n\n");

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

            if (log.getMemo() != null && !log.getMemo().isEmpty())
                sb.append("  메모: ").append(log.getMemo()).append("\n");
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
                  "suggestions": ["개선점1", "개선점2"],
                  "score": 85
                }
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
                        if ("PRIMARY".equals(mm.getRole()))
                            muscleSetCount.merge(mm.getMuscleName(), sets, Integer::sum);
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

        sb.append("""
                
                위 월간 운동 기록을 종합 분석해주세요. 반드시 아래 JSON 형식으로만 응답하세요:
                {
                  "summary": "이번 달 운동 한줄평 (40자 이내)",
                  "targetMuscles": "가장 많이 운동한 부위",
                  "volumeLevel": "낮음/적정/높음",
                  "intensityLevel": "낮음/보통/높음/매우높음",
                  "highlights": ["잘한 점 1", "잘한 점 2", "잘한 점 3"],
                  "suggestions": ["개선점1", "다음 달 추천2", "추천3"],
                  "score": 80
                }
                """);

        return sb.toString();
    }

    // ================================================================
    // 프롬프트 빌더 — 브레베 (기존 유지)
    // ================================================================

    private String buildBrevetPrompt(BrevetAnalysisRequest req) {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("""
                브레베(장거리 사이클링 비경쟁 이벤트) 계획을 분석해주세요.
                
                [대회 정보]
                - 대회명: %s
                - 날짜: %s
                - 출발 시간: %s
                - 제한 시간: %d시간
                - 목표 평균 속도: %.1f km/h
                - 총 거리: %.1f km
                - 총 획득 고도: %d m
                
                """,
                req.getEventName(), req.getEventDate(), req.getStartTime(),
                req.getTimeLimit(), req.getTargetSpeed(),
                req.getTotalDistance(), req.getTotalAscent()
        ));

        sb.append("[CP별 날씨 및 도착 계획]\n");
        for (BrevetAnalysisRequest.CpWeatherDto cp : req.getCps()) {
            sb.append(String.format("""
                    ▶ %s (%dkm 지점, 누적고도 %dm)
                      목표 도착: %s / 마감: %s
                      날씨: %s, 기온 %.1f°C, 풍속 %.1f km/h, 강수확률 %d%%
                      풍향 영향: %s
                    """,
                    cp.getName(), cp.getDistance(), cp.getElevation(),
                    cp.getTargetArrival(), cp.getDeadline(),
                    cp.getWeatherDesc(), cp.getTemp(), cp.getWind(), cp.getPrecip(),
                    cp.getWindEffect() != null ? cp.getWindEffect() : "정보없음"
            ));
        }

        sb.append("""
                
                반드시 아래 JSON 형식으로만 응답하세요:
                {
                  "summary": "전체 컨디션 한줄 요약 (40자 이내)",
                  "overallCondition": "좋음/보통/어려움 중 하나",
                  "paceStrategy": "페이스 전략 (60자 이내)",
                  "warnings": ["주의사항1", "주의사항2", "주의사항3"],
                  "tips": ["구간별 팁1 (CP명 포함)", "팁2", "팁3"],
                  "conclusion": "종합 조언 및 완주 전략 (100자 이내)",
                  "score": 82
                }
                score는 현재 계획대로 완주할 가능성 (0~100)
                """);

        return sb.toString();
    }

    // ================================================================
    // Claude API 호출 (★ max_tokens 2000으로 증가)
    // ================================================================
    private String callClaudeApi(String prompt) {
        if (anthropicApiKey == null || anthropicApiKey.isEmpty()) {
            return "{\"summary\":\"API 키 없음\",\"highlights\":[],\"suggestions\":[],\"score\":0}";
        }

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", anthropicApiKey);
        headers.set("anthropic-version", "2023-06-01");

        Map<String, Object> body = Map.of(
                "model", "claude-sonnet-5",
                "max_tokens", 2000,
                "messages", List.of(Map.of("role", "user", "content", prompt))
        );

        try {
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity("https://api.anthropic.com/v1/messages", request, String.class);

            JsonNode responseJson = objectMapper.readTree(response.getBody());

            // content 배열에서 type=="text" 블록을 찾는다 (thinking 블록이 앞에 올 수 있음)
            String text = null;
            JsonNode contentArr = responseJson.get("content");
            if (contentArr != null && contentArr.isArray()) {
                for (JsonNode block : contentArr) {
                    if ("text".equals(block.path("type").asText())) {
                        text = block.path("text").asText();
                        break;
                    }
                }
            }
            if (text == null) {
                throw new RuntimeException("응답에서 text 블록을 찾지 못함: " + response.getBody());
            }

            if (text.contains("```"))
                text = text.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();

            objectMapper.readTree(text);
            return text;

        } catch (Exception e) {
            System.out.println("❌ Claude API 에러: " + e.getMessage());
            return "{\"summary\":\"분석 실패\",\"highlights\":[],\"suggestions\":[],\"score\":0}";
        }
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
    // 헬퍼
    // ================================================================

    private ExerciseHistoryResponse findSessionById(Long userId, Long sessionId) {
        for (int i = 0; i < 12; i++) {
            java.time.LocalDate date = java.time.LocalDate.now().minusMonths(i);
            String month = String.format("%d-%02d", date.getYear(), date.getMonthValue());
            for (ExerciseHistoryResponse session : exerciseLogService.getHistory(userId, month)) {
                if (session.getId().equals(sessionId)) return session;
            }
        }
        return null;
    }

    // ================================================================
    // Entity → DTO 변환
    // ================================================================

    private ActivityAnalysisResponse toActivityResponse(AiAnalysis analysis) {
        try {
            JsonNode json = objectMapper.readTree(analysis.getAnalysisData());
            return ActivityAnalysisResponse.builder()
                    .id(analysis.getId())
                    .activityId(analysis.getTargetId())
                    .model("claude-sonnet-5")
                    .createdAt(analysis.getCreatedAt().toString())

                    // 핵심
                    .summary(json.path("summary").asText())
                    .intensity(json.path("intensity").asText())
                    .score(json.path("score").asInt())

                    // 정량 분석
                    .pacingGrade(json.path("pacingGrade").asText(""))
                    .intensityFactor(json.path("intensityFactor").isMissingNode()
                            ? null : json.path("intensityFactor").asDouble())
                    .trainingStress(json.path("trainingStress").isMissingNode()
                            ? null : json.path("trainingStress").asInt())

                    // 코칭 인사이트
                    .pacingAnalysis(json.path("pacingAnalysis").asText(""))
                    .physiologyAnalysis(json.path("physiologyAnalysis").asText(""))
                    .cadenceAnalysis(json.path("cadenceAnalysis").asText(""))
                    .weatherImpact(json.path("weatherImpact").asText(""))

                    // 기존
                    .highlights(jsonArrayToList(json.path("highlights")))
                    .suggestions(jsonArrayToList(json.path("suggestions")))

                    // 처방
                    .recoveryAdvice(json.path("recoveryAdvice").asText(""))
                    .nextRideTip(json.path("nextRideTip").asText(""))

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
                    .model("claude-sonnet-5")
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
                    .id(analysis.getId()).sessionId(analysis.getTargetId())
                    .summary("파싱 실패").score(0).build();
        }
    }

    private BrevetAnalysisResponse toBrevetResponse(AiAnalysis analysis, String eventName, String eventDate) {
        try {
            JsonNode json = objectMapper.readTree(analysis.getAnalysisData());
            return BrevetAnalysisResponse.builder()
                    .id(analysis.getId())
                    .eventName(eventName)
                    .eventDate(eventDate)
                    .createdAt(analysis.getCreatedAt().toString())
                    .summary(json.path("summary").asText())
                    .overallCondition(json.path("overallCondition").asText())
                    .paceStrategy(json.path("paceStrategy").asText())
                    .warnings(jsonArrayToList(json.path("warnings")))
                    .tips(jsonArrayToList(json.path("tips")))
                    .conclusion(json.path("conclusion").asText())
                    .score(json.path("score").asInt())
                    .build();
        } catch (JsonProcessingException e) {
            return BrevetAnalysisResponse.builder()
                    .id(analysis.getId()).eventName(eventName).eventDate(eventDate)
                    .summary("파싱 실패").score(0).build();
        }
    }

    private double safeDouble(Double value) { return value != null ? value : 0.0; }
    private int safeInt(Integer value) { return value != null ? value : 0; }

    private List<String> jsonArrayToList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node != null && node.isArray())
            for (JsonNode item : node) list.add(item.asText());
        return list;
    }

    private double getLatestWeight(Long userId) {
        return bodyRecordRepo
                .findByUserIdOrderByRecordDate(userId)
                .stream()
                .filter(r -> r.getWeight() != null)
                .reduce((first, second) -> second)
                .map(r -> r.getWeight())
                .orElse(75.0);
    }
}
