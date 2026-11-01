package com.lifemetrics.backend.health.controller;

import com.lifemetrics.backend.health.dto.HealthDataDto.DailyActivity;
import com.lifemetrics.backend.health.service.HealthDataQueryService;
import com.lifemetrics.backend.health.service.HealthDataQueryService.DateRange;
import com.lifemetrics.backend.health.service.HealthSyncService;
import com.lifemetrics.backend.security.AdminWriteFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HealthSyncControllerTest {

    private final HealthDataQueryService queryService = mock(HealthDataQueryService.class);
    private final AdminWriteFilter adminWriteFilter = mock(AdminWriteFilter.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var controller = new HealthSyncController(mock(HealthSyncService.class), queryService, adminWriteFilter);
        ReflectionTestUtils.setField(controller, "syncToken", "secret");
        ReflectionTestUtils.setField(controller, "userId", 1L);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void 인증_없이는_조회할_수_없다() throws Exception {
        for (String path : List.of("daily", "exercises", "nutrition")) {
            mvc.perform(get("/api/health-sync/" + path)).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(queryService);
    }

    @Test
    void 토큰이_틀리면_조회할_수_없다() throws Exception {
        mvc.perform(get("/api/health-sync/daily").header("Authorization", "Bearer wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 토큰으로_기간을_지정해_조회한다() throws Exception {
        LocalDate d = LocalDate.of(2026, 11, 1);
        when(queryService.dailyActivity(eq(1L), eq(new DateRange(d, d))))
                .thenReturn(List.of(new DailyActivity(d, 1234L, null, null, null, null, null, null)));

        mvc.perform(get("/api/health-sync/daily?from=2026-11-01&to=2026-11-01")
                        .header("Authorization", "Bearer secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].steps").value(1234));
    }

    @Test
    void 관리자_쿠키로도_조회한다() throws Exception {
        when(adminWriteFilter.isAdmin(any())).thenReturn(true);
        when(queryService.exercises(eq(1L), any())).thenReturn(List.of());

        mvc.perform(get("/api/health-sync/exercises")).andExpect(status().isOk());
    }

    @Test
    void 기간이_뒤집히면_400() throws Exception {
        mvc.perform(get("/api/health-sync/nutrition?from=2026-11-02&to=2026-11-01")
                        .header("Authorization", "Bearer secret"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 날짜_형식이_틀리면_400() throws Exception {
        mvc.perform(get("/api/health-sync/daily?from=yesterday")
                        .header("Authorization", "Bearer secret"))
                .andExpect(status().isBadRequest());
    }
}
