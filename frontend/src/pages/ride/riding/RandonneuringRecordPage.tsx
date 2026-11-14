// 랜도너스 공식 기록: KORA 참가 이력(완주/DNF), 완주 시간과 GPS 라이딩 연결.
// 관리자는 KORA 기록 API 응답 JSON 을 붙여 넣어 새 기록을 가져온다(원본 id 로 중복 없이 갱신).
import {useEffect, useMemo, useState} from "react";
import type {CSSProperties} from "react";
import {Link} from "react-router-dom";
import {useAdmin} from "@/lib/admin";
import {fetchRandonneuring, importKora} from "@/api/randonneuring";
import type {ImportResult, RandonneuringSummary} from "@/api/randonneuring";
import {RIDE_TYPE_COLOR, RIDE_TYPE_LABEL} from "@/constants/rideType";

const hm = (sec: number) => `${Math.floor(sec / 3600)}시간 ${String(Math.floor((sec % 3600) / 60)).padStart(2, "0")}분`;

export default function RandonneuringRecordPage() {
    const {isAdmin} = useAdmin();
    const [data, setData] = useState<RandonneuringSummary | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [year, setYear] = useState("all");
    const [json, setJson] = useState("");
    const [importing, setImporting] = useState(false);
    const [result, setResult] = useState<ImportResult | null>(null);
    const [importError, setImportError] = useState<string | null>(null);

    const load = () =>
        fetchRandonneuring()
            .then(setData)
            .catch(() => setError("기록을 불러오지 못했습니다."));

    useEffect(() => {
        void load();
    }, []);

    const years = useMemo(
        () => [...new Set((data?.items ?? []).map((r) => r.eventDate.slice(0, 4)))].sort().reverse(),
        [data],
    );
    const rows = (data?.items ?? []).filter((r) => year === "all" || r.eventDate.startsWith(year));

    const onImport = async () => {
        setImportError(null);
        setResult(null);
        let parsed: unknown;
        try {
            parsed = JSON.parse(json);
        } catch {
            setImportError("JSON 형식이 아닙니다. KORA 기록 API 응답 전체를 그대로 붙여 넣어 주세요.");
            return;
        }
        setImporting(true);
        try {
            setResult(await importKora(parsed));
            setJson("");
            await load();
        } catch (e) {
            const res = (e as { response?: { status?: number; data?: { message?: string } } }).response;
            setImportError(
                res?.status === 401 || res?.status === 403
                    ? "관리자 로그인이 필요합니다."
                    : res?.data?.message ?? `가져오기에 실패했습니다${res?.status ? ` (HTTP ${res.status})` : ""}.`,
            );
        } finally {
            setImporting(false);
        }
    };

    return (
        <div style={S.page}>
            <h2 style={S.title}>🏅 랜도너스 기록</h2>
            {data?.career && <p style={S.career}>{data.career}</p>}
            {error && <p style={S.error}>{error}</p>}

            <div style={S.toolbar}>
                <select value={year} onChange={(e) => setYear(e.target.value)} style={S.select}>
                    <option value="all">전체 연도</option>
                    {years.map((y) => (
                        <option key={y} value={y}>{y}년</option>
                    ))}
                </select>
                <span style={S.muted}>{rows.length}건</span>
            </div>

            <div style={S.tableWrap}>
                <table style={S.table}>
                    <thead>
                    <tr>
                        <th style={S.th}>날짜</th>
                        <th style={S.th}>유형</th>
                        <th style={S.th}>코스</th>
                        <th style={S.thNum}>거리</th>
                        <th style={S.th}>결과</th>
                        <th style={S.thNum}>완주 시간</th>
                        <th style={S.thNum}>공식 평균</th>
                        <th style={S.th}>GPS</th>
                    </tr>
                    </thead>
                    <tbody>
                    {rows.map((r) => (
                        <tr key={r.id}>
                            <td style={S.td}>{r.eventDate}</td>
                            <td style={S.td}>
                                <span style={{...S.badge, background: RIDE_TYPE_COLOR[r.eventType] ?? "#64748b"}}>
                                    {RIDE_TYPE_LABEL[r.eventType] ?? r.eventType}
                                </span>
                            </td>
                            <td style={S.td} title={r.notes ?? undefined}>
                                {r.eventName}
                                {r.notes && <span style={S.note}> · {r.notes}</span>}
                            </td>
                            <td style={S.tdNum}>{r.distanceKm != null ? `${r.distanceKm}km` : "-"}</td>
                            <td style={S.td}>
                                <span style={r.status === "FINISHED" ? S.finished : S.dnf}>
                                    {r.status === "FINISHED" ? "완주" : "DNF"}
                                </span>
                            </td>
                            <td style={S.tdNum}>{r.ridingTimeSec ? hm(r.ridingTimeSec) : "-"}</td>
                            <td style={S.tdNum}>
                                {r.ridingTimeSec && r.distanceKm
                                    ? `${(r.distanceKm / (r.ridingTimeSec / 3600)).toFixed(1)}km/h`
                                    : "-"}
                            </td>
                            <td style={S.td}>
                                {r.activityCoreId ? <Link to={`/records/riding/${r.activityCoreId}`}>보기</Link> : "-"}
                            </td>
                        </tr>
                    ))}
                    </tbody>
                </table>
            </div>

            {isAdmin && (
                <section style={S.importBox}>
                    <h3 style={S.subTitle}>KORA 기록 가져오기</h3>
                    <p style={S.muted}>
                        KORA 기록 API 응답 JSON 전체를 붙여 넣으세요. 이미 있는 기록은 원본 id 로 찾아 갱신하고, 새 기록만
                        추가합니다. 같은 날 GPS 라이딩이 있으면 자동으로 연결하고 비어 있는 라이딩 유형도 채웁니다.
                    </p>
                    <textarea
                        value={json}
                        onChange={(e) => setJson(e.target.value)}
                        placeholder='{"ok": true, "items": [ ... ]}'
                        rows={6}
                        style={S.textarea}
                        disabled={importing}
                    />
                    <button type="button" onClick={() => void onImport()} disabled={importing || !json.trim()} style={S.button}>
                        {importing ? "가져오는 중…" : "가져오기"}
                    </button>
                    {importError && <p style={S.error}>{importError}</p>}
                    {result && (
                        <div style={S.result}>
                            전체 {result.total}건: 추가 {result.inserted}, 갱신 {result.updated}, 건너뜀 {result.skipped} ·
                            GPS 연결 {result.linked}, 유형 채움 {result.tagged}
                            {result.warnings.length > 0 && (
                                <ul style={S.warnings}>
                                    {result.warnings.map((w) => <li key={w}>{w}</li>)}
                                </ul>
                            )}
                        </div>
                    )}
                </section>
            )}
        </div>
    );
}

const th: CSSProperties = {textAlign: "left", padding: "8px 10px", borderBottom: "2px solid #e5e7ef", whiteSpace: "nowrap", fontSize: 13, color: "#475569"};
const td: CSSProperties = {padding: "8px 10px", borderBottom: "1px solid #eef0f5", fontSize: 14, verticalAlign: "top"};
const pill: CSSProperties = {display: "inline-block", padding: "2px 8px", borderRadius: 999, fontSize: 12, fontWeight: 700};

const S: Record<string, CSSProperties> = {
    page: {maxWidth: 1100, margin: "0 auto", padding: 16, boxSizing: "border-box"},
    title: {margin: "0 0 8px", fontSize: 20},
    subTitle: {margin: "0 0 6px", fontSize: 16},
    career: {margin: "0 0 12px", padding: "10px 12px", background: "#f8fafc", border: "1px solid #e5e7ef", borderRadius: 10, fontSize: 14, lineHeight: 1.6},
    toolbar: {display: "flex", alignItems: "center", gap: 10, marginBottom: 8},
    select: {padding: "6px 8px", borderRadius: 8, border: "1px solid #d6d9e4"},
    muted: {color: "#6b7186", fontSize: 13, margin: "0 0 8px"},
    tableWrap: {overflowX: "auto"},
    table: {width: "100%", borderCollapse: "collapse"},
    th,
    thNum: {...th, textAlign: "right"},
    td,
    tdNum: {...td, textAlign: "right", whiteSpace: "nowrap", fontVariantNumeric: "tabular-nums"},
    badge: {...pill, color: "#fff", fontWeight: 600},
    finished: {...pill, background: "#dcfce7", color: "#166534"},
    dnf: {...pill, background: "#fee2e2", color: "#991b1b"},
    note: {color: "#8a90a6", fontSize: 12},
    importBox: {marginTop: 24, padding: 16, border: "1px solid #e5e7ef", borderRadius: 12},
    textarea: {width: "100%", boxSizing: "border-box", fontFamily: "monospace", fontSize: 12, padding: 10, borderRadius: 8, border: "1px solid #d6d9e4"},
    button: {marginTop: 8, padding: "8px 18px", border: "none", borderRadius: 8, background: "#2563eb", color: "#fff", fontWeight: 700, cursor: "pointer"},
    result: {marginTop: 10, padding: "10px 12px", background: "#eff6ff", borderRadius: 8, fontSize: 14},
    warnings: {margin: "6px 0 0", paddingLeft: 18, color: "#92400e", fontSize: 13},
    error: {color: "#b91c1c", fontSize: 14},
};
