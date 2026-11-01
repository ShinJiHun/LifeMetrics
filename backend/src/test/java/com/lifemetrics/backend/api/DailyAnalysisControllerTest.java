package com.lifemetrics.backend.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifemetrics.backend.security.AdminWriteFilter;
import com.lifemetrics.backend.service.DailyAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DailyAnalysisControllerTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 31);

    private final DailyAnalysisService service = mock(DailyAnalysisService.class);
    private final AdminWriteFilter admin = mock(AdminWriteFilter.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new DailyAnalysisController(service, admin)).build();
    }

    @Test
    void 관리자가_아니면_조회도_재분석도_막는다() throws Exception {
        mvc.perform(get("/api/ai/analysis/daily?date=2026-10-31")).andExpect(status().isForbidden());
        mvc.perform(post("/api/ai/analysis/daily/2026-10-31")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void 저장된_분석을_돌려준다() throws Exception {
        when(admin.isAdmin(any())).thenReturn(true);
        when(service.find(eq(1L), eq(DAY)))
                .thenReturn(Optional.of(new ObjectMapper().readTree("{\"result\":{\"summary\":\"좋음\"}}")));

        mvc.perform(get("/api/ai/analysis/daily?date=2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-10-31"))
                .andExpect(jsonPath("$.analysis.result.summary").value("좋음"));
    }

    @Test
    void 분석이_없으면_analysis_가_null_이다() throws Exception {
        when(admin.isAdmin(any())).thenReturn(true);
        when(service.find(eq(1L), eq(DAY))).thenReturn(Optional.empty());

        mvc.perform(get("/api/ai/analysis/daily?date=2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysis").doesNotExist());
    }

    @Test
    void AI_호출이_실패하면_502() throws Exception {
        when(admin.isAdmin(any())).thenReturn(true);
        when(service.analyzeNow(eq(1L), eq(DAY)))
                .thenThrow(new DailyAnalysisService.AnalysisFailedException("실패"));

        mvc.perform(post("/api/ai/analysis/daily/2026-10-31")).andExpect(status().isBadGateway());
    }
}
