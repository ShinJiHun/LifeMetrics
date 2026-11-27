package com.lifemetrics.backend.persona.stock;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

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

    /** 가장 최근 계좌 스냅샷의 보유 종목 코드. 스냅샷이 없으면 빈 목록. */
    public List<String> currentHoldingSymbols() {
        return jdbc.queryForList("""
                        SELECT DISTINCT s.symbol FROM invest_snapshot s
                        WHERE s.account_snapshot_id = (
                            SELECT id FROM invest_account_snapshot ORDER BY snapshot_at DESC, id DESC LIMIT 1)""",
                new MapSqlParameterSource(), String.class);
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

    // ───────────────────────────── 총 수익 ─────────────────────────────

    /** 통화·유형별 합계 한 줄(정산금액 기준, 없으면 거래금액). */
    public record FlowRow(String currency, String type, boolean cancellation, BigDecimal amount,
                          BigDecimal commission, BigDecimal tax) {
    }

    /** 통화별 현금 흐름. cash = 거래내역으로 다시 계산한 현금 잔고(추정). */
    public record CurrencyFlow(String currency, BigDecimal deposit, BigDecimal withdrawal, BigDecimal buy,
                               BigDecimal sell, BigDecimal exchangeIn, BigDecimal exchangeOut, BigDecimal income,
                               BigDecimal fee, BigDecimal cash) {
    }

    /**
     * 총 수익 = (보유 평가금액 + 추정 현금) − (입금 − 출금). 달러는 최근 환전 환율로 원화 환산.
     * 매수·매도·환전·이자·수수료가 모두 현금에 녹아 있으므로 이 한 식으로 "지금까지 번 돈"이 나온다.
     */
    public record Profit(
            LocalDate firstDate, LocalDate lastDate, int txCount,
            List<CurrencyFlow> flows,
            BigDecimal holdingsKrw, BigDecimal holdingsUsd, LocalDateTime holdingsAt,
            BigDecimal usdKrw, LocalDate usdKrwDate,
            BigDecimal depositKrw, BigDecimal withdrawalKrw, BigDecimal netDepositKrw,
            BigDecimal cashKrw, BigDecimal holdingsTotalKrw, BigDecimal currentAssetKrw,
            BigDecimal totalProfitKrw, BigDecimal profitRateOnDeposit,
            BigDecimal incomeKrw, BigDecimal feeKrw,
            List<String> unknownTypes) {
    }

    /** 현금 증감 부호. 0 = 현금이 안 움직이는 거래(주식 입고·출고). 모르는 유형은 null. */
    static Integer cashSign(String type) {
        return switch (type) {
            case "DEPOSIT", "SELL", "DIVIDEND", "INTEREST", "EXCHANGE_IN" -> 1;
            case "WITHDRAWAL", "BUY", "EXCHANGE_OUT" -> -1;
            case "SECURITIES_TRANSFER_IN", "SECURITIES_TRANSFER_OUT" -> 0;
            default -> null;
        };
    }

    public Profit profit() {
        List<FlowRow> rows = jdbc.query("""
                        SELECT currency, transaction_type, is_cancellation,
                               SUM(COALESCE(net_amount, amount, 0)) AS amt,
                               SUM(COALESCE(commission, 0)) AS com, SUM(COALESCE(tax, 0)) AS tax
                        FROM invest_transaction GROUP BY currency, transaction_type, is_cancellation""",
                (rs, i) -> new FlowRow(rs.getString("currency"), rs.getString("transaction_type"),
                        rs.getBoolean("is_cancellation"), rs.getBigDecimal("amt"),
                        rs.getBigDecimal("com"), rs.getBigDecimal("tax")));
        var range = jdbc.queryForMap(
                "SELECT MIN(settlement_date) f, MAX(settlement_date) l, COUNT(*) n FROM invest_transaction",
                new MapSqlParameterSource());
        List<Map<String, Object>> rate = jdbc.queryForList("""
                        SELECT settlement_date, exchange_rate FROM invest_transaction
                        WHERE exchange_rate IS NOT NULL AND exchange_rate > 0
                        ORDER BY settlement_date DESC, transaction_time DESC, id DESC LIMIT 1""",
                new MapSqlParameterSource());
        List<AccountSnapshot> latest = jdbc.query(
                "SELECT * FROM invest_account_snapshot ORDER BY snapshot_at DESC, id DESC LIMIT 1",
                (rs, i) -> account(rs));
        AccountSnapshot snap = latest.isEmpty() ? null : latest.get(0);
        BigDecimal usdKrw = rate.isEmpty() ? null : (BigDecimal) rate.get(0).get("exchange_rate");
        LocalDate usdKrwDate = rate.isEmpty() ? null : toLocalDate(rate.get(0).get("settlement_date"));
        return compute(rows, snap, usdKrw, usdKrwDate,
                toLocalDate(range.get("f")), toLocalDate(range.get("l")), ((Number) range.get("n")).intValue());
    }

    static Profit compute(List<FlowRow> rows, AccountSnapshot snap, BigDecimal usdKrw, LocalDate usdKrwDate,
                          LocalDate first, LocalDate last, int txCount) {
        Map<String, BigDecimal[]> acc = new TreeMap<>(); // currency → [dep, wd, buy, sell, exIn, exOut, income, fee, cash]
        Set<String> unknown = new TreeSet<>();
        for (FlowRow r : rows) {
            Integer sign = cashSign(r.type());
            if (sign == null) {
                unknown.add(r.type());
                continue;
            }
            BigDecimal[] a = acc.computeIfAbsent(r.currency(), k -> zeros(9));
            // 취소 거래는 원거래를 되돌린다
            BigDecimal amt = nz(r.amount()).multiply(BigDecimal.valueOf(r.cancellation() ? -1 : 1));
            int slot = switch (r.type()) {
                case "DEPOSIT" -> 0;
                case "WITHDRAWAL" -> 1;
                case "BUY" -> 2;
                case "SELL" -> 3;
                case "EXCHANGE_IN" -> 4;
                case "EXCHANGE_OUT" -> 5;
                case "DIVIDEND", "INTEREST" -> 6;
                default -> -1;
            };
            if (slot >= 0) a[slot] = a[slot].add(amt);
            BigDecimal fee = nz(r.commission()).add(nz(r.tax()));
            a[7] = a[7].add(r.cancellation() ? fee.negate() : fee);
            a[8] = a[8].add(amt.multiply(BigDecimal.valueOf(sign)));
        }
        List<CurrencyFlow> flows = new ArrayList<>();
        BigDecimal dep = BigDecimal.ZERO, wd = BigDecimal.ZERO, cash = BigDecimal.ZERO,
                income = BigDecimal.ZERO, fee = BigDecimal.ZERO;
        for (var e : acc.entrySet()) {
            BigDecimal[] a = e.getValue();
            flows.add(new CurrencyFlow(e.getKey(), a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8]));
            dep = dep.add(toKrw(e.getKey(), a[0], usdKrw));
            wd = wd.add(toKrw(e.getKey(), a[1], usdKrw));
            cash = cash.add(toKrw(e.getKey(), a[8], usdKrw));
            income = income.add(toKrw(e.getKey(), a[6], usdKrw));
            fee = fee.add(toKrw(e.getKey(), a[7], usdKrw));
        }
        BigDecimal hKrw = snap == null ? BigDecimal.ZERO : nz(snap.evalKrw());
        BigDecimal hUsd = snap == null ? BigDecimal.ZERO : nz(snap.evalUsd());
        BigDecimal holdingsTotal = hKrw.add(toKrw("USD", hUsd, usdKrw));
        BigDecimal netDeposit = dep.subtract(wd);
        BigDecimal current = holdingsTotal.add(cash);
        BigDecimal profit = current.subtract(netDeposit);
        BigDecimal rate = dep.signum() > 0 ? profit.divide(dep, 6, RoundingMode.HALF_UP) : null;
        return new Profit(first, last, txCount, flows,
                snap == null ? null : snap.evalKrw(), snap == null ? null : snap.evalUsd(),
                snap == null ? null : snap.snapshotAt(), usdKrw, usdKrwDate,
                round(dep), round(wd), round(netDeposit), round(cash), round(holdingsTotal), round(current),
                round(profit), rate, round(income), round(fee), List.copyOf(unknown));
    }

    private static BigDecimal toKrw(String currency, BigDecimal v, BigDecimal usdKrw) {
        if (!"USD".equals(currency)) return v;
        return usdKrw == null ? BigDecimal.ZERO : v.multiply(usdKrw);
    }

    private static BigDecimal[] zeros(int n) {
        BigDecimal[] a = new BigDecimal[n];
        Arrays.fill(a, BigDecimal.ZERO);
        return a;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal round(BigDecimal v) {
        return v.setScale(0, RoundingMode.HALF_UP);
    }

    private static LocalDate toLocalDate(Object o) {
        if (o == null) return null;
        if (o instanceof LocalDate d) return d;
        if (o instanceof java.sql.Date d) return d.toLocalDate();
        return LocalDate.parse(o.toString());
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
