package com.lifemetrics.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifemetrics.backend.entity.AiAnalysis;
import com.lifemetrics.backend.entity.AthleteChatMessage;
import com.lifemetrics.backend.persona.diary.DiaryService;
import com.lifemetrics.backend.persona.diary.LectureService;
import com.lifemetrics.backend.repository.AiAnalysisRepository;
import com.lifemetrics.backend.repository.AthleteChatMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 종합 분석: 하루 동안 페르소나별로 남긴 기록을 한데 모아 AI 가 하루 일과 분석 글을 쓴다.
 * <ul>
 *   <li>애슬리트: 일일 분석 입력(운동·라이딩·식단·체성분·치료)과, 있으면 그날 일일 분석 결과, 애슬리트 챗에 한 질문</li>
 *   <li>휴먼: 일기 메모·일기 초안, 그날 강연</li>
 *   <li>일: 업무 일지</li>
 * </ul>
 * 앱 '종합 분석' 화면의 분석 글 자리에 쓰인다. 입력 해시를 저장해 두어 조회 때 입력이 바뀌었는지(stale) 알려 준다.
 * 일일 분석과 달리 새벽 배치는 없고, 요청할 때만 만든다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyInsightService {

    static final String ANALYSIS_TYPE = "insight";
    static final long TARGET_ID = 0L;
    /** 프롬프트가 너무 길어지지 않게 글 하나당 자르는 길이(글자). */
    static final int MAX_TEXT = 3000;
    static final int MAX_QUESTION = 500;

    private final AiAnalysisRepository analysisRepo;
    private final AiAnalysisService aiAnalysisService;
    private final DailyAnalysisService dailyAnalysis;
    private final DiaryService diary;
    private final LectureService lectures;
    private final AthleteChatMessageRepository chatMessages;
    private final ObjectMapper objectMapper;

    public record Stored(JsonNode insight, boolean stale) {}

    /** 저장된 종합 분석과, 그 뒤로 입력이 바뀌었는지. 없으면 비어 있다. */
    public Optional<Stored> find(long userId, LocalDate date) {
        return findEntity(userId, date).map(a -> {
            JsonNode node = parse(a.getAnalysisData());
            String currentHash = hash(collectInput(userId, date));
            return new Stored(node, !currentHash.equals(node.path("inputHash").asText()));
        });
    }

    /** 지금 분석한다. @return 저장된 분석, 그날 기록이 하나도 없으면 비어 있다. */
    public Optional<JsonNode> analyzeNow(long userId, LocalDate date) {
        Map<String, Object> input = collectInput(userId, date);
        if (input.isEmpty()) return Optional.empty();

        String resultJson = aiAnalysisService.requestClaude(buildPrompt(date, toJson(input)));
        if (resultJson == null) {
            throw new DailyAnalysisService.AnalysisFailedException("종합 분석 호출에 실패했습니다: " + date);
        }

        Map<String, Object> stored = new LinkedHashMap<>();
        stored.put("date", date.toString());
        stored.put("analyzedAt", LocalDateTime.now(DailyAnalysisService.KST).withNano(0).toString());
        stored.put("inputHash", hash(input));
        stored.put("sources", List.copyOf(input.keySet()));
        stored.put("input", input);
        stored.put("result", parse(resultJson));
        String data = toJson(stored);

        AiAnalysis analysis = findEntity(userId, date).orElseGet(() -> {
            AiAnalysis a = new AiAnalysis();
            a.setUserId(userId);
            a.setAnalysisType(ANALYSIS_TYPE);
            a.setTargetId(TARGET_ID);
            a.setTargetPeriod(date.toString());
            return a;
        });
        analysis.setAnalysisData(data);
        analysisRepo.save(analysis);
        log.info("종합 분석 저장: {}", date);
        return Optional.of(parse(data));
    }

    private Optional<AiAnalysis> findEntity(long userId, LocalDate date) {
        return analysisRepo.findByUserIdAndAnalysisTypeAndTargetIdAndTargetPeriod(
                userId, ANALYSIS_TYPE, TARGET_ID, date.toString());
    }

    // ── 입력 수집 ────────────────────────────────────

    /** 값이 있는 영역만 키로 넣는다. 한 곳이 실패해도(일기 DB 등) 나머지로 분석한다. */
    Map<String, Object> collectInput(long userId, LocalDate date) {
        Map<String, Object> input = new LinkedHashMap<>();
        put(input, "athleteRecords", date, () -> athleteRecords(userId, date));
        put(input, "athleteDailyAnalysis", date, () -> dailyAnalysis.find(userId, date)
                .map(n -> n.path("result")).filter(n -> !n.isMissingNode() && !n.isNull()).orElse(null));
        put(input, "athleteChatQuestions", date, () -> chatQuestions(date));
        put(input, "diaryNotes", date, () -> diary.notes(date).stream()
                .map(n -> cut(n.content(), MAX_TEXT)).filter(s -> !s.isBlank()).toList());
        put(input, "diary", date, () -> diary.diaryOf(date).map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("title", e.title());
            m.put("content", cut(e.content(), MAX_TEXT));
            m.put("status", e.status());
            return m;
        }).orElse(null));
        put(input, "lectures", date, () -> lectures.forDate(date).stream().map(l -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("title", l.title());
            m.put("speaker", l.speaker());
            m.put("memo", cut(l.memo(), MAX_TEXT));
            return m;
        }).toList());
        put(input, "workLog", date, () -> diary.workLogOf(date));
        return input;
    }

    private Map<String, Object> athleteRecords(long userId, LocalDate date) {
        Map<String, Object> out = new LinkedHashMap<>();
        dailyAnalysis.collectInput(userId, date).forEach((k, v) -> {
            if (!isBlank(v)) out.put(k, v);
        });
        return out;
    }

    private List<String> chatQuestions(LocalDate date) {
        List<String> out = new ArrayList<>();
        for (AthleteChatMessage m : chatMessages.findByRoleAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByIdAsc(
                "user", date.atStartOfDay(), date.plusDays(1).atStartOfDay())) {
            out.add(cut(m.getContent(), MAX_QUESTION));
        }
        return out;
    }

    private static void put(Map<String, Object> input, String key, LocalDate date, Supplier<Object> value) {
        try {
            Object v = value.get();
            if (!isBlank(v)) input.put(key, v);
        } catch (Exception e) {
            log.warn("종합 분석 입력 '{}' 조회 실패 {}: {}", key, date, e.getMessage());
        }
    }

    static boolean isBlank(Object v) {
        return v == null
                || (v instanceof String s && s.isBlank())
                || (v instanceof Collection<?> c && c.isEmpty())
                || (v instanceof Map<?, ?> m && m.isEmpty());
    }

    static String cut(String s, int max) {
        if (s == null) return "";
        String t = s.strip();
        return t.length() <= max ? t : t.substring(0, max) + "…(생략)";
    }

    // ── 프롬프트 ─────────────────────────────────────

    static String buildPrompt(LocalDate date, String inputJson) {
        return """
                당신은 한 사람의 하루를 여러 면에서 함께 보는 코치입니다. 이 사람은 랜도너스(장거리 자전거 대회)를 타고
                웨이트를 병행하는 라이더이자, 회사에 다니는 개발자이며, 일기와 강연·독서 메모를 남깁니다.
                아래는 %s 하루 동안 남긴 기록입니다(JSON). 이 날의 기록만 근거로, 영역을 따로따로가 아니라 서로 이어서 보세요.
                기록에 없는 사실이나 수치를 지어내지 말고, 다른 날과 비교하지 마세요.

                데이터:
                - athleteRecords: 운동·라이딩·식단·체성분·폰 활동량·치료(treatments). 값이 없는 키는 빠져 있습니다.
                - athleteDailyAnalysis: 같은 날 이미 만든 운동·식단 코치 분석. 수치 해석은 이것을 따르고 되풀이하지 말고 요점만 쓰세요.
                - athleteChatQuestions: 그날 애슬리트 챗에 내가 한 질문. 무엇이 궁금하고 신경 쓰였는지를 보여 줍니다.
                - diaryNotes, diary: 그날 메모와 일기(초안일 수 있음). 감정·생각·사건의 근거입니다.
                - lectures: 그날 들은 강연.
                - workLog: 업무 일지(today_done 한 일, tomorrow_plan 내일 계획, issues 이슈).

                쓰는 법:
                - 존댓말 대신 담백한 평서문(~했다, ~이다)으로, 짧게 씁니다. 한국어로만 씁니다.
                - connections 에는 영역 사이 연결만 씁니다. 예: 야근·업무 이슈와 운동량·식사, 컨디션과 일기 속 기분, 강연 내용과 그날 한 일.
                  근거가 약하면 억지로 만들지 말고 빈 배열로 둡니다.
                - 기록이 없는 영역의 값은 null 로 둡니다.

                다음 JSON 형식으로만 답하세요. 설명이나 코드블록 없이 JSON 만 출력합니다.
                {
                  "title": "하루를 한 줄로",
                  "summary": "하루 총평 3~4문장",
                  "athlete": "몸과 운동",
                  "human": "생각·감정·배운 것 (일기·메모·강연)",
                  "work": "일",
                  "connections": ["영역 사이 연결"],
                  "goodPoints": ["잘한 점"],
                  "tomorrow": ["내일 해 볼 것 (업무 일지의 내일 계획이 있으면 반영)"]
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
            throw new IllegalStateException("종합 분석 JSON 변환 실패", e);
        }
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("저장된 종합 분석을 읽지 못했습니다", e);
        }
    }
}
