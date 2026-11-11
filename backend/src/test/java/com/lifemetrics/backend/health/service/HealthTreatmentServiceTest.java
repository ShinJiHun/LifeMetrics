package com.lifemetrics.backend.health.service;

import com.lifemetrics.backend.health.entity.HealthTreatment;
import com.lifemetrics.backend.health.repository.HealthTreatmentRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HealthTreatmentServiceTest {

    private static final LocalDate START = LocalDate.of(2026, 10, 1);

    private final HealthTreatmentRepository repo = mock(HealthTreatmentRepository.class);
    private final HealthTreatmentService service = new HealthTreatmentService(repo);

    private static HealthTreatment treatment(String name, LocalDate start, LocalDate end) {
        HealthTreatment t = new HealthTreatment();
        t.setName(name);
        t.setStartDate(start);
        t.setEndDate(end);
        return t;
    }

    @Test
    void 프롬프트_블록은_치료명_경과일_제한을_담고_해석_지시를_붙인다() {
        HealthTreatment t = treatment("장경인대 증후군", START, null);
        t.setBodyPart("오른쪽 무릎 바깥쪽");
        t.setDiagnosisCode("M76.3");
        t.setRestrictions("하체 고중량·러닝 자제");

        String block = HealthTreatmentService.promptBlock(List.of(t), LocalDate.of(2026, 11, 11));

        assertThat(block)
                .contains("장경인대 증후군 [M76.3] (오른쪽 무릎 바깥쪽)")
                .contains("2026-10-01부터 치료 중, 41일째")
                .contains("운동 제한·권고: 하체 고중량·러닝 자제")
                .contains("적절한 조절");
    }

    @Test
    void 치료가_없으면_프롬프트_블록은_비어_있다() {
        assertThat(HealthTreatmentService.promptBlock(List.of(), START)).isEmpty();
    }

    @Test
    void 이름이나_시작일이_없으면_저장하지_않는다() {
        assertThatThrownBy(() -> service.create(1L, treatment(" ", START, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(1L, treatment("장경인대", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void 종료일이_시작일보다_빠르면_저장하지_않는다() {
        assertThatThrownBy(() -> service.create(1L, treatment("장경인대", START, START.minusDays(1))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 저장할_때_공백은_정리하고_빈_값은_null_로_둔다() {
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        HealthTreatment req = treatment("  장경인대 증후군 ", START, null);
        req.setRestrictions("   ");

        HealthTreatment saved = service.create(1L, req);

        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getName()).isEqualTo("장경인대 증후군");
        assertThat(saved.getRestrictions()).isNull();
    }

    @Test
    void 없는_기록은_수정_삭제하지_않는다() {
        when(repo.findById(9L)).thenReturn(Optional.empty());
        assertThat(service.update(9L, treatment("x", START, null))).isEmpty();
        assertThat(service.delete(9L)).isFalse();
        verify(repo, never()).deleteById(any());
    }
}
