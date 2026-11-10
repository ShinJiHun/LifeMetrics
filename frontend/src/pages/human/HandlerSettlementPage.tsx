// 인간 페르소나 — 핸들러(쏘카) 월별 정산 (관리자 전용).
// 운행료 명세서 PDF 를 올리면 서버가 귀속월·운행일수·횟수·지급금액을 읽어 준다.
// 화면은 연도별 합계 + 월별 그래프 + 표. 원본 PDF 는 표에서 링크로만 연다.
// 지급금액은 세전으로 보고 3.3%(소득세 3% + 지방소득세 0.3%) 원천징수 후 실수령을 추정한다.
import { useEffect, useMemo, useRef, useState } from "react";
import type { CSSProperties } from "react";
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import {
    deleteSettlement,
    fetchSettlements,
    settlementPdfUrl,
    uploadSettlement,
    type SettlementFile,
} from "@/api/handlerSettlement";

const ACCENT = "#16a34a";
const INK = "#111827";
const MUTED = "#6b7280";
const GRID = "#eef0f3";

const won = (n: number | null | undefined) => (n == null ? "–" : `${n.toLocaleString("ko-KR")}원`);
const manwon = (n: number) => (n >= 10000 ? `${Math.round(n / 10000).toLocaleString("ko-KR")}만` : n.toLocaleString("ko-KR"));

function errorMessage(e: unknown): string {
    const anyErr = e as { response?: { status?: number; data?: { message?: string; error?: string } } };
    const status = anyErr.response?.status;
    if (status === 403) return "관리자 로그인이 필요합니다.";
    if (status === 413) return "파일이 너무 큽니다 (최대 50MB).";
    return anyErr.response?.data?.message || anyErr.response?.data?.error || "요청에 실패했습니다.";
}

interface MonthRow {
    month: number; // 1~12
    label: string; // "1월"
    file?: SettlementFile;
    gross: number;
    incomeTax: number;
    localTax: number;
    net: number;
    workDays: number;
    trips: number;
}

export default function HandlerSettlementPage() {
    const [files, setFiles] = useState<SettlementFile[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [notice, setNotice] = useState<string | null>(null);
    const [year, setYear] = useState<number | null>(null);
    const [uploading, setUploading] = useState(false);
    const [manualMonth, setManualMonth] = useState("");
    const inputRef = useRef<HTMLInputElement>(null);

    const load = async () => {
        setLoading(true);
        try {
            const list = await fetchSettlements();
            setFiles(list);
            setError(null);
        } catch (e) {
            setError(errorMessage(e));
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        load();
    }, []);

    // 합계에 들어가는 파일 = 중복 아닌 것
    const active = useMemo(() => files.filter((f) => !f.duplicate), [files]);
    const extras = useMemo(() => files.filter((f) => f.duplicate || !f.parsed), [files]);

    const years = useMemo(() => {
        const set = new Set(active.map((f) => Number(f.month.slice(0, 4))));
        return [...set].sort((a, b) => b - a);
    }, [active]);

    useEffect(() => {
        if (year == null && years.length > 0) setYear(years[0]);
    }, [years, year]);

    const rows: MonthRow[] = useMemo(() => {
        if (year == null) return [];
        return Array.from({ length: 12 }, (_, i) => {
            const m = i + 1;
            const key = `${year}-${String(m).padStart(2, "0")}`;
            const f = active.find((x) => x.month === key);
            return {
                month: m,
                label: `${m}월`,
                file: f,
                gross: f?.grossAmount ?? 0,
                incomeTax: f?.incomeTax ?? 0,
                localTax: f?.localTax ?? 0,
                net: f?.netAmount ?? 0,
                workDays: f?.workDays ?? 0,
                trips: f?.trips ?? 0,
            };
        });
    }, [active, year]);

    const filled = rows.filter((r) => r.file);
    const total = filled.reduce(
        (acc, r) => ({
            gross: acc.gross + r.gross,
            incomeTax: acc.incomeTax + r.incomeTax,
            localTax: acc.localTax + r.localTax,
            net: acc.net + r.net,
            workDays: acc.workDays + r.workDays,
            trips: acc.trips + r.trips,
        }),
        { gross: 0, incomeTax: 0, localTax: 0, net: 0, workDays: 0, trips: 0 },
    );
    const best = filled.reduce<MonthRow | null>((b, r) => (b == null || r.gross > b.gross ? r : b), null);
    const avg = filled.length ? Math.round(total.gross / filled.length) : 0;

    const onPick = async (picked: FileList | null) => {
        if (!picked || picked.length === 0) return;
        setUploading(true);
        setError(null);
        setNotice(null);
        const done: string[] = [];
        const failed: string[] = [];
        for (const file of Array.from(picked)) {
            try {
                const saved = await uploadSettlement(file, manualMonth || undefined);
                done.push(saved.parsed ? `${saved.month} ${won(saved.grossAmount)}` : `${saved.month} (금액 못 읽음)`);
            } catch (e) {
                failed.push(`${file.name}: ${errorMessage(e)}`);
            }
        }
        if (inputRef.current) inputRef.current.value = "";
        if (done.length) setNotice(`저장됨 · ${done.join(", ")}`);
        if (failed.length) setError(failed.join("\n"));
        await load();
        setUploading(false);
    };

    const onDelete = async (f: SettlementFile) => {
        if (!confirm(`${f.month} · ${f.originalName} 을(를) 삭제할까요?`)) return;
        try {
            await deleteSettlement(f.filename);
            await load();
        } catch (e) {
            setError(errorMessage(e));
        }
    };

    return (
        <div style={S.page}>
            <div style={S.headerRow}>
                <div>
                    <h2 style={S.title}>💼 핸들러 월별 정산</h2>
                    <p style={S.sub}>쏘카 운행료 명세서 기준 · 실수령은 3.3% 원천징수를 뺀 추정치</p>
                </div>
                {years.length > 0 && (
                    <div style={S.yearTabs} role="tablist" aria-label="연도">
                        {years.map((y) => (
                            <button
                                key={y}
                                role="tab"
                                aria-selected={y === year}
                                onClick={() => setYear(y)}
                                style={{ ...S.yearTab, ...(y === year ? S.yearTabOn : null) }}
                            >
                                {y}
                            </button>
                        ))}
                    </div>
                )}
            </div>

            {error && <div style={S.error}>{error}</div>}
            {notice && <div style={S.notice}>{notice}</div>}

            {loading && files.length === 0 ? (
                <div style={S.empty}>불러오는 중…</div>
            ) : year == null ? (
                <div style={S.empty}>아직 저장된 정산서가 없습니다. 아래에서 PDF를 올려 주세요.</div>
            ) : (
                <>
                    {/* 올해 요약 */}
                    <div className="hs-tiles" style={S.tiles}>
                        <Tile label={`${year}년 총 지급액 (세전)`} value={won(total.gross)} hint={`${filled.length}개월 · 월평균 ${won(avg)}`} />
                        <Tile
                            label="원천징수 추정 (3.3%)"
                            value={`−${won(total.incomeTax + total.localTax)}`}
                            hint={`소득세 ${won(total.incomeTax)} + 지방세 ${won(total.localTax)}`}
                        />
                        <Tile label="실수령 추정" value={won(total.net)} hint="지급액 − 3.3%" strong />
                        <Tile
                            label="운행"
                            value={`${total.workDays.toLocaleString()}일 · ${total.trips.toLocaleString()}회`}
                            hint={best ? `최고 ${best.label} ${won(best.gross)}` : ""}
                        />
                    </div>

                    {/* 월별 그래프 */}
                    <div style={S.card}>
                        <div style={S.cardTitle}>월별 지급액 (세전)</div>
                        <div style={{ width: "100%", height: 260 }}>
                            <ResponsiveContainer>
                                <BarChart data={rows} margin={{ top: 8, right: 8, bottom: 0, left: 0 }} barCategoryGap="28%">
                                    <CartesianGrid vertical={false} stroke={GRID} />
                                    <XAxis dataKey="label" tickLine={false} axisLine={{ stroke: "#d1d5db" }} tick={{ fill: MUTED, fontSize: 12 }} />
                                    <YAxis
                                        tickFormatter={manwon}
                                        tickLine={false}
                                        axisLine={false}
                                        tick={{ fill: MUTED, fontSize: 12 }}
                                        width={44}
                                    />
                                    <Tooltip cursor={{ fill: "rgba(22,163,74,0.08)" }} content={<MonthTooltip />} />
                                    <Bar dataKey="gross" fill={ACCENT} radius={[4, 4, 0, 0]} maxBarSize={36} />
                                </BarChart>
                            </ResponsiveContainer>
                        </div>
                    </div>

                    {/* 월별 표 */}
                    <div style={S.card}>
                        <div style={S.cardTitle}>월별 상세</div>
                        <div style={{ overflowX: "auto" }}>
                            <table style={S.table}>
                                <thead>
                                    <tr>
                                        <th style={S.thL}>월</th>
                                        <th style={S.th}>일수</th>
                                        <th style={S.th}>횟수</th>
                                        <th style={S.th}>지급액(세전)</th>
                                        <th style={S.th}>소득세 3%</th>
                                        <th style={S.th}>지방세 0.3%</th>
                                        <th style={S.th}>실수령 추정</th>
                                        <th style={S.th}>원본</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {filled.map((r) => (
                                        <tr key={r.month}>
                                            <td style={S.tdL}>{r.label}</td>
                                            <td style={S.td}>{r.workDays}일</td>
                                            <td style={S.td}>{r.trips}회</td>
                                            <td style={S.td}>{won(r.gross)}</td>
                                            <td style={S.tdMuted}>−{won(r.incomeTax)}</td>
                                            <td style={S.tdMuted}>−{won(r.localTax)}</td>
                                            <td style={{ ...S.td, fontWeight: 700 }}>{won(r.net)}</td>
                                            <td style={S.td}>
                                                <a href={settlementPdfUrl(r.file!.filename)} target="_blank" rel="noreferrer" style={S.link}>
                                                    PDF
                                                </a>
                                                <button onClick={() => onDelete(r.file!)} style={S.delBtn} title="삭제" aria-label={`${r.label} 삭제`}>
                                                    ✕
                                                </button>
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                                <tfoot>
                                    <tr>
                                        <td style={S.tfL}>합계</td>
                                        <td style={S.tf}>{total.workDays}일</td>
                                        <td style={S.tf}>{total.trips}회</td>
                                        <td style={S.tf}>{won(total.gross)}</td>
                                        <td style={S.tf}>−{won(total.incomeTax)}</td>
                                        <td style={S.tf}>−{won(total.localTax)}</td>
                                        <td style={S.tf}>{won(total.net)}</td>
                                        <td style={S.tf} />
                                    </tr>
                                </tfoot>
                            </table>
                        </div>
                        <p style={S.footnote}>
                            * 명세서의 지급금액을 세전으로 보고 소득세 3%·지방소득세 0.3%(각 10원 미만 절사)를 뺀 추정치입니다.
                            실제 원천징수는 지급 건 단위로 계산되므로(소득세 1,000원 미만이면 징수하지 않음) 통장 입금액과 조금 다를 수 있습니다.
                        </p>
                    </div>
                </>
            )}

            {/* 업로드 */}
            <div style={S.card}>
                <div style={S.cardTitle}>명세서 올리기</div>
                <div style={S.uploadRow}>
                    <label style={{ ...S.field, flex: 1, minWidth: 220 }}>
                        <span style={S.label}>PDF (여러 개 선택 가능 · 귀속월 자동 인식)</span>
                        <input
                            ref={inputRef}
                            type="file"
                            multiple
                            accept="application/pdf,.pdf"
                            disabled={uploading}
                            onChange={(e) => onPick(e.target.files)}
                            style={S.input}
                        />
                    </label>
                    <label style={S.field}>
                        <span style={S.label}>월 직접 지정 (인식 실패 시만)</span>
                        <input type="month" value={manualMonth} onChange={(e) => setManualMonth(e.target.value)} style={S.input} />
                    </label>
                    {uploading && <span style={{ color: MUTED, fontSize: 14, paddingBottom: 8 }}>올리는 중…</span>}
                </div>
            </div>

            {/* 합계에서 빠진 파일 */}
            {extras.length > 0 && (
                <div style={S.card}>
                    <div style={S.cardTitle}>합계에서 빠진 파일</div>
                    {extras.map((f) => (
                        <div key={f.filename} style={S.extraRow}>
                            <span style={{ minWidth: 0, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                                {f.month} · {f.originalName}
                            </span>
                            <span style={{ color: MUTED, fontSize: 12, flexShrink: 0 }}>
                                {f.duplicate ? "같은 월의 최신 파일이 있음" : "금액을 읽지 못함"}
                            </span>
                            <a href={settlementPdfUrl(f.filename)} target="_blank" rel="noreferrer" style={S.link}>
                                PDF
                            </a>
                            <button onClick={() => onDelete(f)} style={S.delBtn} title="삭제" aria-label="삭제">
                                ✕
                            </button>
                        </div>
                    ))}
                </div>
            )}

            <style>{`
                @media (max-width: 860px) { .hs-tiles { grid-template-columns: repeat(2, minmax(0, 1fr)) !important; } }
                @media (max-width: 480px) { .hs-tiles { gap: 8px !important; } .hs-tiles > div { padding: 12px !important; } }
            `}</style>
        </div>
    );
}

function Tile({ label, value, hint, strong }: { label: string; value: string; hint?: string; strong?: boolean }) {
    return (
        <div style={{ ...S.tile, ...(strong ? { borderColor: ACCENT, background: "#f0fdf4" } : null) }}>
            <div style={S.tileLabel}>{label}</div>
            <div style={S.tileValue}>{value}</div>
            {hint && <div style={S.tileHint}>{hint}</div>}
        </div>
    );
}

function MonthTooltip({ active, payload }: { active?: boolean; payload?: { payload: MonthRow }[] }) {
    if (!active || !payload?.length) return null;
    const r = payload[0].payload;
    return (
        <div style={S.tooltip}>
            <div style={{ fontWeight: 700, marginBottom: 4 }}>{r.label}</div>
            {r.file ? (
                <>
                    <div>지급액 {won(r.gross)}</div>
                    <div style={{ color: MUTED }}>3.3% −{won(r.incomeTax + r.localTax)}</div>
                    <div style={{ fontWeight: 700 }}>실수령 {won(r.net)}</div>
                    <div style={{ color: MUTED, marginTop: 4 }}>
                        {r.workDays}일 · {r.trips}회
                    </div>
                </>
            ) : (
                <div style={{ color: MUTED }}>명세서 없음</div>
            )}
        </div>
    );
}

const S: Record<string, CSSProperties> = {
    page: { padding: 24, maxWidth: 1100 },
    headerRow: { display: "flex", justifyContent: "space-between", alignItems: "flex-end", gap: 12, flexWrap: "wrap", marginBottom: 16 },
    title: { margin: 0, fontSize: 22, color: INK },
    sub: { margin: "6px 0 0", color: MUTED, fontSize: 14 },
    yearTabs: { display: "flex", gap: 4, background: "#f3f4f6", padding: 3, borderRadius: 8 },
    yearTab: { border: "none", background: "transparent", padding: "6px 12px", borderRadius: 6, cursor: "pointer", color: MUTED, fontSize: 14 },
    yearTabOn: { background: "#fff", color: INK, fontWeight: 700, boxShadow: "0 1px 2px rgba(0,0,0,0.08)" },
    tiles: { display: "grid", gridTemplateColumns: "repeat(4, minmax(0, 1fr))", gap: 12, marginBottom: 16 },
    tile: { border: "1px solid #e5e7eb", borderRadius: 10, padding: "14px 16px", background: "#fff", minWidth: 0 },
    tileLabel: { fontSize: 12, color: MUTED, fontWeight: 600 },
    tileValue: { fontSize: "clamp(16px, 4.6vw, 22px)", fontWeight: 800, color: INK, marginTop: 6, fontVariantNumeric: "tabular-nums", wordBreak: "keep-all", overflowWrap: "anywhere" },
    tileHint: { fontSize: 12, color: MUTED, marginTop: 4 },
    card: { border: "1px solid #e5e7eb", borderRadius: 10, padding: 16, marginBottom: 16, background: "#fff" },
    cardTitle: { fontWeight: 700, fontSize: 15, color: INK, marginBottom: 12 },
    table: { width: "100%", borderCollapse: "collapse", fontSize: 14, fontVariantNumeric: "tabular-nums", minWidth: 640 },
    th: { textAlign: "right", padding: "8px 10px", color: MUTED, fontWeight: 600, fontSize: 12, borderBottom: "1px solid #e5e7eb", whiteSpace: "nowrap" },
    thL: { textAlign: "left", padding: "8px 10px", color: MUTED, fontWeight: 600, fontSize: 12, borderBottom: "1px solid #e5e7eb" },
    td: { textAlign: "right", padding: "9px 10px", borderBottom: "1px solid #f3f4f6", color: INK, whiteSpace: "nowrap" },
    tdMuted: { textAlign: "right", padding: "9px 10px", borderBottom: "1px solid #f3f4f6", color: MUTED, whiteSpace: "nowrap" },
    tdL: { textAlign: "left", padding: "9px 10px", borderBottom: "1px solid #f3f4f6", color: INK, fontWeight: 600 },
    tf: { textAlign: "right", padding: "10px", fontWeight: 800, color: INK, borderTop: "2px solid #e5e7eb", whiteSpace: "nowrap" },
    tfL: { textAlign: "left", padding: "10px", fontWeight: 800, color: INK, borderTop: "2px solid #e5e7eb" },
    footnote: { margin: "12px 0 0", fontSize: 12, color: MUTED, lineHeight: 1.6 },
    link: { fontSize: 13, color: ACCENT, textDecoration: "none", fontWeight: 600 },
    delBtn: { border: "none", background: "transparent", cursor: "pointer", color: "#9ca3af", fontSize: 13, marginLeft: 8, padding: 2 },
    uploadRow: { display: "flex", gap: 12, alignItems: "flex-end", flexWrap: "wrap" },
    field: { display: "flex", flexDirection: "column", gap: 4 },
    label: { fontSize: 12, color: MUTED, fontWeight: 600 },
    input: { padding: "7px 8px", border: "1px solid #d1d5db", borderRadius: 6, fontSize: 14 },
    extraRow: { display: "flex", alignItems: "center", gap: 10, padding: "6px 0", fontSize: 14, borderBottom: "1px solid #f3f4f6" },
    error: { padding: "10px 12px", background: "#fef2f2", color: "#b91c1c", border: "1px solid #fecaca", borderRadius: 8, marginBottom: 16, fontSize: 14, whiteSpace: "pre-line" },
    notice: { padding: "10px 12px", background: "#f0fdf4", color: "#166534", border: "1px solid #bbf7d0", borderRadius: 8, marginBottom: 16, fontSize: 14 },
    tooltip: { background: "#fff", border: "1px solid #e5e7eb", borderRadius: 8, padding: "8px 10px", fontSize: 13, boxShadow: "0 4px 12px rgba(0,0,0,0.08)", color: INK },
    empty: { padding: 32, color: "#9ca3af", fontSize: 14, textAlign: "center" },
};
