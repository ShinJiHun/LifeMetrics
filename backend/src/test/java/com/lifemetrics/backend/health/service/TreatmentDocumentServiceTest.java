package com.lifemetrics.backend.health.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifemetrics.backend.health.entity.HealthTreatment;
import com.lifemetrics.backend.service.ClaudeClient;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TreatmentDocumentServiceTest {

    private final ClaudeClient claude = mock(ClaudeClient.class);
    private final TreatmentDocumentService service = new TreatmentDocumentService(claude, new ObjectMapper());

    private static final byte[] PDF = {1, 2, 3};

    @Test
    void 확장자로_파일_종류를_정하고_지원하지_않는_파일은_거절한다() {
        assertThat(TreatmentDocumentService.mediaTypeOf("진단서.PDF", "application/octet-stream")).isEqualTo("application/pdf");
        assertThat(TreatmentDocumentService.mediaTypeOf("IMG_0001.jpg", null)).isEqualTo("image/jpeg");
        assertThat(TreatmentDocumentService.mediaTypeOf("scan", "image/png")).isEqualTo("image/png");
        assertThat(TreatmentDocumentService.mediaTypeOf("진단서.hwp", "application/x-hwp")).isNull();

        assertThatThrownBy(() -> service.extract("진단서.hwp", null, PDF)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(claude);
    }

    @Test
    void 이미지는_5MB_를_넘으면_거절한다() {
        byte[] big = new byte[(int) TreatmentDocumentService.MAX_IMAGE_BYTES + 1];
        assertThatThrownBy(() -> service.extract("a.jpg", "image/jpeg", big)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(claude);
    }

    @Test
    void PDF_는_document_로_보내고_추출값과_원본_파일명을_채운다() {
        when(claude.completeWithFile(anyString(), anyString(), eq(PDF), eq("application/pdf"), anyInt())).thenReturn("""
                {"isMedicalDocument": true, "name": "장경인대 증후군", "diagnosisCode": "M76.3",
                 "bodyPart": "우측 슬관절 외측", "startDate": "2026-10-01", "endDate": null,
                 "restrictions": "향후 4주간 달리기 등 반복적 굴곡 운동 제한", "notes": "OO정형외과, 진단서"}
                """);

        HealthTreatment t = service.extract("진단서.pdf", "application/pdf", PDF);

        assertThat(t.getName()).isEqualTo("장경인대 증후군");
        assertThat(t.getDiagnosisCode()).isEqualTo("M76.3");
        assertThat(t.getBodyPart()).isEqualTo("우측 슬관절 외측");
        assertThat(t.getStartDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(t.getEndDate()).isNull();
        assertThat(t.getRestrictions()).contains("4주간");
        assertThat(t.getSourceFile()).isEqualTo("진단서.pdf");
        assertThat(t.getId()).isNull();
    }

    @Test
    void 코드펜스나_잘못된_날짜가_섞여도_읽을_수_있는_값은_살린다() {
        HealthTreatment t = service.parse("```json\n{\"name\": \"족저근막염\", \"startDate\": \"2026.10.01\", \"bodyPart\": \"null\"}\n```");

        assertThat(t.getName()).isEqualTo("족저근막염");
        assertThat(t.getStartDate()).isNull();
        assertThat(t.getBodyPart()).isNull();
    }

    @Test
    void 병원_문서가_아니거나_진단명이_없으면_알려준다() {
        assertThatThrownBy(() -> service.parse("{\"isMedicalDocument\": false}"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("병·의원 문서");
        assertThatThrownBy(() -> service.parse("{\"name\": null}"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("진단명");
        assertThatThrownBy(() -> service.parse("모르겠습니다"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void AI_호출이_실패하면_알려준다() {
        when(claude.completeWithFile(anyString(), anyString(), any(), anyString(), anyInt())).thenReturn(null);
        assertThatThrownBy(() -> service.extract("a.png", "image/png", PDF)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 프롬프트는_개인_식별_정보를_옮기지_말라고_지시한다() {
        assertThat(TreatmentDocumentService.SYSTEM_PROMPT).contains("주민등록번호").contains("옮기지 않는다");
    }
}
