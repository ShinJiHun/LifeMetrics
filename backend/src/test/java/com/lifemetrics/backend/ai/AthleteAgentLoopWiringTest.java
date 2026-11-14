package com.lifemetrics.backend.ai;

import com.lifemetrics.backend.ai.tools.AthleteToolRegistry;
import com.lifemetrics.backend.client.ClaudeChatClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** 스프링이 AthleteAgentLoop 를 만들 수 있는지 (생성자가 여러 개라 기동 실패했던 문제 회귀 방지). */
class AthleteAgentLoopWiringTest {

    @Test
    void 스프링_빈으로_생성된다() {
        new ApplicationContextRunner()
                .withBean(ClaudeChatClient.class, () -> mock(ClaudeChatClient.class))
                .withBean(AthleteToolRegistry.class, () -> new AthleteToolRegistry(List.of()))
                .withBean(AthleteAgentLoop.class)
                .run(ctx -> assertThat(ctx).hasNotFailed().hasSingleBean(AthleteAgentLoop.class));
    }
}
