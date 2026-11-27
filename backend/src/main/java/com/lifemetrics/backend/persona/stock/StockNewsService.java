package com.lifemetrics.backend.persona.stock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 증권 페르소나 — 보유 종목 소식(뉴스·공시) 조회(읽기 전용).
 * <p>
 * stock_news 는 journal_db, 보유 종목(invest_snapshot)은 riding_db 라 데이터소스가 다르다.
 * 보유 종목 코드는 StockService(riding)로 먼저 받고, 그 코드로 journal 쪽을 거른다(SQL 조인 없음).
 */
@Service
public class StockNewsService {

    static final int DEFAULT_DAYS = 30;
    static final int MAX_DAYS = 365;
    static final int DEFAULT_LIMIT = 50;
    static final int MAX_LIMIT = 500;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final NamedParameterJdbcTemplate jdbc;
    private final StockService stockService;

    @Autowired
    public StockNewsService(@Qualifier("journalDataSource") DataSource dataSource, StockService stockService) {
        this(new NamedParameterJdbcTemplate(dataSource), stockService);
    }

    StockNewsService(NamedParameterJdbcTemplate jdbc, StockService stockService) {
        this.jdbc = jdbc;
        this.stockService = stockService;
    }

    public record News(
            long id, String market, String symbol, String name, String source, String title, String summary,
            String url, String publisher, LocalDateTime publishedAt, LocalDateTime collectedAt, String dartRceptNo) {
    }

    /**
     * 최근 days 일 소식(최신순, 최대 limit 건).
     * symbol 을 비우면 지금 보유 종목 전체 소식. 보유 종목이 없으면 빈 목록.
     * source 는 NAVER_NEWS / DART, 비우면 둘 다.
     */
    public List<News> news(String symbol, String source, Integer days, Integer limit) {
        List<String> symbols;
        if (notBlank(symbol)) {
            symbols = List.of(symbol.trim());
        } else {
            symbols = stockService.currentHoldingSymbols();
            if (symbols.isEmpty()) return List.of();
        }
        int d = clamp(days, DEFAULT_DAYS, 1, MAX_DAYS);
        int n = clamp(limit, DEFAULT_LIMIT, 1, MAX_LIMIT);

        StringBuilder sql = new StringBuilder("""
                SELECT * FROM stock_news
                WHERE symbol IN (:symbols) AND published_at >= :since""");
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("symbols", symbols)
                .addValue("since", LocalDateTime.now(KST).minusDays(d));
        if (notBlank(source)) {
            sql.append(" AND source = :source");
            p.addValue("source", source.trim().toUpperCase());
        }
        sql.append(" ORDER BY published_at DESC, id DESC LIMIT ").append(n);
        return jdbc.query(sql.toString(), p, (rs, i) -> new News(
                rs.getLong("id"), rs.getString("market"), rs.getString("symbol"), rs.getString("name"),
                rs.getString("source"), rs.getString("title"), rs.getString("summary"), rs.getString("url"),
                rs.getString("publisher"), toLdt(rs.getTimestamp("published_at")),
                toLdt(rs.getTimestamp("collected_at")), rs.getString("dart_rcept_no")));
    }

    private static int clamp(Integer v, int def, int min, int max) {
        if (v == null) return def;
        return Math.max(min, Math.min(max, v));
    }

    private static LocalDateTime toLdt(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
