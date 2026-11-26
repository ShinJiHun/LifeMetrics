// 증권 페르소나 — 토스증권 계좌 (관리자 전용, 읽기만).
// data_pipeline 이 토스증권에서 받아 둔 invest_* 테이블(계좌 스냅샷·보유 종목·거래내역)을 보여 준다.
import { useEffect, useMemo, useState } from "react";
import type { CSSProperties } from "react";
import {
    fetchStockSummary,
    fetchStockTransactions,
    type StockSummary,
    type StockTransaction,
    type TxFilter,
} from "@/api/stock";

const ACCENT = "#d97706";
const INK = "#111827";
const MUTED = "#6b7280";
const LINE = "#e5e7eb";
const UP = "#dc2626"; // 국내 관례: 이익 빨강
const DOWN = "#2563eb";

const TYPE_LABEL: Record<string, string> = {
    BUY: "매수",
    SELL: "매도",
    DIVIDEND: "배당",
    DEPOSIT: "입금",
    WITHDRAWAL: "출금",
    EXCHANGE_IN: "환전 입금",
    EXCHANGE_OUT: "환전 출금",
    INTEREST: "이자",
    SECURITIES_TRANSFER_IN: "주식 입고",
    SECURITIES_TRANSFER_OUT: "주식 출고",
};
const typeLabel = (t: string) => TYPE_LABEL[t] ?? t;

function errorMessage(e: unknown): string {
    const anyErr = e as { response?: { status?: number; data?: { message?: string; error?: string } } };
    if (anyErr.response?.status === 403) return "관리자 로그인이 필요합니다.";
    return anyErr.response?.data?.message || anyErr.response?.data?.error || "불러오지 못했습니다.";
}

function money(v: number | null | undefined, currency: string): string {
    if (v == null) return "-";
    return currency === "USD"
        ? `$${v.toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
        : `${Math.round(v).toLocaleString("ko-KR")}원`;
}

function signed(v: number | null | undefined, currency: string): string {
    if (v == null) return "-";
    return (v > 0 ? "+" : v < 0 ? "-" : "") + money(Math.abs(v), currency);
}

function pct(v: number | null | undefined): string {
    if (v == null) return "-";
    return `${v > 0 ? "+" : ""}${(v * 100).toFixed(2)}%`;
}

const tone = (v: number | null | undefined): CSSProperties => ({
    color: v == null || v === 0 ? INK : v > 0 ? UP : DOWN,
});

function qty(v: number | null | undefined): string {
    if (v == null) return "-";
    return v.toLocaleString("ko-KR", { maximumFractionDigits: 6 });
}

function ymd(d: Date): string {
    const p = (n: number) => String(n).padStart(2, "0");
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

export default function TossAccountPage() {
    const [summary, setSummary] = useState<StockSummary | null>(null);
    const [txs, setTxs] = useState<StockTransaction[]>([]);
    const [error, setError] = useState<string | null>(null);
    const [loading, setLoading] = useState(false);
    const [filter, setFilter] = useState<TxFilter>(() => {
        const now = new Date();
        return { from: `${now.getFullYear()}-01-01`, to: ymd(now), type: "", market: "", symbol: "" };
    });

    useEffect(() => {
        fetchStockSummary()
            .then(setSummary)
            .catch((e) => setError(errorMessage(e)));
    }, []);

    useEffect(() => {
        const t = setTimeout(() => {
            setLoading(true);
            fetchStockTransactions(filter)
                .then((list) => {
                    setTxs(list);
                    setError(null);
                })
                .catch((e) => setError(errorMessage(e)))
                .finally(() => setLoading(false));
        }, 250);
        return () => clearTimeout(t);
    }, [filter]);

    // 통화·유형별 합계 (취소 거래 제외, 정산금액 기준)
    const totals = useMemo(() => {
        const m = new Map<string, { type: string; currency: string; count: number; sum: number; fee: number }>();
        for (const t of txs) {
            if (t.cancellation) continue;
            const key = `${t.currency}|${t.transactionType}`;
            const cur = m.get(key) ?? { type: t.transactionType, currency: t.currency, count: 0, sum: 0, fee: 0 };
            cur.count += 1;
            cur.sum += t.netAmount ?? t.amount ?? 0;
            cur.fee += (t.commission ?? 0) + (t.tax ?? 0);
            m.set(key, cur);
        }
        return [...m.values()].sort((a, b) => a.currency.localeCompare(b.currency) || b.count - a.count);
    }, [txs]);

    const types = useMemo(() => Object.keys(TYPE_LABEL), []);
    const set = (patch: Partial<TxFilter>) => setFilter((f) => ({ ...f, ...patch }));
    const latest = summary?.latest;

    return (
        <div style={S.page}>
            <h1 style={S.h1}>📈 토스증권 계좌</h1>
            <p style={S.lead}>
                data_pipeline 이 토스증권에서 받아 온 계좌 스냅샷·보유 종목·거래내역이에요. 여기서는 보기만 해요.
            </p>

            {error && <div style={S.error}>{error}</div>}

            <section style={S.card}>
                <h2 style={S.h2}>
                    계좌 요약
                    {latest && (
                        <span style={S.sub}>
                            {latest.snapshotAt.replace("T", " ").slice(0, 16)} · {latest.session} · 계좌 …
                            {latest.accountSuffix}
                        </span>
                    )}
                </h2>
                {!summary ? (
                    <div style={S.empty}>불러오는 중…</div>
                ) : !latest ? (
                    <div style={S.empty}>아직 스냅샷이 없어요.</div>
                ) : (
                    <div style={S.tiles}>
                        {(["KRW", "USD"] as const).map((c) => {
                            const k = c === "KRW";
                            const evalAmt = k ? latest.evalKrw : latest.evalUsd;
                            const purchase = k ? latest.purchaseKrw : latest.purchaseUsd;
                            const pnl = k ? latest.pnlKrw : latest.pnlUsd;
                            const daily = k ? latest.dailyPnlKrw : latest.dailyPnlUsd;
                            return (
                                <div key={c} style={S.tile}>
                                    <div style={S.tileLabel}>{k ? "🇰🇷 원화 평가금액" : "🇺🇸 달러 평가금액"}</div>
                                    <div style={S.tileValue}>{money(evalAmt, c)}</div>
                                    <div style={S.tileMeta}>매입 {money(purchase, c)}</div>
                                    <div style={{ ...S.tileMeta, ...tone(pnl) }}>손익 {signed(pnl, c)}</div>
                                    <div style={{ ...S.tileMeta, ...tone(daily) }}>오늘 {signed(daily, c)}</div>
                                </div>
                            );
                        })}
                        <div style={S.tile}>
                            <div style={S.tileLabel}>전체 수익률</div>
                            <div style={{ ...S.tileValue, ...tone(latest.pnlRate) }}>{pct(latest.pnlRate)}</div>
                            <div style={{ ...S.tileMeta, ...tone(latest.pnlRateAfterCost) }}>
                                비용 반영 {pct(latest.pnlRateAfterCost)}
                            </div>
                            <div style={{ ...S.tileMeta, ...tone(latest.dailyPnlRate) }}>
                                오늘 {pct(latest.dailyPnlRate)}
                            </div>
                            <div style={S.tileMeta}>보유 {latest.holdingCount}종목</div>
                        </div>
                    </div>
                )}
            </section>

            {summary && summary.holdings.length > 0 && (
                <section style={S.card}>
                    <h2 style={S.h2}>보유 종목</h2>
                    <div style={S.scroll}>
                        <table style={S.table}>
                            <thead>
                                <tr>
                                    <th style={S.th}>종목</th>
                                    <th style={S.thR}>수량</th>
                                    <th style={S.thR}>평균가</th>
                                    <th style={S.thR}>현재가</th>
                                    <th style={S.thR}>평가금액</th>
                                    <th style={S.thR}>손익</th>
                                    <th style={S.thR}>수익률</th>
                                    <th style={S.thR}>오늘</th>
                                </tr>
                            </thead>
                            <tbody>
                                {summary.holdings.map((h) => (
                                    <tr key={`${h.market}-${h.symbol}`}>
                                        <td style={S.td}>
                                            <b>{h.name ?? h.symbol}</b>
                                            <div style={S.mini}>
                                                {h.market} · {h.symbol}
                                            </div>
                                        </td>
                                        <td style={S.tdR}>{qty(h.quantity)}</td>
                                        <td style={S.tdR}>{money(h.avgPrice, h.currency)}</td>
                                        <td style={S.tdR}>{money(h.currentPrice, h.currency)}</td>
                                        <td style={S.tdR}>{money(h.evalAmount, h.currency)}</td>
                                        <td style={{ ...S.tdR, ...tone(h.pnl) }}>{signed(h.pnl, h.currency)}</td>
                                        <td style={{ ...S.tdR, ...tone(h.pnlRate) }}>{pct(h.pnlRate)}</td>
                                        <td style={{ ...S.tdR, ...tone(h.dailyPnlRate) }}>{pct(h.dailyPnlRate)}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </section>
            )}

            <section style={S.card}>
                <h2 style={S.h2}>
                    거래내역
                    <span style={S.sub}>{loading ? "불러오는 중…" : `${txs.length}건 (결제일 기준)`}</span>
                </h2>
                <div style={S.filters}>
                    <input type="date" value={filter.from ?? ""} onChange={(e) => set({ from: e.target.value })} style={S.input} />
                    <span style={{ color: MUTED }}>~</span>
                    <input type="date" value={filter.to ?? ""} onChange={(e) => set({ to: e.target.value })} style={S.input} />
                    <select value={filter.type} onChange={(e) => set({ type: e.target.value })} style={S.input}>
                        <option value="">전체 유형</option>
                        {types.map((t) => (
                            <option key={t} value={t}>
                                {typeLabel(t)}
                            </option>
                        ))}
                    </select>
                    <select value={filter.market} onChange={(e) => set({ market: e.target.value })} style={S.input}>
                        <option value="">국내+해외</option>
                        <option value="KR">국내</option>
                        <option value="US">해외(미국)</option>
                    </select>
                    <input
                        placeholder="종목명·코드"
                        value={filter.symbol}
                        onChange={(e) => set({ symbol: e.target.value })}
                        style={{ ...S.input, width: 130 }}
                    />
                </div>

                {totals.length > 0 && (
                    <div style={S.totals}>
                        {totals.map((t) => (
                            <span key={`${t.currency}-${t.type}`} style={S.chip}>
                                {typeLabel(t.type)} {t.count}건 · <b>{money(t.sum, t.currency)}</b>
                                {t.fee > 0 && <span style={{ color: MUTED }}> (수수료·세금 {money(t.fee, t.currency)})</span>}
                            </span>
                        ))}
                    </div>
                )}

                <div style={S.scroll}>
                    <table style={S.table}>
                        <thead>
                            <tr>
                                <th style={S.th}>결제일</th>
                                <th style={S.th}>유형</th>
                                <th style={S.th}>종목</th>
                                <th style={S.thR}>수량</th>
                                <th style={S.thR}>체결가</th>
                                <th style={S.thR}>거래금액</th>
                                <th style={S.thR}>수수료·세금</th>
                                <th style={S.thR}>정산금액</th>
                                <th style={S.thR}>환율</th>
                            </tr>
                        </thead>
                        <tbody>
                            {txs.map((t) => (
                                <tr key={t.id} style={t.cancellation ? { opacity: 0.5, textDecoration: "line-through" } : undefined}>
                                    <td style={S.td}>
                                        {t.settlementDate}
                                        {!t.settled && <div style={{ ...S.mini, color: ACCENT }}>미결제(예정)</div>}
                                    </td>
                                    <td style={S.td}>
                                        <span style={typeBadge(t.transactionType)}>{typeLabel(t.transactionType)}</span>
                                        {t.cancellation && <div style={S.mini}>취소</div>}
                                    </td>
                                    <td style={S.td}>
                                        {t.symbol ? (
                                            <>
                                                {t.name ?? t.symbol}
                                                <div style={S.mini}>
                                                    {t.market} · {t.symbol}
                                                </div>
                                            </>
                                        ) : (
                                            <span style={{ color: MUTED }}>{t.market === "US" ? "달러" : "원화"}</span>
                                        )}
                                    </td>
                                    <td style={S.tdR}>{qty(t.quantity)}</td>
                                    <td style={S.tdR}>{t.filledPrice != null ? money(t.filledPrice, t.currency) : "-"}</td>
                                    <td style={S.tdR}>{money(t.amount, t.currency)}</td>
                                    <td style={S.tdR}>
                                        {(t.commission ?? 0) + (t.tax ?? 0) > 0
                                            ? money((t.commission ?? 0) + (t.tax ?? 0), t.currency)
                                            : "-"}
                                    </td>
                                    <td style={{ ...S.tdR, fontWeight: 600 }}>{money(t.netAmount, t.currency)}</td>
                                    <td style={S.tdR}>{t.exchangeRate != null ? t.exchangeRate.toLocaleString("ko-KR") : "-"}</td>
                                </tr>
                            ))}
                            {!loading && txs.length === 0 && (
                                <tr>
                                    <td colSpan={9} style={{ ...S.td, ...S.empty }}>
                                        조건에 맞는 거래가 없어요.
                                    </td>
                                </tr>
                            )}
                        </tbody>
                    </table>
                </div>
                {txs.length >= 2000 && <div style={S.mini}>최대 2,000건까지만 보여요. 기간을 좁혀 주세요.</div>}
            </section>
        </div>
    );
}

function typeBadge(type: string): CSSProperties {
    const color =
        type === "BUY" ? UP : type === "SELL" ? DOWN : type === "DIVIDEND" || type === "INTEREST" ? "#059669" : MUTED;
    return {
        display: "inline-block",
        padding: "2px 8px",
        borderRadius: 999,
        fontSize: 12,
        fontWeight: 600,
        color,
        background: `${color}14`,
        border: `1px solid ${color}33`,
        whiteSpace: "nowrap",
    };
}

const thBase: CSSProperties = {
    padding: "8px 10px",
    borderBottom: `2px solid ${LINE}`,
    fontSize: 12,
    color: MUTED,
    fontWeight: 600,
    whiteSpace: "nowrap",
    textAlign: "left",
};
const tdBase: CSSProperties = { padding: "8px 10px", borderBottom: `1px solid ${LINE}`, fontSize: 13, color: INK };

const S: Record<string, CSSProperties> = {
    page: { maxWidth: 1100, margin: "0 auto", padding: "24px 16px 60px" },
    h1: { fontSize: 24, fontWeight: 800, color: INK, margin: "0 0 6px" },
    lead: { color: MUTED, fontSize: 14, margin: "0 0 18px", lineHeight: 1.6 },
    error: { background: "#fef2f2", color: "#b91c1c", padding: "10px 14px", borderRadius: 8, marginBottom: 14, fontSize: 14 },
    card: { background: "#fff", border: `1px solid ${LINE}`, borderRadius: 12, padding: 18, marginBottom: 16 },
    h2: { fontSize: 17, fontWeight: 700, color: INK, margin: "0 0 12px", display: "flex", alignItems: "baseline", gap: 10, flexWrap: "wrap" },
    sub: { fontSize: 12, fontWeight: 400, color: MUTED },
    empty: { color: MUTED, fontSize: 14, textAlign: "center", padding: 16 },
    tiles: { display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: 12 },
    tile: { border: `1px solid ${LINE}`, borderRadius: 10, padding: 14, borderTop: `3px solid ${ACCENT}` },
    tileLabel: { fontSize: 12, color: MUTED, marginBottom: 4 },
    tileValue: { fontSize: 22, fontWeight: 800, color: INK, marginBottom: 6 },
    tileMeta: { fontSize: 13, color: MUTED, lineHeight: 1.6 },
    scroll: { overflowX: "auto" },
    table: { width: "100%", borderCollapse: "collapse", fontVariantNumeric: "tabular-nums" },
    th: thBase,
    thR: { ...thBase, textAlign: "right" },
    td: tdBase,
    tdR: { ...tdBase, textAlign: "right", whiteSpace: "nowrap" },
    mini: { fontSize: 11, color: MUTED, marginTop: 2 },
    filters: { display: "flex", flexWrap: "wrap", gap: 8, alignItems: "center", marginBottom: 12 },
    input: { padding: "6px 8px", border: `1px solid ${LINE}`, borderRadius: 6, fontSize: 13, background: "#fff" },
    totals: { display: "flex", flexWrap: "wrap", gap: 6, marginBottom: 12 },
    chip: { fontSize: 12, padding: "4px 10px", borderRadius: 999, background: "#fffbeb", border: "1px solid #fde68a", color: INK },
};
