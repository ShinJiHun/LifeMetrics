package com.lifemetrics.backend.persona.stock;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 증권 페르소나 — 토스증권 계좌 조회(읽기 전용).
 * <p>
 * invest_account_snapshot / invest_snapshot / invest_transaction 은 data_pipeline 이 토스증권에서 받아 채운다.
 * 여기서는 쓰지 않고 보여 주기만 한다.
 */
@Service
public class StockService {

    /** 거래내역 한 번에 돌려주는 최대 건수 */
    static final int TX_LIMIT = 2000;

    private final NamedParameterJdbcTemplate jdbc;

    public StockService(@Qualifier("ridingDataSource") DataSource dataSource) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    public record AccountSnapshot(
            long id, LocalDateTime snapshotAt, LocalDate snapshotDate, String session, String accountSuffix,
            BigDecimal purchaseKrw, BigDecimal purchaseUsd, BigDecimal evalKrw, BigDecimal evalUsd,
            BigDecimal pnlKrw, BigDecimal pnlUsd, BigDecimal pnlAfterCostKrw, BigDecimal pnlAfterCostUsd,
            BigDecimal pnlRate, BigDecimal pnlRateAfterCost,
            BigDecimal dailyPnlKrw, BigDecimal dailyPnlUsd, BigDecimal dailyPnlRate, int holdingCount) {
    }

    public record Holding(
            String market, String symbol, String name, String currency, BigDecimal quantity,
            BigDecimal avgPrice, BigDecimal currentPrice, BigDecimal purchaseAmount, BigDecimal evalAmount,
            BigDecimal pnl, BigDecimal pnlRate, BigDecimal dailyPnl, BigDecimal dailyPnlRate) {
    }

    public record Summary(AccountSnapshot latest, List<Holding> holdings, List<AccountSnapshot> history) {
    }

    public record Transaction(
            long id, String accountSuffix, LocalDate settlementDate, boolean settled, LocalDateTime transactionTime,
            String transactionType, boolean cancellation, String market, String currency, String assetClass,
            String symbol, String name, BigDecimal quantity, BigDecimal filledPrice, BigDecimal amount,
            BigDecimal commission, BigDecimal tax, BigDecimal netAmount, BigDecimal exchangeRate) {
    }

    /** 가장 최근 계좌 스냅샷 + 그 시점 보유 종목 + 최근 스냅샷 추이(최대 120개). 스냅샷이 없으면 latest=null. */
    public Summary summary() {
        List<AccountSnapshot> history = jdbc.query(
                "SELECT * FROM invest_account_snapshot ORDER BY snapshot_at DESC, id DESC LIMIT 120",
                (rs, i) -> account(rs));
        if (history.isEmpty()) return new Summary(null, List.of(), List.of());
        AccountSnapshot latest = history.get(0);
        List<Holding> holdings = jdbc.query("""
                        SELECT * FROM invest_snapshot WHERE account_snapshot_id = :id
                        ORDER BY market, eval_amount DESC""",
                new MapSqlParameterSource("id", latest.id()),
                (rs, i) -> new Holding(
                        rs.getString("market"), rs.getString("symbol"), rs.getString("name"),
                        rs.getString("currency"), rs.getBigDecimal("quantity"), rs.getBigDecimal("avg_price"),
                        rs.getBigDecimal("current_price"), rs.getBigDecimal("purchase_amount"),
                        rs.getBigDecimal("eval_amount"), rs.getBigDecimal("pnl"), rs.getBigDecimal("pnl_rate"),
                        rs.getBigDecimal("daily_pnl"), rs.getBigDecimal("daily_pnl_rate")));
        return new Summary(latest, holdings, history);
    }

    /**
     * 결제일 기준 기간 거래내역(최신순). type/market/symbol 은 비우면 전체.
     * 종목명은 거래내역에 없어서 보유 스냅샷에 한 번이라도 나온 이름을 붙인다.
     */
    public List<Transaction> transactions(LocalDate from, LocalDate to, String type, String market, String symbol) {
        StringBuilder sql = new StringBuilder("""
                SELECT t.*, n.name FROM invest_transaction t
                LEFT JOIN (SELECT symbol, MAX(name) AS name FROM invest_snapshot GROUP BY symbol) n
                       ON n.symbol = t.symbol
                WHERE 1=1""");
        MapSqlParameterSource p = new MapSqlParameterSource();
        if (from != null) {
            sql.append(" AND t.settlement_date >= :from");
            p.addValue("from", from);
        }
        if (to != null) {
            sql.append(" AND t.settlement_date <= :to");
            p.addValue("to", to);
        }
        if (notBlank(type)) {
            sql.append(" AND t.transaction_type = :type");
            p.addValue("type", type.trim());
        }
        if (notBlank(market)) {
            sql.append(" AND t.market = :market");
            p.addValue("market", market.trim());
        }
        if (notBlank(symbol)) {
            sql.append(" AND (t.symbol LIKE :sym OR n.name LIKE :sym)");
            p.addValue("sym", "%" + symbol.trim() + "%");
        }
        sql.append(" ORDER BY t.settlement_date DESC, t.transaction_time DESC, t.id DESC LIMIT ").append(TX_LIMIT);
        return jdbc.query(sql.toString(), p, (rs, i) -> new Transaction(
                rs.getLong("id"), rs.getString("account_suffix"), rs.getObject("settlement_date", LocalDate.class),
                rs.getBoolean("settled"), toLdt(rs.getTimestamp("transaction_time")),
                rs.getString("transaction_type"), rs.getBoolean("is_cancellation"), rs.getString("market"),
                rs.getString("currency"), rs.getString("asset_class"), rs.getString("symbol"), rs.getString("name"),
                rs.getBigDecimal("quantity"), rs.getBigDecimal("filled_price"), rs.getBigDecimal("amount"),
                rs.getBigDecimal("commission"), rs.getBigDecimal("tax"), rs.getBigDecimal("net_amount"),
                rs.getBigDecimal("exchange_rate")));
    }

    private static AccountSnapshot account(ResultSet rs) throws SQLException {
        return new AccountSnapshot(
                rs.getLong("id"), toLdt(rs.getTimestamp("snapshot_at")), rs.getObject("snapshot_date", LocalDate.class),
                rs.getString("session"), rs.getString("account_suffix"),
                rs.getBigDecimal("purchase_krw"), rs.getBigDecimal("purchase_usd"),
                rs.getBigDecimal("eval_krw"), rs.getBigDecimal("eval_usd"),
                rs.getBigDecimal("pnl_krw"), rs.getBigDecimal("pnl_usd"),
                rs.getBigDecimal("pnl_after_cost_krw"), rs.getBigDecimal("pnl_after_cost_usd"),
                rs.getBigDecimal("pnl_rate"), rs.getBigDecimal("pnl_rate_after_cost"),
                rs.getBigDecimal("daily_pnl_krw"), rs.getBigDecimal("daily_pnl_usd"),
                rs.getBigDecimal("daily_pnl_rate"), rs.getInt("holding_count"));
    }

    private static LocalDateTime toLdt(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
