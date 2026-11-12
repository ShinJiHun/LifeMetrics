package com.lifemetrics.backend.health.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifemetrics.backend.health.entity.HealthTreatment;
import com.lifemetrics.backend.service.ClaudeClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 진단서·소견서·진료확인서(PDF·사진)를 AI 로 읽어 치료 기록 입력 폼을 미리 채운다. 저장하지 않는다.
 * <p>
 * 진단서에는 주민등록번호·주소 같은 개인정보가 있으므로 원문은 저장하지 않고,
 * 분석에 필요한 항목(진단명·상병코드·부위·날짜·소견)만 뽑는다. 파일 자체도 보관하지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TreatmentDocumentService {

    /** 이미지는 API 한도(5MB), PDF 는 base64 로 33% 커지는 걸 감안해 요청 한도(32MB) 안쪽으로 */
    static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    static final long MAX_PDF_BYTES = 20L * 1024 * 1024;
    /** 한 번에 올리는 파일 수·합계 (API 요청 32MB 한도, base64 로 33% 커진다) */
    static final int MAX_FILES = 20;
    static final long MAX_TOTAL_BYTES = 22L * 1024 * 1024;

    /** 업로드된 파일 1개 */
    public record Upload(String filename, String contentType, byte[] bytes) {}

    private static final Map<String, String> TYPES_BY_EXT = Map.of(
            "pdf", "application/pdf",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp",
            "gif", "image/gif");

    static final String SYSTEM_PROMPT = """
            너는 한국 병·의원 문서(진단서, 소견서, 진료확인서, 통원확인서 등)에서 치료 기록에 필요한 값만 옮겨 적는 파서다.

            규칙:
            - 문서에 적힌 내용만 쓴다. 추정·창작하지 않는다. 읽을 수 없거나 없는 항목은 null.
            - 환자 이름, 주민등록번호, 주소, 연락처, 면허번호 같은 개인 식별 정보는 어떤 필드에도 옮기지 않는다.
            - 진단명이 여러 개면 주상병을 name 에, 나머지는 notes 에 "부상병: ..." 으로 적는다.
            - 날짜는 YYYY-MM-DD.
            - JSON 객체 하나만 출력한다. 코드펜스·설명 문장 금지.

            출력 스키마:
            {
              "isMedicalDocument": true,
              "name": "주상병 진단명 (문서 표기 그대로, 한글 우선)",
              "diagnosisCode": "KCD 상병코드 (예: M76.3)",
              "bodyPart": "부위 (좌/우 포함, 예: 우측 슬관절 외측). 진단명에 부위가 드러나면 거기서 옮긴다",
              "startDate": "초진일·진단일이 있으면 그 날짜, 없으면 발병일, 그것도 없으면 발행일",
              "endDate": "치료 종료·완치일이 명시된 경우만, 아니면 null",
              "restrictions": "의사 소견 중 운동·활동 제한, 안정·가료 기간 (예: 향후 4주간 안정 가료 요함)",
              "notes": "의료기관명, 문서 종류·발행일, 소견 요약, 치료 내용(물리치료·주사 등), 부상병"
            }
            병·의원 문서가 아니면 {"isMedicalDocument": false} 만 출력한다.
            """;

    private final ClaudeClient claudeClient;
    private final ObjectMapper objectMapper;

    public HealthTreatment extract(String originalFilename, String contentType, byte[] bytes) {
        return extract(List.of(new Upload(originalFilename, contentType, bytes)));
    }

    /** 여러 장(예: 진단서 앞뒤 사진)을 한 문서로 보고 한 번에 읽는다. */
    public HealthTreatment extract(List<Upload> uploads) {
        List<ClaudeClient.FileInput> files = toFileInputs(uploads);
        String raw = claudeClient.completeWithFiles(SYSTEM_PROMPT,
                "이 문서에서 치료 기록 값을 스키마대로 추출해." + (files.size() > 1 ? " 파일 여러 개는 같은 문서의 여러 장이다." : ""),
                files, 2000);
        if (raw == null) {
            throw new IllegalStateException("AI 판독에 실패했습니다. 잠시 후 다시 시도하거나 직접 입력해 주세요.");
        }
        HealthTreatment t = parse(raw);
        t.setSourceFile(sourceName(uploads));
        return t;
    }

    /** 형식·크기를 확인하고 API 에 실을 형태로 바꾼다. 여러 개면 "[파일 n/N]" 라벨을 붙인다. */
    static List<ClaudeClient.FileInput> toFileInputs(List<Upload> uploads) {
        if (uploads == null || uploads.isEmpty()) {
            throw new IllegalArgumentException("파일을 선택해 주세요.");
        }
        if (uploads.size() > MAX_FILES) {
            throw new IllegalArgumentException("한 번에 " + MAX_FILES + "개까지 올릴 수 있습니다.");
        }
        List<ClaudeClient.FileInput> out = new ArrayList<>();
        long total = 0;
        for (int i = 0; i < uploads.size(); i++) {
            Upload u = uploads.get(i);
            String mediaType = mediaTypeOf(u.filename(), u.contentType());
            if (mediaType == null) {
                throw new IllegalArgumentException(nameOf(u) + ": PDF 또는 이미지(JPG·PNG·WEBP) 파일만 올릴 수 있습니다.");
            }
            long limit = "application/pdf".equals(mediaType) ? MAX_PDF_BYTES : MAX_IMAGE_BYTES;
            if (u.bytes() == null || u.bytes().length == 0 || u.bytes().length > limit) {
                throw new IllegalArgumentException(nameOf(u) + ": 파일이 비었거나 너무 큽니다 (PDF 20MB, 이미지 5MB 까지).");
            }
            total += u.bytes().length;
            String label = uploads.size() > 1 ? "[파일 " + (i + 1) + "/" + uploads.size() + "]" : null;
            out.add(new ClaudeClient.FileInput(label, u.bytes(), mediaType));
        }
        if (total > MAX_TOTAL_BYTES) {
            throw new IllegalArgumentException("파일 합계가 너무 큽니다 (22MB 까지). 나눠서 올려 주세요.");
        }
        return out;
    }

    /** 원본 파일명들 (DB 컬럼 255자 안으로) */
    static String sourceName(List<Upload> uploads) {
        String joined = String.join(", ", uploads.stream().map(TreatmentDocumentService::nameOf).toList());
        return joined.length() <= 255 ? joined : joined.substring(0, 252) + "...";
    }

    private static String nameOf(Upload u) {
        return u.filename() == null || u.filename().isBlank() ? "(이름 없음)" : u.filename();
    }

    /** 확장자를 우선한다(폰에서 올린 파일은 content-type 이 octet-stream 인 경우가 있다). */
    static String mediaTypeOf(String filename, String contentType) {
        if (filename != null && filename.contains(".")) {
            String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            if (TYPES_BY_EXT.containsKey(ext)) return TYPES_BY_EXT.get(ext);
        }
        return contentType != null && TYPES_BY_EXT.containsValue(contentType) ? contentType : null;
    }

    HealthTreatment parse(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        JsonNode json;
        try {
            if (start < 0 || end <= start) throw new IOException("JSON 없음");
            json = objectMapper.readTree(raw.substring(start, end + 1));
        } catch (IOException e) {
            log.warn("진단서 AI 응답 해석 실패: {}", e.getMessage());
            throw new IllegalStateException("AI 응답을 해석하지 못했습니다. 직접 입력해 주세요.");
        }
        if (!json.path("isMedicalDocument").asBoolean(true)) {
            throw new IllegalStateException("진단서·소견서 같은 병·의원 문서로 보이지 않습니다.");
        }

        HealthTreatment t = new HealthTreatment();
        t.setName(text(json, "name"));
        t.setDiagnosisCode(text(json, "diagnosisCode"));
        t.setBodyPart(text(json, "bodyPart"));
        t.setStartDate(date(json, "startDate"));
        t.setEndDate(date(json, "endDate"));
        t.setRestrictions(text(json, "restrictions"));
        t.setNotes(text(json, "notes"));
        if (t.getName() == null) {
            throw new IllegalStateException("문서에서 진단명을 찾지 못했습니다. 직접 입력해 주세요.");
        }
        return t;
    }

    private static String text(JsonNode json, String field) {
        JsonNode n = json.path(field);
        if (n.isMissingNode() || n.isNull()) return null;
        String s = n.asText().trim();
        return s.isEmpty() || "null".equals(s) ? null : s;
    }

    private static LocalDate date(JsonNode json, String field) {
        String s = text(json, field);
        if (s == null) return null;
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            return null; // 날짜를 못 읽으면 비워 두고 화면에서 채운다
        }
    }
}
