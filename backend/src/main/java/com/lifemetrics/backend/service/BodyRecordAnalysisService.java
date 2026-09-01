package com.lifemetrics.backend.service;

import com.lifemetrics.backend.entity.UserBodyRecord;
import com.lifemetrics.backend.repository.UserBodyRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class BodyRecordAnalysisService {

    private static final String SYSTEM_PROMPT = """
            당신은 체성분 데이터를 해석해주는 피트니스 코치다. 아래 측정값을 근거로 짧게 평가한다.

            지켜야 할 것:
            - 주어진 숫자만 근거로 삼는다. 없는 데이터를 지어내지 않는다.
            - 의학적 진단이 아니라 참고 의견임을 전제로 한다.
            - 한국어로 작성한다.
            - 다른 텍스트나 코드블록 없이, 아래 스키마 그대로 순수 JSON 객체 하나만 출력한다.
              직전 기록이 없어 trend 를 쓸 수 없으면 그 필드는 생략한다.

            {
              "summary": "핵심 요약 한 문장",
              "current_evaluation": {
                "muscle": "근육 관련 평가",
                "fat": "체지방 관련 평가",
                "overall": "종합 평가"
              },
              "trend": "직전 측정 대비 변화 서술",
              "recommendations": ["실행 가능한 권고 1", "권고 2"]
            }
            """;

    private final UserBodyRecordRepository bodyRecordRepository;
    private final ClaudeClient claudeClient;

    @Transactional
    public String analyze(Long userId, Long recordId) {
        UserBodyRecord record = bodyRecordRepository.findById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("신체 기록을 찾을 수 없습니다."));

        if (!record.getUserId().equals(userId)) {
            throw new IllegalArgumentException("본인 기록이 아닙니다.");
        }

        // 수정된 리포지토리 메서드가 가장 최근 1건만 반환하므로 이제 예외가 발생하지 않습니다.
        UserBodyRecord previous = bodyRecordRepository
                .findPrevious(userId, record.getRecordDate(), record.getMeasurementType())
                .orElse(null);

        String facts = buildFacts(record, previous);
        String result = claudeClient.complete(SYSTEM_PROMPT, facts, 1200);
        if (result == null) return null;

        String cleaned = stripCodeFence(result);
        record.setRawLlmJson(cleaned);
        bodyRecordRepository.save(record);
        return cleaned;
    }

    private String buildFacts(UserBodyRecord curr, UserBodyRecord prev) {
        StringBuilder sb = new StringBuilder();
        sb.append("측정일: ").append(curr.getRecordDate())
                .append(" (").append(curr.getMeasurementType()).append(")\n");

        appendLine(sb, "체중", curr.getWeight(), "kg");
        appendLine(sb, "BMI", curr.getBmi(), null);
        appendLine(sb, "체지방률", curr.getBodyFatPercentage(), "%");
        appendLine(sb, "체지방량", curr.getBodyFatMass(), "kg");
        appendLine(sb, "제지방량", curr.getFatFreeMass(), "kg");
        appendLine(sb, "골격근량", curr.getSkeletalMuscleMass(), "kg");
        appendLine(sb, "내장지방레벨", curr.getVisceralFatLevel() != null ? curr.getVisceralFatLevel().doubleValue() : null, null);
        appendLine(sb, "기초대사량", curr.getBasalMetabolicRate(), "kcal");
        appendLine(sb, "체수분", curr.getBodyWater(), "kg");
        appendLine(sb, "단백질", curr.getProteinMass(), "kg");
        appendLine(sb, "무기질", curr.getMineral(), "kg");

        if (prev != null) {
            long daysBetween = ChronoUnit.DAYS.between(prev.getRecordDate(), curr.getRecordDate());
            sb.append("\n직전 측정: ").append(prev.getRecordDate())
                    .append(" (").append(daysBetween).append("일 전)\n");
            appendDelta(sb, "체중", curr.getWeight(), prev.getWeight(), "kg");
            appendDelta(sb, "체지방률", curr.getBodyFatPercentage(), prev.getBodyFatPercentage(), "%");
            appendDelta(sb, "체지방량", curr.getBodyFatMass(), prev.getBodyFatMass(), "kg");
            appendDelta(sb, "골격근량", curr.getSkeletalMuscleMass(), prev.getSkeletalMuscleMass(), "kg");
        }
        return sb.toString();
    }

    private void appendLine(StringBuilder sb, String label, Double value, String unit) {
        if (value == null) return;
        sb.append(label).append(": ").append(value);
        if (unit != null) sb.append(unit);
        sb.append("\n");
    }

    private void appendDelta(StringBuilder sb, String label, Double curr, Double prev, String unit) {
        if (curr == null || prev == null) return;
        double delta = Math.round((curr - prev) * 100) / 100.0;
        sb.append(label).append(" 변화: ").append(delta >= 0 ? "+" : "").append(delta).append(unit).append("\n");
    }

    private String stripCodeFence(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```[a-zA-Z]*\\n", "");
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }
        return trimmed.trim();
    }
}