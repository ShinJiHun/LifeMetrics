package com.lifemetrics.backend.persona.diary;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LectureTagTest {

    @Test
    void 태그_정규화() {
        assertThat(LectureService.normalizeTag("  #AI ")).isEqualTo("AI");
        assertThat(LectureService.normalizeTag("##자기  결정\t이론")).isEqualTo("자기 결정 이론");
        assertThat(LectureService.normalizeTag("a,b")).isEqualTo("ab");
        assertThat(LectureService.normalizeTag(" # ")).isNull();
        assertThat(LectureService.normalizeTag(null)).isNull();
        assertThat(LectureService.normalizeTag("x".repeat(100))).hasSize(LectureService.MAX_TAG_LENGTH);
    }

    @Test
    void 대소문자만_다른_중복은_먼저_나온_표기로() {
        assertThat(LectureService.normalizeTags(List.of("AI", "ai", "#인간다움", "인간다움", " ")))
                .containsExactly("AI", "인간다움");
    }

    @Test
    void 게시_본문에_연사와_날짜() {
        var l = new LectureService.Lecture(1, 2L, null, "2026-11-20", "제목", "김연사", "## 핵심 주장\n내용",
                List.of(), List.of(), "draft", false, null, "2026-11-21T01:10:00", null);
        assertThat(LectureService.postMarkdown(l)).startsWith("*연사: 김연사 · 2026-11-20*\n\n## 핵심 주장");
    }

    @Test
    void inbox_소유자_설정() {
        assertThat(DiaryService.parseOwner("1003:1006")).containsExactly(1003, 1006);
        assertThat(DiaryService.parseOwner("")).isNull();
        assertThat(DiaryService.parseOwner("jihoon")).isNull();
    }
}
