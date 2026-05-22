package com.lifemetrics.backend.service;

import com.lifemetrics.backend.entity.BlogPost;
import com.lifemetrics.backend.entity.PersonaProfile;
import com.lifemetrics.backend.repository.BlogPostRepository;
import com.lifemetrics.backend.repository.PersonaProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 적재된 블로그 글 전체를 Claude 로 1회 요약해 "페르소나 프로필"을 생성·캐시한다.
 * 글 갱신 시 재생성하며, 챗 단계에서 매 질문마다 이 프로필을 컨텍스트로 주입한다.
 */
@Service
@RequiredArgsConstructor
public class PersonaProfileService {

    private final BlogPostRepository blogPostRepository;
    private final PersonaProfileRepository personaProfileRepository;
    private final ClaudeClient claudeClient;
    private final PersonaDocService personaDocService;

    /** 프로필 생성 시 글 1개당 프롬프트에 넣을 본문 최대 길이. */
    private static final int PER_POST_CHARS = 1200;
    private static final int MAX_TOKENS = 2000;

    public Optional<PersonaProfile> getLatest() {
        return personaProfileRepository.findTopByOrderByGeneratedAtDesc();
    }

    /** 전 글을 요약해 페르소나 프로필을 새로 생성하고 저장한다. 성공 시 true. */
    @Transactional
    public boolean regenerate() {
        List<BlogPost> posts = blogPostRepository.findAllByOrderByPublishedAtDesc();
        if (posts.isEmpty()) {
            System.out.println("⚠️ [PersonaProfile] 적재된 글이 없어 프로필 생성 건너뜀");
            return false;
        }
        if (!claudeClient.hasApiKey()) {
            System.out.println("⚠️ [PersonaProfile] anthropic.api-key 없음 - 프로필 생성 불가");
            return false;
        }

        StringBuilder corpus = new StringBuilder();
        if (personaDocService.isAnyLoaded()) {
            corpus.append(personaDocService.getCombinedText())
                  .append("## 블로그 글 모음\n\n");
        }
        for (BlogPost p : posts) {
            corpus.append("### ").append(p.getTitle() == null ? "(제목 없음)" : p.getTitle()).append("\n");
            if (p.getCategories() != null) {
                corpus.append("태그: ").append(p.getCategories()).append("\n");
            }
            String c = p.getContent() == null ? "" : p.getContent();
            corpus.append(c.length() > PER_POST_CHARS ? c.substring(0, PER_POST_CHARS) + "…" : c);
            corpus.append("\n\n");
        }

        String systemPrompt = """
                당신은 한 사람의 이력서·포트폴리오와 블로그 글 전체를 읽고 그 사람이 어떤 사람인지 정리하는 분석가입니다.
                아래 자료를 근거로, 본인을 설명하는 "페르소나 프로필"을 작성하세요.

                다음 항목을 마크다운 소제목으로 구성하세요:
                - 한 줄 소개
                - 경력 / 직무 (이력서·포트폴리오 우선, 블로그 보조)
                - 주요 관심사 / 전문 분야
                - 기술 스택 / 학습 이력
                - 대표 프로젝트 / 작업물 (포트폴리오 기반)
                - 취미 / 라이프스타일 (운동, 자전거, 여행, 독서 등)
                - 글쓰기 톤·성격이 드러나는 특징
                - 대표적인 글 주제 5개 내외

                이력서·포트폴리오에 명시된 사실(회사·기간·직무·프로젝트)은 우선 신뢰하되, 블로그 글과 모순되면 둘 다 언급하세요.
                반드시 자료에 실제로 나타난 내용만 쓰고, 추측·과장하지 마세요. 한국어로 작성하세요.
                """;

        String userText = "다음은 글쓴이의 자료입니다 (본인 문서 "
                + (personaDocService.isAnyLoaded() ? "포함" : "없음")
                + ", 블로그 글 " + posts.size() + "개).\n\n" + corpus;

        String profileText = claudeClient.complete(systemPrompt, userText, MAX_TOKENS);
        if (profileText == null || profileText.isBlank()) {
            System.out.println("❌ [PersonaProfile] Claude 응답 비어있음 - 생성 실패");
            return false;
        }

        // 최신 1건만 유효하게 사용하므로 기존 프로필은 정리
        personaProfileRepository.deleteAll();
        PersonaProfile profile = new PersonaProfile();
        profile.setProfileText(profileText.trim());
        profile.setPostCount(posts.size());
        personaProfileRepository.save(profile);
        System.out.println("✅ [PersonaProfile] 프로필 생성 완료 (글 " + posts.size() + "개 기반)");
        return true;
    }
}
