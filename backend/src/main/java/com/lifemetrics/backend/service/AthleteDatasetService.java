package com.lifemetrics.backend.service;

import com.lifemetrics.backend.client.ClaudeChatClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * 애슐리트 페르소나 파인튜닝 데이터셋 한 건 생성(data_pipeline persona/athlete_dataset/generate_dataset.py 가 호출).
 * 그 날짜 기준 컨텍스트로 시스템 프롬프트를 만들고 Claude 답변을 받아 그대로 돌려준다. DB 에 남기지 않는다.
 * <ul>
 *   <li>admin: 본인 전용 분석가 — {@link AthleteContextService#build} 전체 기록</li>
 *   <li>public: 방문자에게 신지훈을 소개하는 개인 비서 — {@link AthleteContextService#buildPublic} 공개용 기록만
 *       (진료·체성분·식단·신체 치수는 컨텍스트에서 아예 뺀다)</li>
 * </ul>
 * 프롬프트 원본: data_pipeline persona/athlete_dataset/prompts.md. 바꾸면 그쪽 문서도 맞춰 주세요.
 */
@Service
public class AthleteDatasetService {

    public static final String PUBLIC = "public";
    public static final String ADMIN = "admin";
    static final int MAX_TOKENS = 1500;
    static final int MAX_QUESTION = 2000;

    private final AthleteContextService contextService;
    private final ClaudeChatClient claude;

    public AthleteDatasetService(AthleteContextService contextService, ClaudeChatClient claude) {
        this.contextService = contextService;
        this.claude = claude;
    }

    public record Sample(String mode, String date, String system, String answer, String model) {
    }

    public Sample sample(String mode, LocalDate date, String question) {
        String m = normalizeMode(mode);
        String q = question == null ? "" : question.strip();
        if (q.isEmpty()) throw new IllegalArgumentException("question 이 비어 있습니다.");
        if (q.length() > MAX_QUESTION) throw new IllegalArgumentException("question 이 너무 깁니다.");
        if (date == null) throw new IllegalArgumentException("date 가 필요합니다(YYYY-MM-DD).");
        if (!claude.isConfigured()) throw new IllegalStateException("Claude API 키(anthropic.api-key)가 설정되지 않았습니다.");

        String system = systemPrompt(m, PUBLIC.equals(m)
                ? contextService.buildPublic(AthleteChatService.USER_ID, date)
                : contextService.build(AthleteChatService.USER_ID, date));
        String answer = claude.chat(system, List.of(new ClaudeChatClient.Message("user", q)), MAX_TOKENS)
                .map(String::strip).filter(s -> !s.isEmpty())
                .orElseThrow(() -> new IllegalStateException("Claude 답변을 받지 못했습니다."));
        return new Sample(m, date.toString(), system, answer, claude.model());
    }

    static String normalizeMode(String mode) {
        String m = mode == null ? "" : mode.strip().toLowerCase(Locale.ROOT);
        if (!PUBLIC.equals(m) && !ADMIN.equals(m)) throw new IllegalArgumentException("mode 는 public 또는 admin 입니다.");
        return m;
    }

    static String systemPrompt(String mode, String context) {
        return (PUBLIC.equals(mode) ? PUBLIC_PROMPT : ADMIN_PROMPT) + context;
    }

    static final String PUBLIC_PROMPT = """
            당신은 신지훈의 개인 비서입니다. 신지훈을 궁금해하는 방문자에게 그가 어떤 사람인지 소개합니다.
            신지훈은 랜도너스(브레베 200~1000km 장거리 자전거)를 타고, 웨이트 트레이닝을 병행하며 몸을 만들고 있는 사람입니다.

            규칙:
            - 신지훈을 3인칭("지훈 님")으로 부르고, 정중하고 따뜻한 비서 말투로 답하세요. 자랑할 만한 기록은 자연스럽게 강조해도 됩니다.
            - 아래 공개 기록에 있는 사실만 말하세요. 없는 내용은 "그 부분은 제가 알고 있는 정보가 없습니다"라고 답하세요.
            - 건강·진료·체중·식단·신체 치수 같은 개인 정보는 묻더라도 "개인적인 부분이라 말씀드리기 어렵다"고 정중히 넘기세요.
            - 연락처·주소·일정처럼 사람을 특정하거나 찾아갈 수 있는 정보는 말하지 마세요.
            - 보통 2~5문장. 표와 코드블록은 쓰지 마세요.

            # 공개 기록
            """;

    static final String ADMIN_PROMPT = """
            당신은 신지훈 본인의 개인 분석가입니다. 지금 대화하는 사람은 신지훈 본인이며, 자기 기록을 바탕으로 냉정한 분석을 원합니다.
            신지훈은 랜도너스(브레베 200~1000km 장거리 자전거)를 타고 웨이트 트레이닝을 병행하며 체지방 감량을 하고 있습니다.

            규칙:
            - 2인칭으로, 결론부터 말하고 근거 수치를 붙이세요. 듣기 좋은 말보다 정확한 분석이 우선입니다.
            - 수치는 아래 기록에 있는 것만 쓰세요. 기록에 없으면 없다고 말하고, 추정할 때는 추정이라고 밝히세요.
            - 긴 라이딩(10시간 이상, 1만kcal 이상)도 실제 대회 기록일 수 있으니 이상치로 취급하지 마세요.
            - 식단 기록은 끼니가 빠진 날이 많습니다. "빠진 끼니"가 있는 날의 섭취·수지는 실제 하루 섭취가 아닙니다.
            - 소모 칼로리는 기초대사량 + 활동 추정치라 오차가 있습니다. 하루 단위보다 며칠 흐름으로 판단하세요.
            - 치료·부상과 진료·처방 내역이 있으면 반영하세요. 약의 효과·부작용 판단은 의사·약사에게 확인하라고 안내하세요.
            - 의학적 진단은 하지 말고, 극단적인 감량(하루 1000kcal 이상 적자 지속 등)은 권하지 마세요.
            - 보통 3~8문장. 계획을 물으면 짧은 목록을 써도 됩니다. 표와 코드블록은 쓰지 마세요.

            # 신지훈의 기록
            """;
}
