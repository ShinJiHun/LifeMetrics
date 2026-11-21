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
    void 게시_본문_머리에_강연_정보_목록_빈칸은_뺀다() {
        var l = lecture("김연사", "14:00", "세종문화회관", null, "GMC 2회", List.of("확장된 표현형", "『이기적 유전자』"));
        assertThat(LectureService.postMarkdown(l)).isEqualTo("""
                - **연사**: 김연사
                - **일시**: 2026-03-09 14:00
                - **장소**: 세종문화회관
                - **시리즈**: GMC 2회
                - **관련 책**: 『확장된 표현형』, 『이기적 유전자』

                ## 핵심 주장
                내용""");
        assertThat(LectureService.postMarkdown(lecture(null, null, null, null, null, List.of())))
                .isEqualTo("- **일시**: 2026-03-09\n\n## 핵심 주장\n내용");
    }

    private static LectureService.Lecture lecture(String speaker, String time, String venue, String host, String series, List<String> books) {
        return new LectureService.Lecture(1, null, null, "manual", "2026-03-09", time, "제목", speaker, venue, host, series, books,
                null, "## 핵심 주장\n내용", List.of(), List.of(), "draft", false, null, "2026-11-22T10:00:00", null, List.of());
    }

    @Test
    void 자료_파일_종류와_저장_이름() {
        assertThat(LectureFileService.kindOf("GMC_001_Ku_Bongwon.MP3")).isEqualTo("audio");
        assertThat(LectureFileService.kindOf("받아쓰기.txt")).isEqualTo("text");
        assertThat(LectureFileService.kindOf("slide.HEIC")).isEqualTo("image");
        assertThat(LectureFileService.kindOf("자료.pdf")).isEqualTo("pdf");
        assertThat(LectureFileService.kindOf("x.docx")).isNull();
        assertThat(LectureFileService.safeName("../../etc/pass:wd.txt")).isEqualTo("pass_wd.txt");
        assertThat(LectureFileService.safeName(".hidden.md")).isEqualTo("_hidden.md");
    }

    @Test
    void 같은_메모의_요약을_연사_제목_순서로_짝짓는다() {
        var existing = List.of(new LectureService.Key("김진명", "내면의 힘"), new LectureService.Key("박태웅", "AI"),
                new LectureService.Key(null, "확장된 표현형"));
        var drafts = List.of(new LectureService.Key("박태웅", "AI 는 다른 지능"), new LectureService.Key("김 진명", null),
                new LectureService.Key("최재천", "확장된 표현형"), new LectureService.Key("김대식", "직업의 압축"));
        var m = LectureService.match(existing, drafts);
        assertThat(m).containsEntry(0, 1).containsEntry(1, 0).containsEntry(2, 2).doesNotContainKey(3);
    }

    @Test
    void 연사가_다르면_순서만으로_덮지_않는다() {
        var m = LectureService.match(List.of(new LectureService.Key("박태웅", "A")), List.of(new LectureService.Key("김대식", "B")));
        assertThat(m).isEmpty();
        var loose = LectureService.match(List.of(new LectureService.Key(null, "A")), List.of(new LectureService.Key("김대식", "B")));
        assertThat(loose).containsEntry(0, 0);
    }

    @Test
    void inbox_소유자_설정() {
        assertThat(DiaryService.parseOwner("1003:1006")).containsExactly(1003, 1006);
        assertThat(DiaryService.parseOwner("")).isNull();
        assertThat(DiaryService.parseOwner("jihoon")).isNull();
    }
}
