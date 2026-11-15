package com.lifemetrics.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** 생성자가 여러 개인 빈은 스프링이 만들 수 있는지 확인한다 (2026-11-15 dev 기동 실패 회귀 방지). */
class PublicChatRateLimiterWiringTest {

    @Test
    void 스프링_빈으로_생성된다() {
        new ApplicationContextRunner()
                .withBean(PublicChatRateLimiter.class)
                .run(ctx -> assertThat(ctx).hasNotFailed().hasSingleBean(PublicChatRateLimiter.class));
    }
}
