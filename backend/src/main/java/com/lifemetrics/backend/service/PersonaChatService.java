package com.lifemetrics.backend.service;

import com.lifemetrics.backend.client.RunPodClient;
import com.lifemetrics.backend.dto.PersonaChatRequest;
import com.lifemetrics.backend.persona.entity.BlogPost;
import com.lifemetrics.backend.persona.entity.PersonaProfile;
import com.lifemetrics.backend.persona.repository.BlogPostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 블로그 글 기반 페르소나 챗. 임베딩 대신,
 * 캐시된 페르소나 프로필 + 질문과 키워드가 겹치는 상위 글 본문을 프롬프트에 주입한다.
 * 글 수가 적어(~50개) in-memory 점수화로 충분하다.
 *
 * LLM 호출 우선순위: 로컬 Ollama(개발 환경) → RunPod(클라우드 서버리스 Ollama, GCP prod 기본) → Gemini(Vertex, RunPod 실패 시 최종 폴백).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonaChatService {

    private final BlogPostRepository blogPostRepository;
    private final PersonaProfileService personaProfileService;
    private final GeminiService geminiService;          // ✅ ClaudeClient 대체
    private final OllamaService ollamaService;           // Gemini ADC 없는 로컬 개발용 대체
    private final RunPodClient runPodClient;              // 클라우드 서버리스 Ollama (RunPod)
    private final PersonaDocService personaDocService;
    private final CareerDataService careerDataService;

    private static final int MAX_HISTORY = 20;
    private static final int TOP_K = 4;
    private static final int PER_POST_CHARS = 1500;
    // thinking 모델(2.5/3.x Flash)은 내부 사고에도 출력 토큰을 소비하므로 여유있게.
    private static final int MAX_TOKENS = 2048;

    public String chat(List<PersonaChatRequest.Message> messages) {
        boolean useOllama = ollamaService.isAvailable();
        boolean useRunPod = !useOllama && runPodClient.isConfigured();
        boolean useGemini = !useOllama && !useRunPod && geminiService.hasApiKey();

        if (!useOllama && !useRunPod && !useGemini) {
            return "AI 서비스가 설정되지 않았습니다. (로컬 Ollama 미기동 / RunPod 미설정 / ADC-Vertex 프로젝트 확인 필요)";
        }
        if (messages == null || messages.isEmpty()) {
            return "메시지가 비어 있습니다.";
        }

        String lastUserMessage = "";
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equalsIgnoreCase(messages.get(i).getRole())) {
                lastUserMessage = messages.get(i).getContent() == null ? "" : messages.get(i).getContent();
                break;
            }
        }

        String systemPrompt = buildSystemPrompt(lastUserMessage);

        // 최근 MAX_HISTORY개만 사용해 GeminiService.ChatMessage 로 변환
        List<GeminiService.ChatMessage> history = new ArrayList<>();
        int start = Math.max(0, messages.size() - MAX_HISTORY);
        for (int i = start; i < messages.size(); i++) {
            PersonaChatRequest.Message m = messages.get(i);
            String content = m.getContent() == null ? "" : m.getContent();
            if (content.isBlank()) continue;
            // role 은 그대로 전달. assistant→model 매핑은 GeminiService 내부에서 처리.
            history.add(new GeminiService.ChatMessage(m.getRole(), content));
        }

        // Gemini는 contents가 user 턴으로 시작해야 한다. 앞에 붙은 assistant 턴은 제거.
        while (!history.isEmpty()
                && ("assistant".equalsIgnoreCase(history.get(0).role())
                || "model".equalsIgnoreCase(history.get(0).role()))) {
            history.remove(0);
        }
        if (history.isEmpty()) {
            return "질문 메시지가 없습니다.";
        }

        String reply;
        if (useOllama) {
            reply = ollamaService.chat(systemPrompt, history);
        } else if (useRunPod) {
            // RunPod(Ollama)은 단일 프롬프트 문자열만 받으므로 systemPrompt + 이력을 합친다.
            String combinedPrompt = buildCombinedPrompt(systemPrompt, history);
            try {
                reply = runPodClient.ask(combinedPrompt);
            } catch (Exception e) {
                log.warn("RunPod 호출 실패, Gemini로 폴백 시도: {}", e.getMessage());
                reply = geminiService.hasApiKey()
                        ? geminiService.chat(systemPrompt, history, MAX_TOKENS)
                        : null;
            }
        } else {
            reply = geminiService.chat(systemPrompt, history, MAX_TOKENS);
        }

        return (reply != null && !reply.isBlank()) ? reply
                : "죄송합니다. 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요.";
    }

    /** systemPrompt + 대화 이력을 RunPod(Ollama)에 보낼 단일 프롬프트 문자열로 합친다. */
    private String buildCombinedPrompt(String systemPrompt, List<GeminiService.ChatMessage> history) {
        StringBuilder sb = new StringBuilder();
        sb.append(systemPrompt).append("\n\n");
        for (GeminiService.ChatMessage m : history) {
            String role = "user".equalsIgnoreCase(m.role()) ? "사용자" : "어시스턴트";
            sb.append(role).append(": ").append(m.content()).append("\n");
        }
        sb.append("어시스턴트:");
        return sb.toString();
    }

    private String buildSystemPrompt(String query) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                당신은 신지훈의 "페르소나 비서"입니다. 아래 본인 문서(이력서·포트폴리오 등), 페르소나 프로필, 신지훈의 블로그 글 발췌를
                근거로 신지훈이 어떤 사람인지 방문자에게 친절하고 자연스럽게 설명하세요.
        
                [매우 중요] 반드시 순수 한국어로만 답변하세요. 한자, 중국어, 영어 단어를 단 한 글자도 섞지 마세요.
                한글 자모와 기본 문장부호만 사용하세요. 이 규칙을 어기면 안 됩니다.

                규칙:
                - 반드시 아래 제공된 정보(본인 문서/프로필/글 발췌)에 근거해서만 답하세요. 모르면 "그 내용은 자료에 없네요"라고 솔직히 말하세요.
                - 경력·직무·소속·프로젝트 같은 사실관계 질문은 이력서·포트폴리오를 우선 인용하세요. 관심사·취미·생각은 블로그 글을 인용하세요.
                - 재직 여부(현재 다니는 회사가 있는지), 각 회사의 퇴사/이직 사유, 구직 상황은 아래 "경력" 섹션이 유일한 근거입니다. "페르소나 프로필"이나 블로그 글에 이와 다른 내용이 있어도 "경력" 섹션을 따르세요.
                - 추측·과장·없는 사실 생성 금지.
                - 자연스러운 대화체(정중체 "~합니다/~네요"). JSON·마크다운 표 금지.
                - 답변은 보통 2~5문장으로 간결하게. 관련 블로그 글이 있으면 글 제목을 자연스럽게 언급해도 좋습니다.

                """);

        Optional<PersonaProfile> profile = personaProfileService.getLatest();
        if (profile.isPresent()) {
            sb.append("## 페르소나 프로필 (블로그 글로 생성 — 재직 여부·퇴사 사유·구직 상황은 아래 '경력'을 우선)\n")
                    .append(profile.get().getProfileText())
                    .append("\n\n");
        }

        if (personaDocService.isAnyLoaded()) {
            sb.append(personaDocService.getCombinedText());
        }

        List<BlogPost> relevant = topRelevantPosts(query);
        if (!relevant.isEmpty()) {
            sb.append("## 질문과 관련된 블로그 글 발췌\n");
            for (BlogPost p : relevant) {
                sb.append("### ").append(p.getTitle() == null ? "(제목 없음)" : p.getTitle()).append("\n");
                if (p.getUrl() != null) {
                    sb.append("(").append(p.getUrl()).append(")\n");
                }
                String c = p.getContent() == null ? "" : p.getContent();
                sb.append(c.length() > PER_POST_CHARS ? c.substring(0, PER_POST_CHARS) + "…" : c);
                sb.append("\n\n");
            }
        }

        // ── 경력은 가장 마지막에(질문 바로 앞) 둔다: 약한 모델이 앞 내용을 잊어도 이건 붙잡도록 ──
        if (careerDataService.isLoaded()) {
            sb.append("## 경력 (신지훈이 직접 관리하는 최신·정확 정보 — 재직 여부/퇴사 사유/구직 상황은 무조건 이 섹션만 근거로 삼는다)\n")
                    .append(careerDataService.getText())
                    .append("\n");
            sb.append("""
                    위 "경력" 섹션은 신지훈 본인이 작성한 사실이다. "각 회사 퇴사 사유", "왜 그만뒀어", "지금 어디 다녀",
                    "이직 준비 중이야" 같은 질문에는 위 섹션의 "퇴사/이직 사유"·"### 현재 상황" 내용을 그대로 근거로 답하라.
                    "내부 정보라 알 수 없다"거나 일반론으로 얼버무리지 마라 — 위 섹션에 이미 사유가 적혀 있다.

                    [재직 상태 — 매우 중요] "### 현재 상황"에 따르면 신지훈은 지금 재직 중인 회사가 없고 이직 준비 중이다.
                    어떤 회사에 대해서도 "현재 근무 중" / "재직 중"이라고 답하지 마라. 각 회사 항목의 "(퇴사)" 표시와
                    "### 현재 상황"이 사실이다. 프로젝트 설명에 "현재" 또는 현재형 문장("운영합니다" 등)이 있어도
                    그건 재직 당시에 쓴 글이니 재직 여부 판단에는 쓰지 마라.
                    민감할 수 있는 표현은 부드럽게 다듬되, 사실 자체는 위 섹션을 따른다.

                    """);
        }
        return sb.toString();
    }

    /** 질문 토큰이 제목/본문에 얼마나 겹치는지로 점수화해 상위 글을 고른다. */
    private List<BlogPost> topRelevantPosts(String query) {
        List<BlogPost> all = blogPostRepository.findAll();
        if (all.isEmpty()) return List.of();

        List<String> tokens = new ArrayList<>();
        for (String t : query.toLowerCase().split("[^0-9a-z가-힣]+")) {
            if (t.length() >= 2) tokens.add(t);
        }
        if (tokens.isEmpty()) {
            // 키워드가 없으면 최신 글 위주로 제공
            return all.stream()
                    .sorted(Comparator.comparing(
                            (BlogPost p) -> p.getPublishedAt(),
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(TOP_K)
                    .toList();
        }

        return all.stream()
                .map(p -> Map.entry(p, score(p, tokens)))
                .filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.<BlogPost, Integer>comparingByValue().reversed())
                .limit(TOP_K)
                .map(Map.Entry::getKey)
                .toList();
    }

    private int score(BlogPost p, List<String> tokens) {
        String title = p.getTitle() == null ? "" : p.getTitle().toLowerCase();
        String content = p.getContent() == null ? "" : p.getContent().toLowerCase();
        String tags = p.getCategories() == null ? "" : p.getCategories().toLowerCase();
        int s = 0;
        for (String tk : tokens) {
            if (title.contains(tk)) s += 5;
            if (tags.contains(tk)) s += 3;
            s += countOccurrences(content, tk);
        }
        return s;
    }

    private int countOccurrences(String haystack, String needle) {
        int count = 0, idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return Math.min(count, 5); // 한 글에서 과도한 가중 방지
    }
}