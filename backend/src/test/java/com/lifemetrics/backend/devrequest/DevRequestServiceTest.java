package com.lifemetrics.backend.devrequest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DevRequestServiceTest {

    @Test
    void 제목이_없으면_첫_줄로_만든다() {
        assertThat(DevRequestService.deriveTitle("\n\n  파워 커브에 90일 비교 추가  \n자세한 설명"))
                .isEqualTo("파워 커브에 90일 비교 추가");
    }

    @Test
    void 긴_첫_줄은_60자로_자른다() {
        String t = DevRequestService.deriveTitle("가".repeat(100));
        assertThat(t).hasSize(61).endsWith("…");
    }

    @Test
    void 로그는_뒤쪽만_남긴다() {
        String log = "a".repeat(10) + "b".repeat(60_000);
        String tail = DevRequestService.tail(log);
        assertThat(tail).startsWith("…\n").endsWith("b").doesNotContain("a");
        assertThat(DevRequestService.tail("짧음")).isEqualTo("짧음");
        assertThat(DevRequestService.tail(null)).isNull();
    }

    @Test
    void 개발_서버에_올라간_배포만_운영_반영할_수_있다() {
        assertThat(DevRequestService.canApprove("DEV_READY")).isTrue();
        assertThat(DevRequestService.canApprove("ANSWERED")).isFalse();
        assertThat(DevRequestService.canApprove("SUPERSEDED")).isFalse();
        assertThat(DevRequestService.canApprove("FAILED")).isFalse();
    }

    @Test
    void 대화_브랜치는_첫_메시지_id로_정한다() {
        assertThat(DevRequestService.branchOf(42)).isEqualTo("devreq/42");
    }

    @Test
    void 끝난_메시지에만_이어서_보낼_수_있다() {
        assertThat(DevRequestService.FOLLOWABLE).contains("DEV_READY", "ANSWERED", "FAILED", "DONE");
        assertThat(DevRequestService.FOLLOWABLE).doesNotContain("QUEUED", "RUNNING", "APPROVED", "PROMOTING", "SUPERSEDED");
    }
}
