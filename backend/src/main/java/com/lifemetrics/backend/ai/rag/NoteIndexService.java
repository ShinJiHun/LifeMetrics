package com.lifemetrics.backend.ai.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifemetrics.backend.entity.AiAnalysis;
import com.lifemetrics.backend.entity.BodyGoal;
import com.lifemetrics.backend.entity.NoteChunk;
import com.lifemetrics.backend.entity.RandonneuringResult;
import com.lifemetrics.backend.health.entity.HealthTreatment;
import com.lifemetrics.backend.health.entity.HealthTreatmentVisit;
import com.lifemetrics.backend.health.repository.HealthTreatmentRepository;
import com.lifemetrics.backend.health.repository.HealthTreatmentVisitRepository;
import com.lifemetrics.backend.health.service.TreatmentVisitService;
import com.lifemetrics.backend.repository.AiAnalysisRepository;
import com.lifemetrics.backend.repository.BodyGoalRepository;
import com.lifemetrics.backend.repository.NoteChunkRepository;
import com.lifemetrics.backend.repository.RandonneuringResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 애슐리트 챗 RAG 색인·검색.
 * 숫자 기록은 조회 도구가 맡고, 여기서는 긴 텍스트(치료·진료, 과거 AI 분석, 목표·대회 메모)만 다룬다.
 * 원본 → 문서 → 조각(최대 CHUNK_CHARS) → (임베딩을 켰으면) 임베딩. 본문이 같은 조각은 다시 처리하지 않는다.
 * 검색은 사용자 조각 전체(수백 개)를 메모리에서 고른다:
 *   임베딩 켬  → 질문 벡터와 내적(정규화 벡터라 코사인과 같음)
 *   임베딩 끔  → 키워드 점수(단어 일치 + 한글 2글자 겹침). 외부 호출이 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoteIndexService {

    static final int CHUNK_CHARS = 1500;
    static final int OVERLAP_CHARS = 150;
    public static final Set<String> SOURCES = Set.of("treatment", "visit", "ai_analysis", "body_goal", "randonneuring");

    private final NoteChunkRepository chunkRepo;
    private final GeminiEmbeddingClient embedder;
    private final HealthTreatmentRepository treatmentRepo;
    private final HealthTreatmentVisitRepository visitRepo;
    private final AiAnalysisRepository analysisRepo;
    private final BodyGoalRepository goalRepo;
    private final RandonneuringResultRepository randoRepo;
    private final ObjectMapper objectMapper;

    /** 색인 대상 문서 하나. */
    record Doc(String source, long sourceId, LocalDate date, String title, String body) {}

    public record IndexResult(int documents, int chunks, int embedded, int unchanged, int deleted, int failed) {}

    public record Hit(String source, long sourceId, LocalDate date, String title, String text, double score) {}

    // ── 색인 ─────────────────────────────────────────

    /** 전체 원본을 다시 훑어 바뀐 조각만 임베딩하고, 사라진 조각은 지운다. 실패한 조각은 다음 색인 때 다시 시도된다. */
    public synchronized IndexResult reindex(long userId) {
        List<Doc> docs = collect(userId);
        Map<String, NoteChunk> existing = new HashMap<>();
        for (NoteChunk c : chunkRepo.findByUserId(userId)) existing.put(key(c.getSource(), c.getSourceId(), c.getChunkNo()), c);

        boolean useEmbedding = embedder.isConfigured();
        String model = useEmbedding ? embedder.model() : null;
        int chunks = 0, embedded = 0, unchanged = 0, failed = 0;
        for (Doc d : docs) {
            List<String> parts = chunk(d.body());
            for (int i = 0; i < parts.size(); i++) {
                chunks++;
                String text = d.title() + "\n" + parts.get(i);
                String hash = sha256((useEmbedding ? embedder.model() + "|" + embedder.dims() : "keyword") + "|" + text);
                NoteChunk c = existing.remove(key(d.source(), d.sourceId(), i));
                if (c != null && hash.equals(c.getContentHash())) {
                    unchanged++;
                    continue;
                }
                try {
                    float[] v = useEmbedding ? embedder.embed(text, GeminiEmbeddingClient.Task.RETRIEVAL_DOCUMENT) : null;
                    if (c == null) c = new NoteChunk();
                    c.setUserId(userId);
                    c.setSource(d.source());
                    c.setSourceId(d.sourceId());
                    c.setChunkNo(i);
                    c.setChunkDate(d.date());
                    c.setTitle(cut(d.title(), 200));
                    c.setText(text);
                    c.setContentHash(hash);
                    c.setModel(model);
                    c.setDims(v == null ? null : v.length);
                    c.setEmbedding(v == null ? null : toBytes(v));
                    chunkRepo.save(c);
                    embedded++;
                } catch (Exception e) {
                    failed++;
                    log.warn("RAG 색인 실패 {}:{}#{} - {}", d.source(), d.sourceId(), i, e.getMessage());
                }
            }
        }
        // 원본이 지워졌거나 조각 수가 줄어든 나머지
        chunkRepo.deleteAll(existing.values());
        log.info("RAG 색인({}): 문서 {} 조각 {} 갱신 {} 그대로 {} 삭제 {} 실패 {}", useEmbedding ? model : "키워드",
                docs.size(), chunks, embedded, unchanged, existing.size(), failed);
        return new IndexResult(docs.size(), chunks, embedded, unchanged, existing.size(), failed);
    }

    List<Doc> collect(long userId) {
        List<Doc> docs = new ArrayList<>();
        Map<Long, String> treatmentNames = new HashMap<>();
        for (HealthTreatment t : treatmentRepo.findAllForList(userId)) {
            treatmentNames.put(t.getId(), t.getName());
            docs.add(new Doc("treatment", t.getId(), t.getStartDate(), "치료·부상: " + t.getName(), lines(
                    "부위: " + nz(t.getBodyPart()),
                    "진단코드: " + nz(t.getDiagnosisCode()),
                    "기간: " + t.getStartDate() + " ~ " + (t.getEndDate() == null ? "치료 중" : t.getEndDate()),
                    "운동 제한·권고: " + nz(t.getRestrictions()),
                    "메모: " + nz(t.getNotes()))));
        }
        for (HealthTreatmentVisit v : visitRepo.findByUserIdOrderByVisitDateDescIdDesc(userId)) {
            List<String> items = new ArrayList<>();
            if (v.getItems() != null) for (Map<String, Object> it : v.getItems()) items.add("- " + TreatmentVisitService.itemLine(it));
            String treatment = treatmentNames.get(v.getTreatmentId());
            docs.add(new Doc("visit", v.getId(), v.getVisitDate(),
                    "진료 " + v.getVisitDate() + " " + nz(v.getInstitution()) + (treatment == null ? "" : " (" + treatment + ")"),
                    lines("진료과: " + nz(v.getDepartment()),
                            "문서: " + nz(v.getDocType()),
                            "상병: " + nz(v.getDiagnosis()),
                            "요약: " + nz(v.getSummary()),
                            items.isEmpty() ? "" : "항목:\n" + String.join("\n", items))));
        }
        for (AiAnalysis a : analysisRepo.findByUserId(userId)) {
            if (a.getAnalysisData() == null || a.getAnalysisData().isBlank()) continue;
            LocalDate date = a.getCreatedAt() == null ? null : a.getCreatedAt().toLocalDate();
            String target = a.getTargetPeriod() != null ? a.getTargetPeriod()
                    : a.getTargetId() != null ? "대상 " + a.getTargetId() : "";
            docs.add(new Doc("ai_analysis", a.getId(), date,
                    "과거 AI 분석 [" + a.getAnalysisType() + "] " + target, readable(a.getAnalysisData())));
        }
        for (BodyGoal g : goalRepo.findByUserIdOrderByStartDateDescIdDesc(userId)) {
            if (g.getNotes() == null || g.getNotes().isBlank()) continue;
            docs.add(new Doc("body_goal", g.getId(), g.getStartDate(),
                    "체성분 목표 " + g.getStartDate() + " ~ " + g.getTargetDate() + " (" + g.getStatus() + ")", g.getNotes()));
        }
        for (RandonneuringResult r : randoRepo.findByUserIdOrderByEventDateDescIdDesc(userId)) {
            if (r.getNotes() == null || r.getNotes().isBlank()) continue;
            docs.add(new Doc("randonneuring", r.getId(), r.getEventDate(),
                    "랜도너스 " + r.getEventDate() + " " + r.getEventName() + " " + r.getStatus(), r.getNotes()));
        }
        docs.removeIf(d -> d.body() == null || d.body().isBlank());
        return docs;
    }

    // ── 검색 ─────────────────────────────────────────

    /** 질문과 가까운 조각 k 개. sources·기간이 주어지면 그 안에서만 고른다. */
    public List<Hit> search(long userId, String query, Set<String> sources, LocalDate from, LocalDate to, int k) {
        List<NoteChunk> pool = chunkRepo.findByUserId(userId).stream()
                .filter(c -> sources == null || sources.isEmpty() || sources.contains(c.getSource()))
                .filter(c -> from == null || (c.getChunkDate() != null && !c.getChunkDate().isBefore(from)))
                .filter(c -> to == null || (c.getChunkDate() != null && !c.getChunkDate().isAfter(to)))
                .toList();
        boolean vectors = embedder.isConfigured() && pool.stream().anyMatch(c -> c.getEmbedding() != null);
        if (vectors) {
            float[] q = embedder.embed(query, GeminiEmbeddingClient.Task.RETRIEVAL_QUERY);
            return pool.stream()
                    .filter(c -> c.getEmbedding() != null && c.getDims() != null && c.getDims() == q.length)
                    .map(c -> hit(c, dot(q, fromBytes(c.getEmbedding()))))
                    .sorted(Comparator.comparingDouble(Hit::score).reversed())
                    .limit(k)
                    .toList();
        }
        Keywords q = Keywords.of(query);
        return pool.stream()
                .map(c -> hit(c, q.score(c.getText())))
                .filter(h -> h.score() > 0)
                .sorted(Comparator.comparingDouble(Hit::score).reversed())
                .limit(k)
                .toList();
    }

    /** 지금 검색 방식: "embedding" 또는 "keyword". */
    public String mode() {
        return embedder.isConfigured() ? "embedding" : "keyword";
    }

    private static Hit hit(NoteChunk c, double score) {
        return new Hit(c.getSource(), c.getSourceId(), c.getChunkDate(), c.getTitle(), c.getText(), score);
    }

    /**
     * 키워드 점수 = 0.7 × (질문 단어 중 본문에 있는 비율) + 0.3 × (질문의 2글자 조각 중 본문에 있는 비율).
     * 2글자 조각은 "무릎이/무릎을"처럼 조사가 붙은 한국어를 어느 정도 맞춘다. 0~1.
     */
    record Keywords(Set<String> terms, Set<String> grams) {
        static Keywords of(String query) {
            Set<String> terms = new java.util.LinkedHashSet<>();
            for (String t : normalize(query).split(" ")) if (t.length() >= 2) terms.add(t);
            return new Keywords(terms, bigrams(normalize(query)));
        }

        double score(String text) {
            if (terms.isEmpty() && grams.isEmpty()) return 0;
            String t = normalize(text);
            long termHits = terms.stream().filter(t::contains).count();
            Set<String> textGrams = bigrams(t);
            long gramHits = grams.stream().filter(textGrams::contains).count();
            double termPart = terms.isEmpty() ? 0 : (double) termHits / terms.size();
            double gramPart = grams.isEmpty() ? 0 : (double) gramHits / grams.size();
            return 0.7 * termPart + 0.3 * gramPart;
        }

        static String normalize(String s) {
            return s == null ? "" : s.toLowerCase(java.util.Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
        }

        static Set<String> bigrams(String s) {
            Set<String> out = new java.util.HashSet<>();
            for (String w : s.split(" ")) for (int i = 0; i + 2 <= w.length(); i++) out.add(w.substring(i, i + 2));
            return out;
        }
    }

    // ── 도우미 ───────────────────────────────────────

    /** 줄 경계에서 끊어 CHUNK_CHARS 이하로 나누고, 다음 조각에 OVERLAP_CHARS 만큼 앞 내용을 겹친다. */
    static List<String> chunk(String body) {
        String s = body.strip();
        List<String> out = new ArrayList<>();
        int start = 0;
        while (start < s.length()) {
            int end = Math.min(s.length(), start + CHUNK_CHARS);
            if (end < s.length()) {
                int nl = s.lastIndexOf('\n', end);
                if (nl > start + CHUNK_CHARS / 2) end = nl;
            }
            out.add(s.substring(start, end).strip());
            if (end >= s.length()) break;
            start = Math.max(end - OVERLAP_CHARS, start + 1);
        }
        return out;
    }

    /** AI 분석 JSON 을 "키: 값" 줄로 펼친다. JSON 이 아니면 그대로. */
    String readable(String raw) {
        try {
            JsonNode n = objectMapper.readTree(raw);
            if (!n.isContainerNode()) return raw;
            List<String> out = new ArrayList<>();
            flatten("", n, out);
            return String.join("\n", out);
        } catch (Exception e) {
            return raw;
        }
    }

    private static void flatten(String prefix, JsonNode n, List<String> out) {
        if (n.isObject()) {
            for (Iterator<Map.Entry<String, JsonNode>> it = n.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> e = it.next();
                flatten(prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey(), e.getValue(), out);
            }
        } else if (n.isArray()) {
            boolean scalars = true;
            for (JsonNode x : n) scalars &= x.isValueNode();
            if (scalars) {
                List<String> vals = new ArrayList<>();
                n.forEach(x -> vals.add(x.asText()));
                if (!vals.isEmpty()) out.add(prefix + ": " + String.join("; ", vals));
            } else {
                int i = 0;
                for (JsonNode x : n) flatten(prefix + "[" + i++ + "]", x, out);
            }
        } else if (!n.isNull() && !n.asText().isBlank()) {
            out.add(prefix + ": " + n.asText());
        }
    }

    static byte[] toBytes(float[] v) {
        ByteBuffer b = ByteBuffer.allocate(v.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float x : v) b.putFloat(x);
        return b.array();
    }

    static float[] fromBytes(byte[] bytes) {
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] v = new float[bytes.length / 4];
        for (int i = 0; i < v.length; i++) v[i] = b.getFloat();
        return v;
    }

    static double dot(float[] a, float[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++) s += (double) a[i] * b[i];
        return s;
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String key(String source, long sourceId, int chunkNo) {
        return source + ":" + sourceId + "#" + chunkNo;
    }

    /** 값이 없는 줄("메모: -")은 뺀다. */
    private static String lines(String... ls) {
        List<String> out = new ArrayList<>();
        for (String l : ls) if (l != null && !l.isBlank() && !l.endsWith(": -")) out.add(l);
        return String.join("\n", out);
    }

    private static String nz(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
