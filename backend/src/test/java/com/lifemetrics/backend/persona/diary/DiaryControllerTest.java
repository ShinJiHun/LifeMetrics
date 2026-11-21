package com.lifemetrics.backend.persona.diary;

import com.lifemetrics.backend.security.AdminWriteFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DiaryControllerTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 18);
    private static final String NOTE_JSON = "{\"source\":\"manual\",\"content\":\"메모\"}";

    private final DiaryService service = mock(DiaryService.class);
    private final LectureService lectures = mock(LectureService.class);
    private final AdminWriteFilter adminWriteFilter = mock(AdminWriteFilter.class);
    private DiaryController controller;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        controller = new DiaryController(service, lectures, adminWriteFilter);
        ReflectionTestUtils.setField(controller, "appToken", "secret");
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
        when(service.day(DAY)).thenReturn(new DiaryService.Day(DAY.toString(), List.of(), null, null, Map.of(), List.of()));
        when(service.addNote(eq(DAY), any(), any(), any())).thenReturn(
                new DiaryService.Note(1L, DAY.toString(), "manual", null, "메모", "2026-11-18T21:00:00"));
    }

    @Test
    void 인증_없이는_403() throws Exception {
        mvc.perform(get("/api/diary/2026-11-18"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("관리자만 접근할 수 있습니다."));
        mvc.perform(post("/api/diary/2026-11-18/notes").contentType(MediaType.APPLICATION_JSON).content(NOTE_JSON))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void 토큰이_틀리면_403() throws Exception {
        mvc.perform(get("/api/diary/2026-11-18").header("Authorization", "Bearer wrong"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void 올바른_토큰이면_조회와_메모_추가가_된다() throws Exception {
        mvc.perform(get("/api/diary/2026-11-18").header("Authorization", "Bearer secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-11-18"));

        mvc.perform(post("/api/diary/2026-11-18/notes").header("Authorization", "Bearer secret")
                        .contentType(MediaType.APPLICATION_JSON).content(NOTE_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
        verify(service).addNote(DAY, "manual", null, "메모");
    }

    @Test
    void 관리자_쿠키면_200() throws Exception {
        when(adminWriteFilter.isAdmin(any())).thenReturn(true);
        mvc.perform(get("/api/diary/2026-11-18")).andExpect(status().isOk());
    }

    @Test
    void 서버에_토큰_설정이_없으면_토큰만으로는_403() throws Exception {
        ReflectionTestUtils.setField(controller, "appToken", "");
        mvc.perform(get("/api/diary/2026-11-18").header("Authorization", "Bearer "))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/diary/2026-11-18").header("Authorization", "Bearer secret"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void 강연_API도_관리자만_되고_필터를_넘긴다() throws Exception {
        mvc.perform(get("/api/diary/lectures")).andExpect(status().isForbidden());
        mvc.perform(post("/api/diary/notes/3/lecture")).andExpect(status().isForbidden());
        verifyNoInteractions(lectures);

        when(lectures.list(any(), any(), any(), any())).thenReturn(List.of());
        mvc.perform(get("/api/diary/lectures").param("tag", "AI").param("speaker", "김")
                        .param("from", "2026-11-01").param("to", "2026-11-30").header("Authorization", "Bearer secret"))
                .andExpect(status().isOk());
        verify(lectures).list("AI", "김", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));

        mvc.perform(get("/api/diary/lectures").param("from", "11월").header("Authorization", "Bearer secret"))
                .andExpect(status().isBadRequest());
    }
}
