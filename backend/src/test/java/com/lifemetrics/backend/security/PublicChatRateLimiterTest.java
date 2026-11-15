package com.lifemetrics.backend.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class PublicChatRateLimiterTest {

    /** 테스트 중 시각을 바꿀 수 있는 시계 */
    static class MutableClock extends Clock {
        Instant now;
        MutableClock(Instant now) { this.now = now; }
        public ZoneId getZone() { return ZoneOffset.ofHours(9); }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }

    @Test
    void IP_당_하루_한도와_전체_한도를_지키고_날짜가_바뀌면_초기화한다() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-15T03:00:00Z"));
        PublicChatRateLimiter limiter = new PublicChatRateLimiter(2, 3, clock);

        assertThat(limiter.tryAcquire("1.1.1.1")).isEqualTo(PublicChatRateLimiter.Result.OK);
        assertThat(limiter.tryAcquire("1.1.1.1")).isEqualTo(PublicChatRateLimiter.Result.OK);
        assertThat(limiter.tryAcquire("1.1.1.1")).isEqualTo(PublicChatRateLimiter.Result.IP_LIMIT);
        assertThat(limiter.tryAcquire("2.2.2.2")).isEqualTo(PublicChatRateLimiter.Result.OK);
        assertThat(limiter.tryAcquire("3.3.3.3")).isEqualTo(PublicChatRateLimiter.Result.TOTAL_LIMIT);

        clock.now = Instant.parse("2026-11-15T15:30:00Z"); // 한국 시각 11/16 00:30
        assertThat(limiter.tryAcquire("1.1.1.1")).isEqualTo(PublicChatRateLimiter.Result.OK);
        assertThat(PublicChatRateLimiter.message(PublicChatRateLimiter.Result.IP_LIMIT)).contains("내일");
    }
}
