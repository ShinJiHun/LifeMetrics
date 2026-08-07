package com.lifemetrics.backend.service;

import com.lifemetrics.backend.dto.PersonaChatRequest;
import com.lifemetrics.backend.persona.entity.BlogPost;
import com.lifemetrics.backend.persona.entity.PersonaProfile;
import com.lifemetrics.backend.persona.repository.BlogPostRepository;
import lombok.RequiredArgsConstructor;
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
 * LLM 호출은 Vertex(Gemini). 개인 문서/블로그를 다루므로 입력을 학습에 쓰지 않는 Vertex가 적합.
 */
@Service
@RequiredArgsConstructor
public class PersonaChatService {

    private final BlogPostRepository blogPostRepository;
    private final PersonaProfileService personaProfileService;
    private final GeminiService geminiService;          // ✅ ClaudeClient 대체
    private final OllamaService ollamaService;           // Gemini ADC 없는 로컬 개발용 대체
    private final PersonaDocService personaDocService;

    private static final int MAX_HISTORY = 20;
    private static final int TOP_K = 4;
    private static final int PER_POST_CHARS = 1500;
    // thinking 모델(2.5/3.x Flash)은 내부 사고에도 출력 토큰을 소비하므로 여유있게.
    private static final int MAX_TOKENS = 2048;

    public String chat(List<PersonaChatRequest.Message> messages) {
        boolean useGemini = geminiService.hasApiKey();
        boolean useOllama = !useGemini && ollamaService.isAvailable();
        if (!useGemini && !useOllama) {
            return "AI 서비스가 설정되지 않았습니다. (ADC / Vertex 프로젝트 확인 필요, 또는 로컬 Ollama 미기동)";
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

        String reply = useGemini
                ? geminiService.chat(systemPrompt, history, MAX_TOKENS)
                : ollamaService.chat(systemPrompt, history);
        return (reply != null && !reply.isBlank()) ? reply
                : "죄송합니다. 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요.";
    }

    private String buildSystemPrompt(String query) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                당신은 신지훈의 "페르소나 비서"입니다. 아래 본인 문서(이력서·포트폴리오 등), 페르소나 프로필, 신지훈의 블로그 글 발췌를
                근거로 신지훈이 어떤 사람인지 방문자에게 친절하고 자연스럽게 설명하세요.

                규칙:
                - 반드시 아래 제공된 정보(본인 문서/프로필/글 발췌)에 근거해서만 답하세요. 모르면 "그 내용은 자료에 없네요"라고 솔직히 말하세요.
                - 경력·직무·소속·프로젝트 같은 사실관계 질문은 이력서·포트폴리오를 우선 인용하세요. 관심사·취미·생각은 블로그 글을 인용하세요.
                - 추측·과장·없는 사실 생성 금지.
                - 자연스러운 대화체(정중체 "~합니다/~네요"). JSON·마크다운 표 금지.
                - 답변은 보통 2~5문장으로 간결하게. 관련 블로그 글이 있으면 글 제목을 자연스럽게 언급해도 좋습니다.

                """);

        if (personaDocService.isAnyLoaded()) {
            sb.append(personaDocService.getCombinedText());
        }

        Optional<PersonaProfile> profile = personaProfileService.getLatest();
        if (profile.isPresent()) {
            sb.append("## 페르소나 프로필\n")
                    .append(profile.get().getProfileText())
                    .append("\n\n");
        } else {
            sb.append("## 페르소나 프로필\n(아직 생성되지 않음 - /api/persona/refresh 필요)\n\n");
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
