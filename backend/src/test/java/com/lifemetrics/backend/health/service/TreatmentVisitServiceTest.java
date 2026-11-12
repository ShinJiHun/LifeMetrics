package com.lifemetrics.backend.health.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifemetrics.backend.health.entity.HealthTreatment;
import com.lifemetrics.backend.health.entity.HealthTreatmentVisit;
import com.lifemetrics.backend.health.repository.HealthTreatmentRepository;
import com.lifemetrics.backend.health.repository.HealthTreatmentVisitRepository;
import com.lifemetrics.backend.service.ClaudeClient;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TreatmentVisitServiceTest {

    private final ClaudeClient claude = mock(ClaudeClient.class);
    private final HealthTreatmentRepository treatments = mock(HealthTreatmentRepository.class);
    private final HealthTreatmentVisitRepository visits = mock(HealthTreatmentVisitRepository.class);
    private final TreatmentVisitService service = new TreatmentVisitService(claude, new ObjectMapper(), treatments, visits);

    private static final byte[] PDF = {1, 2, 3};

    @Test
    void 세부내역서를_날짜별_진료로_나누고_항목을_정리한다() {
        when(claude.completeWithFile(anyString(), anyString(), eq(PDF), eq("application/pdf"), anyInt())).thenReturn("""
                {"isMedicalDocument": true, "docType": "진료비 세부내역서", "visits": [
                  {"date": "2026-11-10", "institution": "OO정형외과", "department": "정형외과",
                   "diagnosis": "장경인대 증후군 M76.3",
                   "items": [{"category": "물리치료", "name": "체외충격파", "amount": "50,000원"},
                             {"category": "이상한값", "name": "모름"},
                             {"category": "약", "name": null}],
                   "totalAmount": 62000, "patientAmount": "18,600"},
                  {"date": "2026-11-03", "institution": "OO정형외과",
                   "items": [{"category": "주사", "name": "트리암시놀론", "drugClass": "스테로이드", "days": 1},
                             {"category": "약", "name": "록소프로펜정", "timesPerDay": 3.0, "days": "5일"}]},
                  {"date": "날짜모름", "items": []}
                ]}
                """);

        List<HealthTreatmentVisit> result = service.extract(1L, "세부내역서.pdf", "application/pdf", PDF);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getVisitDate()).isEqualTo(LocalDate.of(2026, 11, 3)); // 날짜순
        assertThat(result.get(0).getItems().get(0)).doesNotContainKey("days");
        assertThat(result.get(0).getItems().get(1)).containsEntry("timesPerDay", 3).containsEntry("days", 5);
        HealthTreatmentVisit v = result.get(1);
        assertThat(v.getTreatmentId()).isEqualTo(1L);
        assertThat(v.getDocType()).isEqualTo("진료비 세부내역서");
        assertThat(v.getPatientAmount()).isEqualTo(18600);
        assertThat(v.getSourceFile()).isEqualTo("세부내역서.pdf");
        assertThat(v.getItems()).hasSize(2);
        assertThat(v.getItems().get(0)).containsEntry("amount", 50000);
        assertThat(v.getItems().get(1)).containsEntry("category", "기타");
    }

    @Test
    void 병원_문서가_아니면_거절한다() {
        when(claude.completeWithFile(anyString(), anyString(), any(), anyString(), anyInt()))
                .thenReturn("{\"isMedicalDocument\": false}");
        assertThatThrownBy(() -> service.extract(1L, "a.png", null, PDF)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 같은_날짜_기관_문서종류는_덮어쓴다() {
        HealthTreatment t = new HealthTreatment();
        t.setId(1L);
        t.setUserId(1L);
        when(treatments.findById(1L)).thenReturn(Optional.of(t));
        HealthTreatmentVisit old = visit(LocalDate.of(2026, 11, 10), "OO정형외과", "처방전");
        HealthTreatmentVisit otherDoc = visit(LocalDate.of(2026, 11, 10), "OO정형외과", "진료비 세부내역서");
        when(visits.findByTreatmentIdAndVisitDate(1L, LocalDate.of(2026, 11, 10))).thenReturn(List.of(old, otherDoc));
        when(visits.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<HealthTreatmentVisit> saved = service.save(1L, List.of(visit(LocalDate.of(2026, 11, 10), " OO정형외과 ", "처방전")));

        verify(visits).delete(old);
        verify(visits, never()).delete(otherDoc);
        assertThat(saved.get(0).getUserId()).isEqualTo(1L);
        assertThat(saved.get(0).getInstitution()).isEqualTo("OO정형외과");
    }

    @Test
    @SuppressWarnings("unchecked")
    void 분석에는_최근_14일_진료와_복용_기간_중인_약만_넣는다() {
        LocalDate day = LocalDate.of(2026, 11, 20);
        HealthTreatmentVisit recent = visit(LocalDate.of(2026, 11, 18), "OO정형외과", "진료비 세부내역서");
        recent.setItems(List.of(
                Map.of("category", "진찰", "name", "재진진찰료"),
                Map.of("category", "물리치료", "name", "체외충격파")));
        HealthTreatmentVisit oldMed = visit(LocalDate.of(2026, 10, 25), "OO약국", "약제비 영수증");
        oldMed.setItems(List.of(
                Map.of("category", "약", "name", "셀레콕시브캡슐", "dose", "1캡슐", "timesPerDay", 2, "days", 30, "drugClass", "소염진통제"),
                Map.of("category", "약", "name", "레바미피드정", "days", 7)));
        HealthTreatmentVisit oldPt = visit(LocalDate.of(2026, 10, 25), "OO정형외과", "진료비 세부내역서");
        oldPt.setItems(List.of(Map.of("category", "물리치료", "name", "간섭전류치료")));
        when(visits.findForAnalysis(any(), any(), any())).thenReturn(List.of(oldMed, oldPt, recent));

        List<Map<String, Object>> care = service.recentCareOf(List.of(1L), day).get(1L);

        assertThat(care).hasSize(2);
        assertThat(care.get(0)).containsEntry("daysAgo", 26L);
        assertThat((List<Object>) care.get(0).get("items"))
                .containsExactly("약: 셀레콕시브캡슐 1캡슐×2회 30일분 (소염진통제) — 이 날 복용 기간 중");
        assertThat((List<Object>) care.get(1).get("items")).containsExactly("물리치료: 체외충격파");
    }

    private static HealthTreatmentVisit visit(LocalDate date, String institution, String docType) {
        HealthTreatmentVisit v = new HealthTreatmentVisit();
        v.setTreatmentId(1L);
        v.setVisitDate(date);
        v.setInstitution(institution);
        v.setDocType(docType);
        v.setItems(List.of());
        return v;
    }
}
