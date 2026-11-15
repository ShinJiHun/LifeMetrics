package com.lifemetrics.backend.ai.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifemetrics.backend.ai.rag.NoteIndexService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** RAG 검색: 숫자가 아니라 "무슨 말을 들었는지·무엇을 지적받았는지" 같은 서술형 기록을 의미로 찾는다. */
@Component
@RequiredArgsConstructor
public class SearchNotesTool implements AthleteTool {

    static final int DEFAULT_K = 6;
    static final int MAX_K = 10;

    private final NoteIndexService index;

    @Override
    public String name() {
        return "search_notes";
    }

    @Override
    public String description() {
        return "서술형 기록을 의미로 검색한다: 치료·부상 메모와 운동 제한, 진료·처방 내역(진단, 시술, 약), "
                + "과거 AI 분석(라이딩·운동·일일·브레베 계획 분석의 요약·하이라이트·개선점), 체성분 목표 메모, 랜도너스 대회 메모. "
                + "\"의사가 뭐라고 했지\", \"전에 지적받은 자세 문제\", \"지난 분석에서 개선점\"처럼 내용으로 찾을 때 쓴다. "
                + "수치·기간 합계는 다른 조회 도구가 정확하다. 결과마다 출처와 날짜가 붙으니 답변에서 출처를 밝혀라. "
                + "검색은 키워드 기반일 수 있으니 query 에는 문장 대신 핵심 단어를 여러 개 넣어라 "
                + "(예: \"무릎 통증 장경인대 운동 제한 주사\"). 결과가 없으면 비슷한 말로 한 번 더 찾아라.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("query", Map.of("type", "string", "description", "찾을 내용 (자연어)"));
        p.put("sources", Map.of("type", "array",
                "items", Map.of("type", "string", "enum", List.copyOf(new java.util.TreeSet<>(NoteIndexService.SOURCES))),
                "description", "출처 제한. 없으면 전체"));
        p.put("from", ToolInputs.dateProp("기록 날짜 시작 (포함). 없으면 전체"));
        p.put("to", ToolInputs.dateProp("기록 날짜 끝 (포함). 없으면 전체"));
        p.put("k", Map.of("type", "integer", "minimum", 1, "maximum", MAX_K, "description", "결과 수 (기본 " + DEFAULT_K + ")"));
        return ToolInputs.schema(p, List.of("query"));
    }

    @Override
    public String execute(JsonNode input, long userId, LocalDate today) {
        String query = ToolInputs.text(input, "query");
        if (query == null) throw new IllegalArgumentException("query 는 필수입니다.");
        Set<String> sources = new LinkedHashSet<>();
        for (JsonNode s : input.path("sources")) {
            String v = s.asText().toLowerCase(Locale.ROOT);
            if (!NoteIndexService.SOURCES.contains(v)) {
                throw new IllegalArgumentException("알 수 없는 출처: " + v + " (가능: " + NoteIndexService.SOURCES + ")");
            }
            sources.add(v);
        }
        LocalDate from = input.hasNonNull("from") ? ToolInputs.date(input, "from", null) : null;
        LocalDate to = input.hasNonNull("to") ? ToolInputs.date(input, "to", null) : null;
        if (from != null && to != null) ToolInputs.requireOrder(from, to);
        int k = ToolInputs.intIn(input, "k", DEFAULT_K, 1, MAX_K);

        List<NoteIndexService.Hit> hits = index.search(userId, query, sources, from, to, k);
        StringBuilder sb = new StringBuilder("검색 \"" + query + "\": " + hits.size() + "건 (관련도순)");
        if (hits.isEmpty()) return sb.append("\n(기록 없음 — 색인이 비어 있거나 조건에 맞는 기록이 없음)").toString();
        for (NoteIndexService.Hit h : hits) {
            String body = h.text().length() > 1200 ? h.text().substring(0, 1200) + "…" : h.text();
            sb.append("\n\n[").append(h.source()).append(' ').append(h.date() == null ? "날짜 없음" : h.date())
                    .append(String.format(Locale.ROOT, ", 관련도 %.2f]\n", h.score()))
                    .append(body);
        }
        String out = sb.toString();
        return out.length() <= MAX_RESULT_CHARS ? out : out.substring(0, MAX_RESULT_CHARS) + "\n(길어서 잘림. k 를 줄여라)";
    }
}
