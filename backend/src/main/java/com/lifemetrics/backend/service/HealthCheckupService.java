package com.lifemetrics.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifemetrics.backend.dto.HealthCheckupDto;
import com.lifemetrics.backend.entity.HealthCheckup;
import com.lifemetrics.backend.repository.HealthCheckupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 건강검진(국민건강보험공단 일반건강검진 결과통보서) 기록 CRUD + PDF 추출.
 * 조회 권한 검사는 컨트롤러에서 한다(관리자 전용).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthCheckupService {

    private final HealthCheckupRepository repository;
    private final ClaudeClient claudeClient;
    private final ObjectMapper objectMapper;

    private static final String EXTRACT_SYSTEM_PROMPT = """
            너는 국민건강보험공단 "일반건강검진 결과통보서" 텍스트에서 값을 그대로 옮겨 적는 파서다.

            규칙:
            - 통보서에 인쇄된 값만 읽는다. 계산·추정·창작하지 않는다.
            - 체크박스(■ = 선택됨, □ = 선택 안 됨)를 보고 판정/결과 문자열을 고른다.
            - "비해당", "해당없음", 빈 값은 null 로 둔다.
            - 참고치(괄호 안 정상범위)가 아니라 측정값만 취한다.
            - JSON 객체 하나만 출력한다. 코드펜스·설명 문장 금지.

            출력 스키마 (없는 항목은 null):
            {
              "checkupDate": "YYYY-MM-DD",
              "checkupPlace": "내원|출장",
              "checkupOrg": "검진기관명",
              "checkupDoctor": "판정의사명",
              "overallJudgment": "정상A|정상B(경계)|일반 질환의심|고혈압·당뇨병 질환의심|유질환자",
              "extraExams": "그 외 받은 검사 (예: 우울증,조기정신증검사)",
              "suspectedDisease": "의심 질환 서술",
              "existingDisease": "유질환 서술",
              "lifestyleAdvice": "생활습관 관리 소견",
              "etcAdvice": "기타 소견",
              "heightCm": number, "weightKg": number, "bmi": number,
              "bmiGrade": "저체중|정상|과체중|비만",
              "waistCm": number, "waistResult": "정상|복부비만",
              "visionLeft": number, "visionRight": number, "visionCorrected": boolean,
              "hearingLeft": "좌 청력", "hearingRight": "우 청력", "hearingResult": "정상|질환의심",
              "systolicBp": integer, "diastolicBp": integer,
              "bpResult": "정상|고혈압 전단계|고혈압 의심",
              "hemoglobin": number, "anemiaResult": "정상|빈혈 의심",
              "fastingBloodSugar": integer, "diabetesResult": "정상|공복혈당장애 의심|당뇨병 의심",
              "totalCholesterol": integer, "hdlCholesterol": integer,
              "triglyceride": integer, "ldlCholesterol": integer,
              "lipidResult": "정상|고콜레스테롤혈증 의심 등",
              "serumCreatinine": number, "egfr": integer, "kidneyResult": "정상|신장기능 이상 의심",
              "ast": integer, "alt": integer, "ggt": integer, "liverResult": "정상|간기능 이상 의심",
              "urineProteinResult": "정상|경계|단백뇨 의심",
              "chestXrayResult": "정상|비활동성 폐결핵|질환의심",
              "pastHistory": "과거병력", "medication": "약물치료",
              "needSmokingCessation": boolean, "needDrinkingReduction": boolean,
              "needPhysicalActivity": boolean, "needStrengthExercise": boolean,
              "hepBResult": "B형간염 결과", "hepCResult": "C형간염 결과",
              "depressionResult": "우울증상이 없음|가벼운 우울증상|중간 정도 우울증 의심|심한 우울증 의심",
              "depressionScore": integer,
              "psychosisResult": "조기정신증 결과", "cognitiveResult": "인지기능장애 결과",
              "boneDensityResult": "골밀도검사 결과", "urinationResult": "배뇨장애 결과"
            }
            """;

    // ── 조회 ────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<HealthCheckupDto> list(Long userId) {
        return repository.findByUserIdOrderByCheckupDateDesc(userId).stream()
                .map(HealthCheckupDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public HealthCheckupDto get(Long id) {
        return repository.findById(id).map(HealthCheckupDto::from)
                .orElseThrow(() -> new IllegalArgumentException("건강검진 기록을 찾을 수 없습니다: " + id));
    }

    // ── 저장 ────────────────────────────────────────
    @Transactional
    public HealthCheckupDto create(Long userId, HealthCheckupDto req) {
        HealthCheckup e = new HealthCheckup();
        e.setUserId(userId);
        req.applyTo(e);
        return HealthCheckupDto.from(repository.save(e));
    }

    @Transactional
    public HealthCheckupDto update(Long id, HealthCheckupDto req) {
        HealthCheckup e = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("건강검진 기록을 찾을 수 없습니다: " + id));
        req.applyTo(e);
        return HealthCheckupDto.from(repository.save(e));
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    // ── PDF 추출 (저장 안 함, 폼 prefill 용) ─────────
    public HealthCheckupDto extractFromPdf(MultipartFile file) throws IOException {
        String text = extractText(file);
        if (text.isBlank()) {
            throw new IllegalStateException("PDF에서 텍스트를 읽지 못했습니다. 스캔 이미지 PDF는 지원하지 않습니다.");
        }

        // 결과통보서 3쪽 + 50여 항목이라 확장 사고에 토큰을 넉넉히 줘야 text 블록까지 온다.
        String llmJson = claudeClient.complete(EXTRACT_SYSTEM_PROMPT, text, 8000);
        if (llmJson == null) {
            throw new IllegalStateException("AI 추출에 실패했습니다. 잠시 후 다시 시도하거나 수동으로 입력해 주세요.");
        }

        HealthCheckupDto dto = parseJson(llmJson);
        dto.setRawText(text);
        dto.setSourceFile(file.getOriginalFilename());
        return dto;
    }

    private String extractText(MultipartFile file) throws IOException {
        try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
            return new PDFTextStripper().getText(doc).trim();
        }
    }

    private HealthCheckupDto parseJson(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            log.warn("건강검진 AI 응답에서 JSON을 찾지 못함: {}", raw);
            throw new IllegalStateException("AI 응답을 해석하지 못했습니다.");
        }
        try {
            return objectMapper.readValue(raw.substring(start, end + 1), HealthCheckupDto.class);
        } catch (IOException e) {
            log.warn("건강검진 AI JSON 파싱 실패: {}", e.getMessage());
            throw new IllegalStateException("AI 응답을 해석하지 못했습니다.");
        }
    }
}
