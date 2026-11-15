package com.lifemetrics.backend.persona.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifemetrics.backend.ai.rag.KeywordScorer;
import com.lifemetrics.backend.client.ClaudeChatClient;
import com.lifemetrics.backend.persona.entity.DevExperience;
import com.lifemetrics.backend.persona.repository.DevExperienceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 개발자 경험 카드: 관리·검색·초안 생성.
 * 검색은 외부 호출 없는 키워드 점수(KeywordScorer). 초안은 커밋 기록·메모를 Claude 에 주고 카드 JSON 을 받는다(저장은 사람이 검토 후).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DevExperienceService {

    static final double MIN_SCORE = 0.15;
    static final int DRAFT_MAX_INPUT = 30_000;

    private final DevExperienceRepository repo;
    private final ClaudeChatClient claude;
    private final ObjectMapper objectMapper;

    public List<DevExperience> list(boolean includePrivate) {
        return includePrivate ? repo.findAllByOrderByHappenedOnDescIdDesc()
                : repo.findByVisibilityOrderByHappenedOnDescIdDesc(DevExperience.PUBLIC);
    }

    public DevExperience save(DevExperience e) {
        validate(e);
        return repo.save(e);
    }

    public boolean delete(long id) {
        if (!repo.existsById(id)) return false;
        repo.deleteById(id);
        return true;
    }

    /** 질문과 관련된 공개 카드 k 개 (관련도순, 너무 약한 일치는 버림). 개발자 페르소나 챗이 쓴다. */
    public List<DevExperience> relevantPublic(String query, int k) {
        if (query == null || query.isBlank()) return List.of();
        KeywordScorer q = KeywordScorer.of(query);
        record Scored(DevExperience e, double s) {}
        return repo.findByVisibilityOrderByHappenedOnDescIdDesc(DevExperience.PUBLIC).stream()
                .map(e -> new Scored(e, q.score(e.asText())))
                .filter(x -> x.s() >= MIN_SCORE)
                .sorted(Comparator.comparingDouble(Scored::s).reversed())
                .limit(k)
                .map(Scored::e)
                .toList();
    }

    /**
     * 커밋 기록·작업 메모로 카드 초안을 만든다. 저장하지 않는다(화면에서 고친 뒤 저장).
     * 모두 PRIVATE 로 시작해 공개 여부는 사람이 정한다.
     */
    public List<DevExperience> draft(String project, String notes) {
        if (!claude.isConfigured()) throw new IllegalStateException("Claude API 키가 설정되지 않았습니다.");
        if (notes == null || notes.isBlank()) throw new IllegalArgumentException("커밋 기록이나 작업 메모를 넣어 주세요.");
        String input = notes.length() > DRAFT_MAX_INPUT ? notes.substring(0, DRAFT_MAX_INPUT) : notes;
        String reply = claude.chat(DRAFT_SYSTEM,
                        List.of(new ClaudeChatClient.Message("user", "프로젝트: " + project + "\n\n" + input)), 4000)
                .orElseThrow(() -> new IllegalStateException("초안을 만들지 못했습니다. 잠시 후 다시 시도해 주세요."));
        return parseDrafts(project, reply);
    }

    List<DevExperience> parseDrafts(String project, String reply) {
        String json = reply.strip();
        int s = json.indexOf('['), e = json.lastIndexOf(']');
        if (s < 0 || e <= s) throw new IllegalStateException("초안 형식을 읽지 못했습니다.");
        try {
            JsonNode arr = objectMapper.readTree(json.substring(s, e + 1));
            List<DevExperience> out = new ArrayList<>();
            for (JsonNode n : arr) {
                DevExperience d = new DevExperience();
                d.setProject(project);
                String date = text(n, "happenedOn");
                if (date != null && date.matches("\\d{4}-\\d{2}-\\d{2}")) d.setHappenedOn(LocalDate.parse(date));
                d.setTitle(text(n, "title"));
                d.setSituation(text(n, "situation"));
                d.setCause(text(n, "cause"));
                d.setDecision(text(n, "decision"));
                d.setResult(text(n, "result"));
                d.setLesson(text(n, "lesson"));
                d.setTags(text(n, "tags"));
                d.setSourceRef(text(n, "sourceRef"));
                d.setVisibility(DevExperience.PRIVATE);
                if (d.getTitle() != null && d.getSituation() != null) out.add(d);
            }
            return out;
        } catch (Exception ex) {
            throw new IllegalStateException("초안 형식을 읽지 못했습니다: " + ex.getMessage());
        }
    }

    static void validate(DevExperience e) {
        if (blank(e.getProject())) throw new IllegalArgumentException("프로젝트명은 필수입니다.");
        if (blank(e.getTitle())) throw new IllegalArgumentException("제목은 필수입니다.");
        if (blank(e.getSituation())) throw new IllegalArgumentException("상황·문제는 필수입니다.");
        String v = e.getVisibility() == null ? DevExperience.PRIVATE : e.getVisibility().toUpperCase(Locale.ROOT);
        if (!v.equals(DevExperience.PUBLIC) && !v.equals(DevExperience.PRIVATE)) {
            throw new IllegalArgumentException("공개 범위는 PUBLIC 또는 PRIVATE 입니다.");
        }
        e.setVisibility(v);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) return null;
        if (v.isArray()) {
            List<String> parts = new ArrayList<>();
            v.forEach(x -> parts.add(x.asText()));
            return String.join(",", parts);
        }
        String t = v.asText().strip();
        return t.isEmpty() ? null : t;
    }

    static final String DRAFT_SYSTEM = """
            당신은 개발자의 포트폴리오용 "경험 카드" 초안을 쓰는 편집자입니다.
            입력은 한 프로젝트의 커밋 기록과 작업 메모입니다. 면접에서 이야기할 만한 사건(장애·버그 해결, 설계 결정,
            성능·데이터 정합성 개선, 기술 선택과 트레이드오프)을 골라 사건 하나당 카드 하나로 정리하세요.

            규칙:
            - 입력에 있는 사실만 쓰세요. 숫자·원인·결과를 지어내지 말고, 입력에 없으면 그 필드는 null 로 두세요.
            - 단순 기능 추가나 문구 수정처럼 이야깃거리가 없는 커밋은 카드로 만들지 마세요. 많아야 8개.
            - 각 필드는 1~3문장, 한국어, 담백한 서술체(~했다).
            - 회사 내부 정보로 보이는 내용(고객명, 내부 시스템명, 매출 등)은 쓰지 마세요.
            - 출력은 JSON 배열만. 설명 문장이나 코드블록 없이.

            각 원소 형식:
            {"happenedOn":"YYYY-MM-DD 또는 null","title":"한 줄 제목","situation":"상황·문제","cause":"원인 분석",
             "decision":"결정과 그 이유(고려한 대안 포함)","result":"결과(가능하면 숫자)","lesson":"배운 점",
             "tags":"쉼표로 구분한 기술·역량 태그","sourceRef":"근거 커밋 해시 등"}
            """;

    /** 요청 본문 → 엔티티 (id 는 경로에서). */
    public static DevExperience fromMap(Map<String, Object> m, DevExperience target) {
        target.setProject(str(m.get("project")));
        Object d = m.get("happenedOn");
        target.setHappenedOn(d == null || d.toString().isBlank() ? null : LocalDate.parse(d.toString()));
        target.setTitle(str(m.get("title")));
        target.setSituation(str(m.get("situation")));
        target.setCause(str(m.get("cause")));
        target.setDecision(str(m.get("decision")));
        target.setResult(str(m.get("result")));
        target.setLesson(str(m.get("lesson")));
        target.setTags(str(m.get("tags")));
        target.setVisibility(str(m.get("visibility")));
        target.setSourceRef(str(m.get("sourceRef")));
        return target;
    }

    private static String str(Object o) {
        if (o == null) return null;
        String s = o.toString().strip();
        return s.isEmpty() ? null : s;
    }
}
