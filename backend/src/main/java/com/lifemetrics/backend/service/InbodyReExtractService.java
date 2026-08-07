package com.lifemetrics.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifemetrics.backend.entity.MeasurementType;
import com.lifemetrics.backend.entity.UserBodyRecord;
import com.lifemetrics.backend.repository.UserBodyRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NAS 에 보관된 인바디 기록지 이미지를 다시 읽어 미추출 항목을 채운다.
 *
 * 기존 파서(외부 8001 서비스)는 체성분 기본값만 뽑아 저장했고, 인바디점수·적정체중·
 * 권장섭취열량 같은 값은 버려졌다. 내장지방레벨은 게이지 눈금을 값으로 잘못 읽은
 * 사례가 확인됐다(기록지 7 → DB 10). 이 배치는 그 두 문제를 함께 해결한다.
 *
 * 기본은 dry-run 이다. 값을 덮어쓰기 전에 항상 차이를 눈으로 확인해야 한다.
 * 읽어온 값이 이상해도 조용히 반영되면 원본과 어긋난 채로 남기 때문이다.
 */
@Service
@RequiredArgsConstructor
public class InbodyReExtractService {

    private static final String SYSTEM_PROMPT = """
            당신은 인바디(InBody) 결과지 이미지에서 수치를 그대로 옮겨 적는 OCR 도구다.

            반드시 지킬 것:
            - 이미지에 인쇄된 값만 읽는다. 계산하거나 추정하지 않는다.
            - 그래프의 눈금 라벨(예: 게이지 위의 10, 100, 표준 범위 표기)을 값으로 착각하지 않는다.
              측정값은 굵게 인쇄된 숫자이거나 항목명 옆에 단독으로 오는 숫자다.
            - 괄호 안의 정상 범위(예: "1720 kcal ( 1710~2008 )")에서 범위가 아닌 측정값만 취한다.
            - 이미지에 없는 항목은 null 로 둔다. 절대 지어내지 않는다.
            - JSON 객체 하나만 출력한다. 설명 문장이나 코드펜스를 붙이지 않는다.

            출력 스키마:
            {
              "record_date": "YYYY-MM-DD",
              "weight": number, "skeletal_muscle_mass": number, "body_fat_mass": number,
              "fat_free_mass": number, "body_fat_percentage": number, "bmi": number,
              "visceral_fat_level": integer, "total_body_water": number,
              "protein": number, "mineral": number,
              "basal_metabolic_rate": number, "inbody_score": integer,
              "target_weight": number, "weight_control": number,
              "fat_control": number, "muscle_control": number,
              "obesity_degree": number, "waist_hip_ratio": number,
              "recommended_intake_kcal": integer,
              "segmental": {"lean": {...}, "fat": {...}}
            }

            segmental 은 부위별 근육/체지방 분석의 부위명을 키로, 등급 문자열이나 수치를 값으로 담는다.
            """;

    /** 재추출 대상 이미지 디렉터리. prod 는 NAS, 로컬은 샘플 복사본을 가리킨다. */
    @Value("${inbody.processed.path:/mnt/200gb/NAS/inbody/processed/}")
    private String processedPath;

    private final UserBodyRecordRepository bodyRecordRepository;
    private final ClaudeClient claudeClient;
    private final ObjectMapper objectMapper;

    /**
     * @param apply false 면 차이만 계산하고 저장하지 않는다(기본).
     * @param limit 한 번에 처리할 최대 건수. 비용 통제를 위해 반드시 지정한다.
     */
    @Transactional
    public Map<String, Object> reExtract(Long userId, boolean apply, int limit) {
        List<UserBodyRecord> targets = bodyRecordRepository
                .findByUserIdAndMeasurementTypeOrderByRecordDate(userId, MeasurementType.INBODY)
                .stream()
                .filter(r -> r.getRawFilename() != null && !r.getRawFilename().isBlank())
                .limit(limit)
                .toList();

        List<Map<String, Object>> results = new ArrayList<>();
        int changed = 0;
        int failed = 0;

        for (UserBodyRecord record : targets) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("recordDate", record.getRecordDate().toString());
            row.put("file", record.getRawFilename());

            JsonNode extracted;
            try {
                extracted = extractFromImage(record.getRawFilename());
            } catch (Exception e) {
                row.put("error", e.getMessage());
                results.add(row);
                failed++;
                continue;
            }

            Map<String, Object> diff = diff(record, extracted);
            row.put("diff", diff);
            if (!diff.isEmpty()) {
                changed++;
                if (apply) {
                    applyValues(record, extracted);
                    record.setReextractedAt(LocalDateTime.now());
                }
            }
            results.add(row);
        }

        return Map.of(
                "applied", apply,
                "scanned", targets.size(),
                "withChanges", changed,
                "failed", failed,
                "results", results);
    }

    private JsonNode extractFromImage(String filename) throws IOException {
        Path path = Path.of(processedPath, filename);
        if (!Files.exists(path)) {
            throw new IOException("이미지를 찾을 수 없습니다: " + path);
        }
        byte[] bytes = Files.readAllBytes(path);
        String mediaType = filename.toLowerCase().endsWith(".png") ? "image/png" : "image/jpeg";

        String raw = claudeClient.completeWithImage(
                SYSTEM_PROMPT, "이 인바디 결과지의 값을 스키마대로 추출하라.",
                bytes, mediaType, 2000);
        if (raw == null) {
            throw new IOException("Claude 호출 실패");
        }
        return objectMapper.readTree(stripFence(raw));
    }

    /** 모델이 지시를 어기고 코드펜스를 붙이는 경우가 있어 방어한다. */
    private String stripFence(String s) {
        String t = s.trim();
        if (t.startsWith("```")) {
            int start = t.indexOf('\n');
            int end = t.lastIndexOf("```");
            if (start > 0 && end > start) return t.substring(start + 1, end).trim();
        }
        return t;
    }

    /** DB 값과 추출값이 다른 항목만 추린다. 기존 값이 없던 항목도 변경으로 본다. */
    private Map<String, Object> diff(UserBodyRecord r, JsonNode x) {
        Map<String, Object> d = new LinkedHashMap<>();
        cmp(d, "weight", r.getWeight(), num(x, "weight"));
        cmp(d, "skeletalMuscleMass", r.getSkeletalMuscleMass(), num(x, "skeletal_muscle_mass"));
        cmp(d, "bodyFatMass", r.getBodyFatMass(), num(x, "body_fat_mass"));
        cmp(d, "fatFreeMass", r.getFatFreeMass(), num(x, "fat_free_mass"));
        cmp(d, "bodyFatPercentage", r.getBodyFatPercentage(), num(x, "body_fat_percentage"));
        cmp(d, "bmi", r.getBmi(), num(x, "bmi"));
        cmp(d, "visceralFatLevel", toDouble(r.getVisceralFatLevel()), num(x, "visceral_fat_level"));
        cmp(d, "bodyWater", r.getBodyWater(), num(x, "total_body_water"));
        cmp(d, "proteinMass", r.getProteinMass(), num(x, "protein"));
        cmp(d, "mineral", r.getMineral(), num(x, "mineral"));
        cmp(d, "basalMetabolicRate", r.getBasalMetabolicRate(), num(x, "basal_metabolic_rate"));
        cmp(d, "inbodyScore", toDouble(r.getInbodyScore()), num(x, "inbody_score"));
        cmp(d, "targetWeight", r.getTargetWeight(), num(x, "target_weight"));
        cmp(d, "weightControl", r.getWeightControl(), num(x, "weight_control"));
        cmp(d, "fatControl", r.getFatControl(), num(x, "fat_control"));
        cmp(d, "muscleControl", r.getMuscleControl(), num(x, "muscle_control"));
        cmp(d, "obesityDegree", r.getObesityDegree(), num(x, "obesity_degree"));
        cmp(d, "waistHipRatio", r.getWaistHipRatio(), num(x, "waist_hip_ratio"));
        cmp(d, "recommendedIntakeKcal", toDouble(r.getRecommendedIntakeKcal()),
                num(x, "recommended_intake_kcal"));
        return d;
    }

    private void cmp(Map<String, Object> d, String key, Double before, Double after) {
        if (after == null) return;
        // 소수 셋째 자리 이하 차이는 반올림 표기 차이로 보고 무시한다.
        if (before != null && Math.abs(before - after) < 0.001) return;
        d.put(key, Map.of("before", before == null ? "null" : before, "after", after));
    }

    private void applyValues(UserBodyRecord r, JsonNode x) {
        setIfPresent(num(x, "weight"), r::setWeight);
        setIfPresent(num(x, "skeletal_muscle_mass"), r::setSkeletalMuscleMass);
        setIfPresent(num(x, "body_fat_mass"), r::setBodyFatMass);
        setIfPresent(num(x, "fat_free_mass"), r::setFatFreeMass);
        setIfPresent(num(x, "body_fat_percentage"), r::setBodyFatPercentage);
        setIfPresent(num(x, "bmi"), r::setBmi);
        setIfPresent(num(x, "total_body_water"), r::setBodyWater);
        setIfPresent(num(x, "protein"), r::setProteinMass);
        setIfPresent(num(x, "mineral"), r::setMineral);
        setIfPresent(num(x, "basal_metabolic_rate"), r::setBasalMetabolicRate);
        setIfPresent(num(x, "target_weight"), r::setTargetWeight);
        setIfPresent(num(x, "weight_control"), r::setWeightControl);
        setIfPresent(num(x, "fat_control"), r::setFatControl);
        setIfPresent(num(x, "muscle_control"), r::setMuscleControl);
        setIfPresent(num(x, "obesity_degree"), r::setObesityDegree);
        setIfPresent(num(x, "waist_hip_ratio"), r::setWaistHipRatio);

        setIfPresentInt(num(x, "visceral_fat_level"), r::setVisceralFatLevel);
        setIfPresentInt(num(x, "inbody_score"), r::setInbodyScore);
        setIfPresentInt(num(x, "recommended_intake_kcal"), r::setRecommendedIntakeKcal);

        JsonNode segmental = x.get("segmental");
        if (segmental != null && !segmental.isNull()) {
            r.setSegmentalJson(segmental.toString());
        }
    }

    private void setIfPresent(Double v, java.util.function.Consumer<Double> setter) {
        if (v != null) setter.accept(v);
    }

    private void setIfPresentInt(Double v, java.util.function.Consumer<Integer> setter) {
        if (v != null) setter.accept((int) Math.round(v));
    }

    private Double num(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull() || !v.isNumber()) ? null : v.asDouble();
    }

    private Double toDouble(Integer v) {
        return v == null ? null : v.doubleValue();
    }
}
