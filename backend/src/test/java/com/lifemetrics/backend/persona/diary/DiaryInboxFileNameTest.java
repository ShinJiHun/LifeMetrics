package com.lifemetrics.backend.persona.diary;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiaryInboxFileNameTest {

    @Test
    void 출처와_맞는_이름은_그대로() {
        assertThat(DiaryService.inboxFileName("app-2026-11-20-회사.md", "app")).isEqualTo("app-2026-11-20-회사.md");
        assertThat(DiaryService.inboxFileName("cli-0130-일기.md", "cli")).isEqualTo("cli-0130-일기.md");
    }

    @Test
    void 출처가_다르게_잡히는_이름은_출처를_앞에_붙여_폴더_재스캔_중복을_막는다() {
        String name = DiaryService.inboxFileName("오늘 정리.md", "app");
        assertThat(name).isEqualTo("app-오늘 정리.md");
        assertThat(DiaryService.folderSource(name)).isEqualTo("app");
        assertThat(DiaryService.folderSource(DiaryService.inboxFileName("memo.txt", "manual"))).isEqualTo("manual");
    }

    @Test
    void 경로_문자와_확장자를_정리한다() {
        assertThat(DiaryService.inboxFileName("../../etc/passwd", "cli")).isEqualTo("passwd.md");
        assertThat(DiaryService.inboxFileName("C:\\x\\app-a.md", "app")).isEqualTo("app-a.md");
        assertThat(DiaryService.inboxFileName(".hidden.md", "cli")).isEqualTo("_hidden.md");
        assertThat(DiaryService.inboxFileName("  ", "app")).isNull();
        assertThat(DiaryService.inboxFileName(null, "app")).isNull();
    }
}
