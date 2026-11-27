package com.lifemetrics.backend.persona.stock;

import com.lifemetrics.backend.security.AdminWriteFilter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StockNewsTest {

    private final StockService stockService = mock(StockService.class);
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final StockNewsService newsService = new StockNewsService(jdbc, stockService);

    // ── 컨트롤러: 관리자 체크 ──

    @Test
    void 관리자가_아니면_403이고_조회하지_않는다() throws Exception {
        StockNewsService news = mock(StockNewsService.class);
        AdminWriteFilter admin = mock(AdminWriteFilter.class);
        when(admin.isAdmin(any())).thenReturn(false);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new StockController(stockService, news, admin)).build();

        mvc.perform(get("/api/stock/news")).andExpect(status().isForbidden());
        mvc.perform(get("/api/stock/news").param("symbol", "IONQ")).andExpect(status().isForbidden());
        verifyNoInteractions(news);
    }

    @Test
    void 관리자면_소식을_돌려준다() throws Exception {
        StockNewsService news = mock(StockNewsService.class);
        AdminWriteFilter admin = mock(AdminWriteFilter.class);
        when(admin.isAdmin(any())).thenReturn(true);
        when(news.news("IONQ", "DART", 7, 10)).thenReturn(List.of(new StockNewsService.News(
                1, "US", "IONQ", "아이온큐", "NAVER_NEWS", "제목", null, "https://n.news/1", "연합",
                LocalDateTime.of(2026, 11, 27, 9, 0), LocalDateTime.of(2026, 11, 27, 9, 5), null)));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new StockController(stockService, news, admin)).build();

        mvc.perform(get("/api/stock/news").param("symbol", "IONQ").param("source", "DART")
                        .param("days", "7").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("제목"));
        verify(news).news("IONQ", "DART", 7, 10);
    }

    // ── 서비스: symbol 없으면 보유 종목 기준 ──

    @SuppressWarnings("unchecked")
    private MapSqlParameterSource captureParams() {
        ArgumentCaptor<MapSqlParameterSource> p = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(anyString(), p.capture(), any(RowMapper.class));
        return p.getValue();
    }

    @Test
    void symbol_없으면_지금_보유_종목으로_거른다() {
        when(stockService.currentHoldingSymbols()).thenReturn(List.of("439870", "463250", "IONQ"));

        newsService.news(null, null, null, null);

        MapSqlParameterSource p = captureParams();
        assertThat((List<String>) p.getValue("symbols")).containsExactly("439870", "463250", "IONQ");
        assertThat((LocalDateTime) p.getValue("since")).isBefore(LocalDateTime.now().minusDays(29));
        assertThat(p.hasValue("source")).isFalse();
    }

    @Test
    void 보유_종목이_없으면_조회_없이_빈_목록() {
        when(stockService.currentHoldingSymbols()).thenReturn(List.of());

        assertThat(newsService.news(" ", null, null, null)).isEmpty();
        verifyNoInteractions(jdbc);
    }

    @Test
    @SuppressWarnings("unchecked")
    void symbol_주면_그_종목만_보고_보유_종목은_안_본다() {
        newsService.news(" IONQ ", "dart", 7, 9999);

        MapSqlParameterSource p = captureParams();
        assertThat((List<String>) p.getValue("symbols")).containsExactly("IONQ");
        assertThat(p.getValue("source")).isEqualTo("DART");
        verifyNoInteractions(stockService);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        assertThat(sql.getValue()).endsWith("LIMIT " + StockNewsService.MAX_LIMIT);
    }
}
