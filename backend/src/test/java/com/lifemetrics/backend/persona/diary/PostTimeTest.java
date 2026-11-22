package com.lifemetrics.backend.persona.diary;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 블로그 글 날짜는 게시한 날이 아니라 강연·일기의 날짜. */
class PostTimeTest {

    @Test
    void 강연_글은_강연_날짜와_시작_시각() {
        assertThat(LectureService.postTime("2026-11-21", "19:00~21:00")).isEqualTo(LocalDateTime.of(2026, 11, 21, 19, 0));
        assertThat(LectureService.postTime("2026-03-07", " 9:30")).isEqualTo(LocalDateTime.of(2026, 3, 7, 9, 30));
    }

    @Test
    void 강연_시각이_없거나_못_읽으면_정오() {
        assertThat(LectureService.postTime("2026-11-21", null)).isEqualTo(LocalDateTime.of(2026, 11, 21, 12, 0));
        assertThat(LectureService.postTime("2026-11-21", "저녁")).isEqualTo(LocalDateTime.of(2026, 11, 21, 12, 0));
        assertThat(LectureService.postTime("2026-11-21", "25:00")).isEqualTo(LocalDateTime.of(2026, 11, 21, 12, 0));
    }

    @Test
    void 일기는_그날_게시하면_게시_시각_다음날_이후면_그날_23시59분() {
        LocalDateTime sameDay = LocalDateTime.of(2026, 11, 21, 22, 30);
        assertThat(DiaryService.postTime("2026-11-21", sameDay)).isEqualTo(sameDay);
        assertThat(DiaryService.postTime("2026-11-21", LocalDateTime.of(2026, 11, 22, 0, 40)))
                .isEqualTo(LocalDateTime.of(2026, 11, 21, 23, 59));
    }
}
