package com.lifemetrics.backend.health.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifemetrics.backend.health.entity.HealthTreatment;
import com.lifemetrics.backend.health.entity.HealthTreatmentVisit;
import com.lifemetrics.backend.health.repository.HealthTreatmentRepository;
import com.lifemetrics.backend.health.repository.HealthTreatmentVisitRepository;
import com.lifemetrics.backend.service.ClaudeClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * 진료·처방 내역(처방전, 약제비 영수증, 진료비 (세부)내역서, 처방조제내역 …)을 AI 로 읽어 치료 1건 아래에 날짜별로 쌓는다.
 * <p>
 * extract 는 저장하지 않고 미리보기만 돌려준다. 화면에서 확인한 뒤 save 로 저장한다.
 * 원본 파일과 원문 텍스트는 저장하지 않고, 개인 식별 정보(이름·주민번호·주소·면허번호 등)는 옮기지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TreatmentVisitService {

    static final Set<String> CATEGORIES = Set.of("진찰", "검사", "영상", "주사", "물리치료", "처치", "약", "재료", "기타");

    static final String SYSTEM_PROMPT = """
            너는 한국 병·의원·약국 문서에서 진료·처방 내역을 옮겨 적는 파서다.
            대상 문서: 처방전, 약제비 영수증(복약안내 포함), 진료비 영수증, 진료비 세부산정내역서(세부내역서),
            건강보험 앱의 진료내역·처방조제내역(투약정보) 캡처 등.

            규칙:
            - 문서에 적힌 내용만 쓴다. 추정·창작하지 않는다. 읽을 수 없거나 없는 항목은 null.
            - 환자 이름, 주민등록번호, 주소, 연락처, 의사·약사 이름, 면허번호, 처방전 교부번호 같은 개인 식별 정보는 어떤 필드에도 옮기지 않는다.
            - 진료일(약국은 조제일)마다 visits 원소 하나. 세부내역서처럼 여러 날짜가 있으면 날짜별로 나눈다.
              일자 구분 없이 기간만 있으면 기간 첫날 하나로 묶고 summary 에 기간을 적는다.
            - items 는 그날 받은 진료·처방 항목. category 는 진찰 / 검사 / 영상 / 주사 / 물리치료 / 처치 / 약 / 재료 / 기타 중 하나.
              · 진찰료·입원료 같은 기본료는 category "진찰" 하나로만 (여러 줄이면 합쳐도 된다).
              · 물리치료는 종류가 드러나게 (예: 체외충격파, 표층열치료, 간섭전류치료, 도수치료).
              · 약은 제품명 그대로, dose(1회 투약량), timesPerDay(1일 투여횟수), days(투약일수) 를 숫자로.
                drugClass 에는 그 약의 일반적인 분류를 짧게 (예: 소염진통제(NSAID), 근이완제, 위장보호제, 스테로이드, 국소마취제). 모르면 null.
              · 주사도 약품명이 있으면 name 에 약품명, drugClass 에 분류.
            - amount 는 항목 금액(원, 숫자), 모르면 null. totalAmount 는 그날 진료비 총액, patientAmount 는 본인부담금.
            - diagnosis 는 문서에 상병명·상병코드가 있으면 "명칭 코드" 형태로 (예: 장경인대 증후군 M76.3).
            - summary 는 그날 무엇을 받았는지 한 줄 (예: "체외충격파·간섭전류치료, 소염진통제 등 3종 5일분").
            - 날짜는 YYYY-MM-DD.
            - JSON 객체 하나만 출력한다. 코드펜스·설명 문장 금지.

            출력 스키마:
            {
              "isMedicalDocument": true,
              "docType": "처방전 | 약제비 영수증 | 진료비 영수증 | 진료비 세부내역서 | 처방조제내역 | 진료내역 | 기타",
              "visits": [
                {
                  "date": "YYYY-MM-DD",
                  "institution": "의료기관명",
                  "department": "진료과",
                  "diagnosis": "상병명 상병코드",
                  "items": [
                    {"category": "약", "name": "", "code": null, "dose": "1정", "timesPerDay": 3, "days": 5, "drugClass": "", "amount": null}
                  ],
                  "totalAmount": null,
                  "patientAmount": null,
                  "summary": ""
                }
              ]
            }
            병·의원·약국 문서가 아니면 {"isMedicalDocument": false} 만 출력한다.
            """;

    /** 분석에 넣는 진료 기간: 그 날짜 포함 최근 14일 */
    static final int RECENT_DAYS = 14;
    /** 약 복용 기간을 보려고 더 거슬러 올라가는 범위 */
    static final int MED_LOOKBACK_DAYS = 60;

    private final ClaudeClient claudeClient;
    private final ObjectMapper objectMapper;
    private final HealthTreatmentRepository treatmentRepo;
    private final HealthTreatmentVisitRepository visitRepo;

    public List<HealthTreatmentVisit> list(long userId) {
        return visitRepo.findByUserIdOrderByVisitDateDescIdDesc(userId);
    }

    // ── 문서 읽기 ──────────────────────────────────

    /** 문서 → 날짜별 진료 미리보기 (저장 안 함). treatmentId 는 채워서 돌려준다. */
    public List<HealthTreatmentVisit> extract(long treatmentId, String originalFilename, String contentType, byte[] bytes) {
        return extract(treatmentId, List.of(new TreatmentDocumentService.Upload(originalFilename, contentType, bytes)));
    }

    /** 여러 장(세부내역서 여러 페이지, 처방전+영수증 등)을 한 번에 읽어 날짜별로 합친다. */
    public List<HealthTreatmentVisit> extract(long treatmentId, List<TreatmentDocumentService.Upload> uploads) {
        List<ClaudeClient.FileInput> files = TreatmentDocumentService.toFileInputs(uploads);
        String instruction = files.size() > 1
                ? "파일 여러 개의 진료·처방 내역을 스키마대로 추출해. 한 문서가 여러 장으로 이어질 수 있고 서로 다른 문서가 섞여 있을 수도 있다."
                  + " 같은 날짜·같은 기관의 같은 진료가 여러 장(예: 영수증과 세부내역서)에 겹쳐 나오면 visits 하나로 합치고 항목을 중복해 적지 않는다."
                  + " docType 은 가장 자세한 문서 종류로 하나만 적는다."
                : "이 문서의 진료·처방 내역을 스키마대로 추출해.";
        String raw = claudeClient.completeWithFiles(SYSTEM_PROMPT, instruction, files, 16000);
        if (raw == null) {
            throw new IllegalStateException("AI 판독에 실패했습니다. 잠시 후 다시 시도해 주세요.");
        }
        List<HealthTreatmentVisit> visits = parse(raw);
        String source = TreatmentDocumentService.sourceName(uploads);
        visits.forEach(v -> {
            v.setTreatmentId(treatmentId);
            v.setSourceFile(source);
        });
        return visits;
    }

    List<HealthTreatmentVisit> parse(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        JsonNode json;
        try {
            if (start < 0 || end <= start) throw new IOException("JSON 없음");
            json = objectMapper.readTree(raw.substring(start, end + 1));
        } catch (IOException e) {
            log.warn("진료 문서 AI 응답 해석 실패: {}", e.getMessage());
            throw new IllegalStateException("AI 응답을 해석하지 못했습니다. 문서가 길면 페이지를 나눠 올려 주세요.");
        }
        if (!json.path("isMedicalDocument").asBoolean(true)) {
            throw new IllegalStateException("처방전·영수증·세부내역서 같은 병·의원·약국 문서로 보이지 않습니다.");
        }
        String docType = text(json, "docType");
        List<HealthTreatmentVisit> result = new ArrayList<>();
        for (JsonNode v : json.path("visits")) {
            LocalDate date = date(v, "date");
            if (date == null) continue; // 날짜 없는 진료는 타임라인에 놓을 수 없다
            HealthTreatmentVisit visit = new HealthTreatmentVisit();
            visit.setVisitDate(date);
            visit.setDocType(docType);
            visit.setInstitution(text(v, "institution"));
            visit.setDepartment(text(v, "department"));
            visit.setDiagnosis(text(v, "diagnosis"));
            visit.setTotalAmount(integer(v, "totalAmount"));
            visit.setPatientAmount(integer(v, "patientAmount"));
            visit.setSummary(text(v, "summary"));
            visit.setItems(items(v.path("items")));
            result.add(visit);
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("문서에서 진료 날짜를 찾지 못했습니다.");
        }
        result.sort(Comparator.comparing(HealthTreatmentVisit::getVisitDate));
        return result;
    }

    private List<Map<String, Object>> items(JsonNode arr) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (JsonNode it : arr) {
            String name = text(it, "name");
            if (name == null) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            String category = text(it, "category");
            m.put("category", category != null && CATEGORIES.contains(category) ? category : "기타");
            m.put("name", name);
            putIf(m, "code", text(it, "code"));
            putIf(m, "dose", text(it, "dose"));
            if ("약".equals(m.get("category"))) { // 투약 횟수·일수는 약에만 의미가 있다 (물리치료 "1일분" 같은 잡음 제거)
                putIf(m, "timesPerDay", number(it, "timesPerDay"));
                putIf(m, "days", number(it, "days"));
            }
            putIf(m, "drugClass", text(it, "drugClass"));
            putIf(m, "amount", integer(it, "amount"));
            out.add(m);
        }
        return out;
    }

    // ── 저장 ──────────────────────────────────

    /**
     * 미리보기에서 확인한 진료들을 저장한다. 같은 치료·날짜·기관·문서종류가 이미 있으면 새 것으로 바꾼다
     * (같은 문서를 다시 올려도 중복되지 않게).
     */
    @Transactional
    public List<HealthTreatmentVisit> save(long treatmentId, List<HealthTreatmentVisit> visits) {
        HealthTreatment t = treatmentRepo.findById(treatmentId)
                .orElseThrow(() -> new IllegalArgumentException("치료 기록을 찾을 수 없습니다."));
        List<HealthTreatmentVisit> saved = new ArrayList<>();
        for (HealthTreatmentVisit req : visits) {
            if (req.getVisitDate() == null) throw new IllegalArgumentException("진료일이 없는 항목이 있습니다.");
            for (HealthTreatmentVisit old : visitRepo.findByTreatmentIdAndVisitDate(treatmentId, req.getVisitDate())) {
                if (Objects.equals(old.getInstitution(), blankToNull(req.getInstitution()))
                        && Objects.equals(old.getDocType(), blankToNull(req.getDocType()))) {
                    visitRepo.delete(old);
                }
            }
            HealthTreatmentVisit v = new HealthTreatmentVisit();
            v.setUserId(t.getUserId());
            v.setTreatmentId(treatmentId);
            v.setVisitDate(req.getVisitDate());
            v.setInstitution(blankToNull(req.getInstitution()));
            v.setDepartment(blankToNull(req.getDepartment()));
            v.setDocType(blankToNull(req.getDocType()));
            v.setDiagnosis(blankToNull(req.getDiagnosis()));
            v.setItems(req.getItems() == null ? List.of() : req.getItems());
            v.setTotalAmount(req.getTotalAmount());
            v.setPatientAmount(req.getPatientAmount());
            v.setSummary(blankToNull(req.getSummary()));
            v.setSourceFile(blankToNull(req.getSourceFile()));
            saved.add(visitRepo.save(v));
        }
        return saved;
    }

    @Transactional
    public boolean delete(long visitId) {
        if (!visitRepo.existsById(visitId)) return false;
        visitRepo.deleteById(visitId);
        return true;
    }

    // ── 분석 입력용 ──────────────────────────────────

    /**
     * 치료별로, 그 날짜 기준 최근 14일 진료 + 그날까지 복용 기간이 이어지는 약. 진료 기록이 없으면 빈 맵.
     * 값: 진료 1회당 {date, daysAgo, institution?, items[문자열]} (금액은 분석에 필요 없어 뺀다).
     */
    public Map<Long, List<Map<String, Object>>> recentCareOf(Collection<Long> treatmentIds, LocalDate date) {
        if (treatmentIds.isEmpty()) return Map.of();
        List<HealthTreatmentVisit> visits = visitRepo.findForAnalysis(treatmentIds, date.minusDays(MED_LOOKBACK_DAYS), date);
        Map<Long, List<Map<String, Object>>> out = new LinkedHashMap<>();
        for (HealthTreatmentVisit v : visits) {
            long daysAgo = date.toEpochDay() - v.getVisitDate().toEpochDay();
            boolean recent = daysAgo < RECENT_DAYS;
            List<String> lines = new ArrayList<>();
            for (Map<String, Object> it : v.getItems() == null ? List.<Map<String, Object>>of() : v.getItems()) {
                if ("진찰".equals(it.get("category")) || "재료".equals(it.get("category"))) continue;
                boolean isMed = "약".equals(it.get("category"));
                Integer days = it.get("days") instanceof Number n ? n.intValue() : null;
                boolean medOngoing = isMed && days != null && daysAgo < days;
                if (!recent && !medOngoing) continue;
                lines.add(itemLine(it) + (medOngoing ? " — 이 날 복용 기간 중" : ""));
            }
            if (lines.isEmpty()) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", v.getVisitDate().toString());
            m.put("daysAgo", daysAgo);
            if (v.getInstitution() != null) m.put("institution", v.getInstitution());
            m.put("items", lines);
            out.computeIfAbsent(v.getTreatmentId(), k -> new ArrayList<>()).add(m);
        }
        return out;
    }

    /** "약: 록소프로펜정 1정×3회 5일분 (소염진통제(NSAID))" */
    static String itemLine(Map<String, Object> it) {
        StringBuilder sb = new StringBuilder().append(it.get("category")).append(": ").append(it.get("name"));
        if (it.get("dose") != null) sb.append(' ').append(it.get("dose"));
        if (it.get("timesPerDay") != null) sb.append('×').append(num(it.get("timesPerDay"))).append("회");
        if (it.get("days") != null) sb.append(' ').append(num(it.get("days"))).append("일분");
        if (it.get("drugClass") != null) sb.append(" (").append(it.get("drugClass")).append(')');
        return sb.toString();
    }

    private static String num(Object o) {
        return o instanceof Number n && n.doubleValue() == Math.floor(n.doubleValue()) ? String.valueOf(n.longValue()) : String.valueOf(o);
    }

    // ── helpers ──────────────────────────────────

    private static void putIf(Map<String, Object> m, String k, Object v) {
        if (v != null) m.put(k, v);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String text(JsonNode json, String field) {
        JsonNode n = json.path(field);
        if (n.isMissingNode() || n.isNull()) return null;
        String s = n.asText().trim();
        return s.isEmpty() || "null".equals(s) ? null : s;
    }

    private static Number number(JsonNode json, String field) {
        JsonNode n = json.path(field);
        if (n.isNumber()) {
            // 삼항 연산자로 쓰면 int 가 double 로 승격돼 1 이 1.0 이 된다
            if (n.isIntegralNumber() || n.doubleValue() == Math.floor(n.doubleValue())) return n.intValue();
            return n.doubleValue();
        }
        String s = text(json, field);
        if (s == null) return null;
        try {
            double d = Double.parseDouble(s.replaceAll("[^0-9.]", ""));
            return d == Math.floor(d) ? (Number) (int) d : d;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer integer(JsonNode json, String field) {
        JsonNode n = json.path(field);
        if (n.isNumber()) return n.intValue();
        String s = text(json, field);
        if (s == null) return null;
        String digits = s.replaceAll("[^0-9]", "");
        try {
            return digits.isEmpty() ? null : Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate date(JsonNode json, String field) {
        String s = text(json, field);
        if (s == null) return null;
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
