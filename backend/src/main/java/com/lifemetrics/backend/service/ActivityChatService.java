package com.lifemetrics.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifemetrics.backend.dto.ActivityChatRequest;
import com.lifemetrics.backend.entity.ActivityCore;
import com.lifemetrics.backend.entity.ActivityWeatherPoint;
import com.lifemetrics.backend.entity.AiAnalysis;
import com.lifemetrics.backend.repository.ActivityCoreRepository;
import com.lifemetrics.backend.repository.ActivityWeatherPointRepository;
import com.lifemetrics.backend.repository.AiAnalysisRepository;
import com.lifemetrics.backend.repository.UserBodyRecordRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ActivityChatService {
    private final ActivityCoreRepository activityRepo;
    private final ActivityWeatherPointRepository weatherPointRepo;
    private final AiAnalysisRepository analysisRepo;
    private final UserBodyRecordRepository bodyRecordRepo;
    private final ObjectMapper objectMapper;

    // ✅ Claude 대신 Vertex(Gemini) 호출 서비스 주입
    private final GeminiService geminiService;

    @PostConstruct
    public void logKeyStatus() {
        if (geminiService.hasApiKey()) {
            System.out.println("✅ [ActivityChatService] Gemini(Vertex) 준비됨");
        } else {
            System.out.println("⚠️ [ActivityChatService] Gemini(Vertex) 미설정 - ADC / vertex.project-id 확인 필요");
        }
    }

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MAX_HISTORY = 20;
    private static final int MAX_OUTPUT_TOKENS = 2048;

    public String chat(Long userId, Long activityId, List<ActivityChatRequest.Message> messages) {
        if (!geminiService.hasApiKey()) {
            return "AI 서비스가 설정되지 않았습니다. (ADC / Vertex 프로젝트 확인 필요)";
        }
        if (messages == null || messages.isEmpty()) {
            return "메시지가 비어 있습니다.";
        }

        ActivityCore activity = activityRepo.findById(activityId).orElseThrow(() -> new RuntimeException("Activity not found: " + activityId));

        List<ActivityWeatherPoint> weatherPoints = weatherPointRepo.findByActivityCoreIdOrderBySeq(activityId);

        String systemPrompt = buildSystemPrompt(activity, weatherPoints, userId);

        // 최근 MAX_HISTORY개만 사용해 GeminiService.ChatMessage 로 변환
        List<GeminiService.ChatMessage> history = new ArrayList<>();
        int start = Math.max(0, messages.size() - MAX_HISTORY);
        for (int i = start; i < messages.size(); i++) {
            ActivityChatRequest.Message m = messages.get(i);
            String content = m.getContent() == null ? "" : m.getContent();
            if (content.isBlank()) continue;
            // role 은 그대로 전달. assistant→model 매핑은 GeminiService 내부에서 처리.
            history.add(new GeminiService.ChatMessage(m.getRole(), content));
        }

        // Gemini는 contents가 user 턴으로 시작해야 한다. 앞에 붙은 assistant 턴은 제거.
        while (!history.isEmpty() && ("assistant".equalsIgnoreCase(history.get(0).role()) || "model".equalsIgnoreCase(history.get(0).role()))) {
            history.remove(0);
        }
        if (history.isEmpty()) {
            return "질문 메시지가 없습니다.";
        }

        String reply = geminiService.chat(systemPrompt, history, MAX_OUTPUT_TOKENS);
        if (reply == null || reply.isBlank()) {
            // 인증/모델/토큰 등 실제 원인은 GeminiService 로그(finishReason, 응답 본문)에서 확인.
            return "죄송합니다. 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요.";
        }
        return reply;
    }

    private String buildSystemPrompt(ActivityCore a, List<ActivityWeatherPoint> weatherPoints, Long userId) {
        StringBuilder sb = new StringBuilder();

        sb.append("""
                당신은 한국의 베테랑 사이클링 코치입니다. KOM 다수 보유, 1200km 브레베 완주 경험,
                UCI 코칭 자격증을 가지고 있습니다. 사용자가 자신의 라이딩 기록에 대해 묻는 질문에
                데이터를 근거로 친절하고 구체적으로 답변하세요.
                
                톤: 전문적이되 친근하게. "~네요", "~합니다" 정중체. 과장 금지.
                반드시 아래 라이딩 데이터의 수치를 인용하여 답변하세요.
                모호한 표현("적당히", "잘") 금지. 구체적인 숫자/존/시간 인용.
                JSON 출력 금지. 자연스러운 대화체로 답하세요.
                답변은 가능한 한 간결하게 (보통 2~5문장). 사용자가 더 자세히 묻기 전까지는 길게 늘이지 말 것.
                
                """);

        double weightKg = getLatestWeight(userId);
        boolean hasPower = Boolean.TRUE.equals(a.getHasPower());

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
        double durationHours = movingMin / 60.0;
        double estimatedFtp = weightKg * 3.0;
        double normalizedPower = a.getNormalizedPower() != null ? a.getNormalizedPower() : displayPower;
        double intensityFactor = estimatedFtp > 0 ? normalizedPower / estimatedFtp : 0;
        int tss = (int) Math.round(durationHours * intensityFactor * intensityFactor * 100);

        sb.append("## 라이딩 기록 데이터\n\n");
        if (a.getName() != null && !a.getName().isEmpty()) {
            sb.append("- 제목: ").append(a.getName()).append("\n");
        }
        if (a.getStartTime() != null) {
            sb.append("- 시작 시각: ").append(a.getStartTime().toString()).append("\n");
        }
        if (a.getLocationName() != null && !a.getLocationName().isEmpty()) {
            sb.append("- 출발지: ").append(a.getLocationName()).append("\n");
        }
        if (a.getGearName() != null && !a.getGearName().isEmpty()) {
            sb.append("- 장비: ").append(a.getGearName()).append("\n");
        }

        sb.append(String.format("""
                - 거리: %.1f km
                - 이동 시간: %d시간 %d분
                - 평균 속도: %.1f km/h / 최고 %.1f km/h
                - 획득 고도: %.0f m
                - 평균 심박: %.0f bpm / 최고 %.0f bpm
                - 평균 케이던스: %.0f rpm
                - 평균 파워 %s: %.0f W (NP %.0f W, %.2f W/kg)
                - 추정 FTP: %.0f W (체중 %.1f kg × 3.0)
                - Intensity Factor: %.2f / TSS: %d
                - 칼로리: %d kcal
                - 상대적 노력(RE): %s
                """, distanceKm, movingMin / 60, movingMin % 60, safeDouble(a.getAvgSpeed()), safeDouble(a.getMaxSpeed()), safeDouble(a.getTotalAscent()), safeDouble(a.getAvgHeartRate()), safeDouble(a.getMaxHeartRate()), safeDouble(a.getAvgCadence()), powerLabel, displayPower, normalizedPower, weightKg > 0 ? displayPower / weightKg : 0, estimatedFtp, weightKg, intensityFactor, tss, a.getCalories(), a.getRelativeEffort() != null ? a.getRelativeEffort().toString() : "-"));

        if (!weatherPoints.isEmpty()) {
            sb.append("\n## 구간별 날씨\n");
            double avgTemp = 0, avgWind = 0;
            int countWp = 0, countWind = 0;
            for (ActivityWeatherPoint wp : weatherPoints) {
                if (wp.getTemperature() != null) {
                    avgTemp += wp.getTemperature();
                    countWp++;
                }
                if (wp.getWindSpeed() != null) {
                    avgWind += wp.getWindSpeed();
                    countWind++;
                }
            }
            if (countWp > 0) {
                sb.append(String.format("- 평균 기온 %.1f°C\n", avgTemp / countWp));
            }
            if (countWind > 0) {
                sb.append(String.format("- 평균 풍속 %.1f m/s\n", avgWind / countWind));
            }
            ActivityWeatherPoint first = weatherPoints.get(0);
            if (first.getWeatherDesc() != null) {
                sb.append("- 날씨: ").append(first.getWeatherDesc()).append("\n");
            }
        }

        Optional<AiAnalysis> priorAnalysis = analysisRepo.findByUserIdAndAnalysisTypeAndTargetId(userId, "activity", a.getId());
        if (priorAnalysis.isPresent()) {
            try {
                JsonNode json = objectMapper.readTree(priorAnalysis.get().getAnalysisData());
                sb.append("\n## 기존 AI 분석 결과\n");
                appendIfPresent(sb, "- 한줄 평", json.path("summary"));
                appendIfPresent(sb, "- 강도", json.path("intensity"));
                appendIfPresent(sb, "- 페이싱", json.path("pacingGrade"));
                appendIfPresent(sb, "- 페이싱 분석", json.path("pacingAnalysis"));
                appendIfPresent(sb, "- 생리학적 분석", json.path("physiologyAnalysis"));
                appendIfPresent(sb, "- 케이던스 분석", json.path("cadenceAnalysis"));
                appendIfPresent(sb, "- 날씨 영향", json.path("weatherImpact"));
                appendIfPresent(sb, "- 회복 권장", json.path("recoveryAdvice"));
                appendIfPresent(sb, "- 다음 라이딩 팁", json.path("nextRideTip"));
            } catch (Exception ignore) {
            }
        }

        return sb.toString();
    }

    private void appendIfPresent(StringBuilder sb, String label, JsonNode node) {
        if (node == null || node.isMissingNode()) return;
        String value = node.asText("");
        if (value.isEmpty()) return;
        sb.append(label).append(": ").append(value).append("\n");
    }

    private double safeDouble(Double value) {
        return value != null ? value : 0.0;
    }

    private int safeInt(Integer value) {
        return value != null ? value : 0;
    }

    private double getLatestWeight(Long userId) {
        return bodyRecordRepo.findByUserIdOrderByRecordDate(userId).stream().filter(r -> r.getWeight() != null).reduce((first, second) -> second).map(r -> r.getWeight()).orElse(75.0);
    }
}
