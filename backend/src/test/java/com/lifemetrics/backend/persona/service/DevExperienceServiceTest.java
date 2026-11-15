package com.lifemetrics.backend.persona.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifemetrics.backend.client.ClaudeChatClient;
import com.lifemetrics.backend.persona.entity.DevExperience;
import com.lifemetrics.backend.persona.repository.DevExperienceRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DevExperienceServiceTest {

    private final DevExperienceRepository repo = mock(DevExperienceRepository.class);
    private final ClaudeChatClient claude = mock(ClaudeChatClient.class);
    private final DevExperienceService service = new DevExperienceService(repo, claude, new ObjectMapper());

    private static DevExperience card(String title, String situation, String tags, String visibility) {
        DevExperience e = new DevExperience();
        e.setProject("LifeMetrics");
        e.setHappenedOn(LocalDate.of(2026, 11, 14));
        e.setTitle(title);
        e.setSituation(situation);
        e.setTags(tags);
        e.setVisibility(visibility);
        return e;
    }

    @Test
    void 관련_공개_카드를_관련도순으로_고르고_약한_일치는_버린다() {
        DevExperience deploy = card("배포 직후 기동 실패", "dev 배포가 헬스체크에서 실패해 자동 롤백됐다", "Spring,배포,장애 대응", "PUBLIC");
        DevExperience data = card("랜도너스 기록 합계 1km 오차", "공식 합산 거리와 DB 합계가 맞지 않았다", "데이터 정합성", "PUBLIC");
        when(repo.findByVisibilityOrderByHappenedOnDescIdDesc("PUBLIC")).thenReturn(List.of(data, deploy));

        assertThat(service.relevantPublic("배포하다 장애 난 경험 있어?", 3)).containsExactly(deploy);
        assertThat(service.relevantPublic("요리 좋아해?", 3)).isEmpty();
        assertThat(service.relevantPublic(" ", 3)).isEmpty();
    }

    @Test
    void 카드는_상황_원인_결정_결과_순서의_한_덩어리로_펼친다() {
        DevExperience e = card("배포 직후 기동 실패", "롤백됐다", "Spring", "PUBLIC");
        e.setCause("생성자가 2개");
        e.setResult(" ");
        assertThat(e.asText()).isEqualTo("[LifeMetrics 2026-11-14] 배포 직후 기동 실패\n상황: 롤백됐다\n원인: 생성자가 2개\n태그: Spring");
    }

    @Test
    void 초안은_JSON_배열만_골라_읽고_필수값_없는_항목은_버리며_비공개로_시작한다() {
        when(claude.isConfigured()).thenReturn(true);
        when(claude.chat(anyString(), anyList(), anyInt())).thenReturn(Optional.of("""
                초안입니다.
                ```json
                [{"happenedOn":"2026-11-14","title":"기동 실패","situation":"롤백","cause":null,
                  "tags":["Spring","배포"],"sourceRef":"a1b2c3"},
                 {"title":"상황 없음"},
                 {"happenedOn":"모름","title":"답변 잘림","situation":"max_tokens"}]
                ```"""));

        List<DevExperience> drafts = service.draft("LifeMetrics", "a1b2c3 fix: ...");

        assertThat(drafts).extracting(DevExperience::getTitle).containsExactly("기동 실패", "답변 잘림");
        assertThat(drafts.get(0).getTags()).isEqualTo("Spring,배포");
        assertThat(drafts.get(0).getCause()).isNull();
        assertThat(drafts.get(1).getHappenedOn()).isNull();
        assertThat(drafts).allSatisfy(d -> {
            assertThat(d.getVisibility()).isEqualTo("PRIVATE");
            assertThat(d.getProject()).isEqualTo("LifeMetrics");
        });
    }

    @Test
    void 저장_전에_필수값과_공개_범위를_검사한다() {
        DevExperience ok = DevExperienceService.fromMap(Map.of("project", "LifeMetrics", "title", "t", "situation", "s",
                "visibility", "public", "happenedOn", "2026-11-14"), new DevExperience());
        DevExperienceService.validate(ok);
        assertThat(ok.getVisibility()).isEqualTo("PUBLIC");

        assertThatThrownBy(() -> DevExperienceService.validate(card(" ", "s", null, "PUBLIC"))).hasMessageContaining("제목");
        assertThatThrownBy(() -> DevExperienceService.validate(card("t", "s", null, "FRIENDS"))).hasMessageContaining("PUBLIC 또는 PRIVATE");
        assertThatThrownBy(() -> service.draft("p", " ")).isInstanceOf(IllegalStateException.class);
    }
}
