// 인간 페르소나 — 핸들러 핸들 기록 (관리자 전용).
// 캡처 1장을 올리면 서버가 날짜·출발지·도착지를 읽고, 카카오 길찾기(가장 빠른 길)로 경로를 만든다.
// 미리보기에서 주소를 고치고 "위치 다시 찾기" → 저장. 위쪽 지도는 저장된 모든 핸들 경로.
import { useEffect, useMemo, useRef, useState } from "react";
import type { CSSProperties } from "react";
import { Link } from "react-router-dom";
import { shrinkImage } from "@/lib/shrinkImage";
import {
    deleteTrip,
    extractTrip,
    fetchTrips,
    resolveTrip,
    saveTrip,
    tripImageUrl,
    type HandlerTrip,
    type TripDraft,
    type TripPreview,
} from "@/api/handlerTrips";
import HandlerTripsMap, { type MapTrip } from "./HandlerTripsMap";

const ACCENT = "#2563eb";
const INK = "#111827";
const MUTED = "#6b7280";

const won = (n: number | null | undefined) => (n == null ? "–" : `${n.toLocaleString("ko-KR")}원`);
const km = (m: number | null | undefined) => (m == null ? "–" : `${(m / 1000).toFixed(1)}km`);
const mins = (s: number | null | undefined) => (s == null ? "–" : `${Math.round(s / 60)}분`);

function errorMessage(e: unknown): string {
    const anyErr = e as { response?: { status?: number; data?: { message?: string } }; code?: string };
    if (anyErr.response?.status === 403) return "관리자 로그인이 필요합니다.";
    if (anyErr.code === "ECONNABORTED") return "시간이 너무 오래 걸렸습니다. 다시 시도해 주세요.";
    return anyErr.response?.data?.message || "요청에 실패했습니다.";
}

const ROUTE_LABEL: Record<string, string> = { kakao: "카카오 최단시간", osrm: "OSRM", straight: "직선" };

export default function HandlerTripsPage() {
    const [trips, setTrips] = useState<HandlerTrip[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [notice, setNotice] = useState<string | null>(null);
    const [month, setMonth] = useState<string>("all");
    const [selected, setSelected] = useState<number | string | null>(null);

    // 업로드 → 미리보기
    const [file, setFile] = useState<File | null>(null);
    const [imgUrl, setImgUrl] = useState<string | null>(null);
    const [busy, setBusy] = useState<null | "extract" | "resolve" | "save">(null);
    const [preview, setPreview] = useState<TripPreview | null>(null);
    const [draft, setDraft] = useState<TripDraft | null>(null);
    const [dirtyPlace, setDirtyPlace] = useState(false);
    const inputRef = useRef<HTMLInputElement>(null);
    // 핸들러 화면엔 연도가 없어 올리기 전에 고른다 (기본 올해, 마지막 선택 기억)
    const thisYear = new Date().getFullYear();
    const [year, setYear] = useState<number>(() => {
        try {
            const y = Number(localStorage.getItem("handlerTrip.year"));
            return y >= 2022 && y <= thisYear ? y : thisYear;
        } catch {
            return thisYear;
        }
    });
    const yearOptions = Array.from({ length: thisYear - 2022 + 1 }, (_, i) => 2022 + i); // 핸들러 시작 2022년부터
    const pickYear = (y: number) => {
        setYear(y);
        try {
            localStorage.setItem("handlerTrip.year", String(y));
        } catch {
            /* 저장 못 해도 동작엔 지장 없음 */
        }
        // 미리보기 중이면 날짜의 연도도 바꾼다
        setDraft((d) => (d?.tripDate ? { ...d, tripDate: `${y}${d.tripDate.slice(4)}` } : d));
    };

    const load = async () => {
        setLoading(true);
        try {
            setTrips(await fetchTrips());
        } catch (e) {
            setError(errorMessage(e));
        } finally {
            setLoading(false);
        }
    };
    useEffect(() => {
        load();
    }, []);

    const months = useMemo(() => [...new Set(trips.map((t) => t.tripDate.slice(0, 7)))].sort().reverse(), [trips]);
    const shown = useMemo(() => (month === "all" ? trips : trips.filter((t) => t.tripDate.startsWith(month))), [trips, month]);
    const totals = useMemo(
        () => ({
            n: shown.length,
            dist: shown.reduce((a, t) => a + (t.routeDistanceM ?? 0), 0),
            fare: shown.reduce((a, t) => a + (t.fare ?? 0), 0),
            days: new Set(shown.map((t) => t.tripDate)).size,
        }),
        [shown],
    );
    const mapTrips: MapTrip[] = useMemo(
        () =>
            shown.map((t) => ({
                id: t.id,
                polyline: t.polyline,
                origin: t.originLon != null && t.originLat != null ? [t.originLon, t.originLat] : null,
                dest: t.destLon != null && t.destLat != null ? [t.destLon, t.destLat] : null,
            })),
        [shown],
    );

    const previewMap: MapTrip[] = useMemo(() => {
        if (!preview) return [];
        return [
            {
                id: "preview",
                polyline: preview.route?.polyline ?? null,
                origin: preview.origin ? [preview.origin.lon, preview.origin.lat] : null,
                dest: preview.dest ? [preview.dest.lon, preview.dest.lat] : null,
            },
        ];
    }, [preview]);

    const reset = () => {
        setFile(null);
        if (imgUrl) URL.revokeObjectURL(imgUrl);
        setImgUrl(null);
        setPreview(null);
        setDraft(null);
        setDirtyPlace(false);
        if (inputRef.current) inputRef.current.value = "";
    };

    const onPick = async (picked: File | undefined) => {
        if (!picked) return;
        setError(null);
        setNotice(null);
        setPreview(null);
        setBusy("extract");
        try {
            const small = await shrinkImage(picked);
            setFile(small);
            if (imgUrl) URL.revokeObjectURL(imgUrl);
            setImgUrl(URL.createObjectURL(small));
            const p = await extractTrip(small, year);
            setPreview(p);
            setDraft(p.draft);
            setDirtyPlace(false);
        } catch (e) {
            setError(errorMessage(e));
        } finally {
            setBusy(null);
        }
    };

    const onResolve = async () => {
        if (!draft) return;
        setBusy("resolve");
        setError(null);
        try {
            const p = await resolveTrip(draft);
            setPreview({ ...p, raw: preview?.raw ?? null });
            setDirtyPlace(false);
        } catch (e) {
            setError(errorMessage(e));
        } finally {
            setBusy(null);
        }
    };

    const onSave = async () => {
        if (!draft) return;
        if (preview?.duplicateId && !confirm("이미 저장된 핸들 같습니다. 그래도 저장할까요?")) return;
        setBusy("save");
        setError(null);
        try {
            const t = await saveTrip(draft, file, preview?.raw ?? null);
            setNotice(`저장됨 · ${t.tripDate} ${t.originName ?? ""} → ${t.destName ?? ""} (${km(t.routeDistanceM)})`);
            reset();
            await load();
            setSelected(t.id);
        } catch (e) {
            setError(errorMessage(e));
        } finally {
            setBusy(null);
        }
    };

    const onDelete = async (t: HandlerTrip) => {
        if (!confirm(`${t.tripDate} ${t.originName ?? ""} → ${t.destName ?? ""} 기록을 삭제할까요?`)) return;
        try {
            await deleteTrip(t.id);
            if (selected === t.id) setSelected(null);
            await load();
        } catch (e) {
            setError(errorMessage(e));
        }
    };

    const set = (k: keyof TripDraft, place = false) => (e: React.ChangeEvent<HTMLInputElement>) => {
        const v = e.target.value;
        const numeric = k === "fare" || k === "distanceKm" || k === "durationMin";
        setDraft((d) => (d ? { ...d, [k]: v === "" ? null : numeric ? Number(v) : v } : d));
        if (place) setDirtyPlace(true);
    };

    return (
        <div style={S.page}>
            <div style={S.headerRow}>
                <div>
                    <h2 style={S.title}>🚗 핸들 기록</h2>
                    <p style={S.sub}>
                        캡처 1장씩 → 출발지·도착지 기준 카카오 최단시간 경로(추정) ·{" "}
                        <Link to="/human/handler-settlement" style={S.link}>
                            월별 정산 →
                        </Link>
                    </p>
                </div>
                {months.length > 0 && (
                    <select value={month} onChange={(e) => setMonth(e.target.value)} style={S.input} aria-label="월">
                        <option value="all">전체 기간</option>
                        {months.map((m) => (
                            <option key={m} value={m}>
                                {m}
                            </option>
                        ))}
                    </select>
                )}
            </div>

            {error && <div style={S.error}>{error}</div>}
            {notice && <div style={S.notice}>{notice}</div>}

            {/* 업로드 / 미리보기 */}
            <div style={S.card}>
                <div style={S.cardTitle}>핸들 1건 올리기</div>
                <div style={S.yearRow}>
                    <span style={S.label}>연도 (캡처에 연도가 없어요)</span>
                    <div style={S.yearTabs} role="radiogroup" aria-label="연도">
                        {yearOptions.map((y) => (
                            <button
                                key={y}
                                role="radio"
                                aria-checked={y === year}
                                disabled={busy != null}
                                onClick={() => pickYear(y)}
                                style={{ ...S.yearTab, ...(y === year ? S.yearTabOn : null) }}
                            >
                                {y}
                            </button>
                        ))}
                    </div>
                </div>
                {!preview ? (
                    <label style={S.field}>
                        <span style={S.label}>핸들 내역 캡처 (이미지 1장)</span>
                        <input
                            ref={inputRef}
                            type="file"
                            accept="image/*"
                            disabled={busy != null}
                            onChange={(e) => onPick(e.target.files?.[0])}
                            style={S.input}
                        />
                        {busy === "extract" && <span style={{ color: MUTED, fontSize: 14 }}>캡처 읽고 경로 찾는 중… (10~20초)</span>}
                    </label>
                ) : (
                    draft && (
                        <div className="ht-preview" style={S.previewGrid}>
                            <div style={{ minWidth: 0 }}>
                                <div style={S.formGrid}>
                                    <F label="날짜" type="date" value={draft.tripDate} onChange={set("tripDate")} />
                                    <F label="제한 시작" type="time" value={draft.startTime} onChange={set("startTime")} />
                                    <F label="제한 끝" type="time" value={draft.endTime} onChange={set("endTime")} />
                                    <F label="종류" value={draft.serviceType} onChange={set("serviceType")} />
                                    <F label="핸들 코드" value={draft.handleCode} onChange={set("handleCode")} />
                                    <F label="차종" value={draft.carModel} onChange={set("carModel")} />
                                    <F label="차량번호" value={draft.carNumber} onChange={set("carNumber")} />
                                    <F label="운행료(원)" type="number" value={draft.fare} onChange={set("fare")} />
                                    <F label="화면 거리(km)" type="number" value={draft.distanceKm} onChange={set("distanceKm")} />
                                </div>
                                <div style={S.placeBlock}>
                                    <span style={{ ...S.dot, background: "#16a34a" }} />
                                    <div style={S.placeFields}>
                                        <F label="출발지" value={draft.originName} onChange={set("originName", true)} />
                                        <F label="출발 주소" value={draft.originAddress} onChange={set("originAddress", true)} wide />
                                        <PlaceHint p={preview.origin} />
                                    </div>
                                </div>
                                <div style={S.placeBlock}>
                                    <span style={{ ...S.dot, background: "#dc2626" }} />
                                    <div style={S.placeFields}>
                                        <F label="도착지" value={draft.destName} onChange={set("destName", true)} />
                                        <F label="도착 주소" value={draft.destAddress} onChange={set("destAddress", true)} wide />
                                        <PlaceHint p={preview.dest} />
                                    </div>
                                </div>
                                <F label="핸들 내용·메모" value={draft.memo} onChange={set("memo")} wide />

                                {preview.route && (
                                    <div style={S.routeSummary}>
                                        경로 {km(preview.route.distanceM)} · 예상 {mins(preview.route.durationS)} ·{" "}
                                        {ROUTE_LABEL[preview.route.source] ?? preview.route.source}
                                    </div>
                                )}
                                {preview.duplicateId && <div style={S.warn}>⚠ 이미 저장된 핸들입니다 (#{preview.duplicateId}, 같은 핸들 코드 또는 같은 시각·출발지)</div>}
                                {preview.warnings.map((w) => (
                                    <div key={w} style={S.warn}>
                                        ⚠ {w}
                                    </div>
                                ))}

                                <div style={S.btnRow}>
                                    <button onClick={onResolve} disabled={busy != null} style={dirtyPlace ? S.btnPrimary : S.btn}>
                                        {busy === "resolve" ? "찾는 중…" : "위치·경로 다시 찾기"}
                                    </button>
                                    <button
                                        onClick={onSave}
                                        disabled={busy != null || dirtyPlace || !preview.origin || !preview.dest || !draft.tripDate}
                                        style={S.btnPrimary}
                                        title={dirtyPlace ? "주소를 고쳤으면 먼저 다시 찾기" : undefined}
                                    >
                                        {busy === "save" ? "저장 중…" : "저장"}
                                    </button>
                                    <button onClick={reset} disabled={busy != null} style={S.btn}>
                                        취소
                                    </button>
                                </div>
                            </div>
                            <div style={{ minWidth: 0, display: "flex", flexDirection: "column", gap: 10 }}>
                                <HandlerTripsMap trips={previewMap} height={300} />
                                {imgUrl && (
                                    <a href={imgUrl} target="_blank" rel="noreferrer">
                                        <img src={imgUrl} alt="올린 캡처" style={S.thumb} />
                                    </a>
                                )}
                            </div>
                        </div>
                    )
                )}
            </div>

            {/* 전체 경로 */}
            <div style={S.card}>
                <div style={{ ...S.cardTitle, display: "flex", justifyContent: "space-between", gap: 8, flexWrap: "wrap" }}>
                    <span>모든 핸들 경로</span>
                    <span style={{ color: MUTED, fontWeight: 500, fontSize: 13 }}>
                        {totals.n}건 · {totals.days}일 · {km(totals.dist)} · {won(totals.fare)}
                        {selected != null && (
                            <button onClick={() => setSelected(null)} style={{ ...S.linkBtn, marginLeft: 8 }}>
                                전체 보기
                            </button>
                        )}
                    </span>
                </div>
                {loading && trips.length === 0 ? (
                    <div style={S.empty}>불러오는 중…</div>
                ) : trips.length === 0 ? (
                    <div style={S.empty}>아직 기록이 없습니다. 위에서 캡처를 올려 주세요.</div>
                ) : (
                    <HandlerTripsMap trips={mapTrips} selectedId={selected} onSelect={setSelected} />
                )}
            </div>

            {/* 목록 */}
            {shown.length > 0 && (
                <div style={S.card}>
                    <div style={S.cardTitle}>기록</div>
                    <div style={{ overflowX: "auto" }}>
                        <table style={S.table}>
                            <thead>
                                <tr>
                                    <th style={S.thL}>날짜</th>
                                    <th style={S.thL}>제한 시간</th>
                                    <th style={S.thL}>출발 → 도착</th>
                                    <th style={S.thL}>종류</th>
                                    <th style={S.th}>경로</th>
                                    <th style={S.th}>운행료</th>
                                    <th style={S.th} />
                                </tr>
                            </thead>
                            <tbody>
                                {shown.map((t) => (
                                    <tr
                                        key={t.id}
                                        onClick={() => setSelected(t.id)}
                                        style={{ cursor: "pointer", background: selected === t.id ? "#eff6ff" : undefined }}
                                    >
                                        <td style={S.tdL}>{t.tripDate.slice(5).replace("-", "/")}</td>
                                        <td style={S.tdMutedL}>
                                            {t.startTime?.slice(0, 5) ?? ""}
                                            {t.endTime ? `–${t.endTime.slice(0, 5)}` : ""}
                                        </td>
                                        <td style={{ ...S.tdL, fontWeight: 400, whiteSpace: "normal", minWidth: 220 }}>
                                            {t.originName ?? t.originAddress} → {t.destName ?? t.destAddress}
                                        </td>
                                        <td style={S.tdMutedL}>
                                            {t.serviceType ?? ""}
                                            {t.handleCode && <span style={{ fontSize: 11, marginLeft: 4 }}>{t.handleCode}</span>}
                                        </td>
                                        <td style={S.td}>
                                            {km(t.routeDistanceM)}
                                            {t.routeSource && t.routeSource !== "kakao" && (
                                                <span style={{ color: MUTED, fontSize: 11 }}> ({ROUTE_LABEL[t.routeSource] ?? t.routeSource})</span>
                                            )}
                                        </td>
                                        <td style={S.td}>{won(t.fare)}</td>
                                        <td style={S.td} onClick={(e) => e.stopPropagation()}>
                                            {t.hasImage && (
                                                <a href={tripImageUrl(t.id)} target="_blank" rel="noreferrer" style={S.link}>
                                                    캡처
                                                </a>
                                            )}
                                            <button onClick={() => onDelete(t)} style={S.delBtn} aria-label="삭제" title="삭제">
                                                ✕
                                            </button>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                    <p style={S.footnote}>* 경로는 실제 주행 기록이 아니라 출발지→도착지 카카오 길찾기(저장 시점 교통 기준 최단시간) 추정입니다.</p>
                </div>
            )}

            <style>{`@media (max-width: 860px) { .ht-preview { grid-template-columns: minmax(0, 1fr) !important; } }`}</style>
        </div>
    );
}

function F({
    label,
    value,
    onChange,
    type = "text",
    wide,
}: {
    label: string;
    value: string | number | null;
    onChange: (e: React.ChangeEvent<HTMLInputElement>) => void;
    type?: string;
    wide?: boolean;
}) {
    return (
        <label style={{ ...S.field, ...(wide ? { gridColumn: "1 / -1" } : null) }}>
            <span style={S.label}>{label}</span>
            <input type={type} value={value ?? ""} onChange={onChange} style={S.input} />
        </label>
    );
}

function PlaceHint({ p }: { p: TripPreview["origin"] }) {
    if (!p) return <div style={{ ...S.hint, color: "#b91c1c" }}>위치를 찾지 못함</div>;
    return (
        <div style={S.hint}>
            📍 {p.placeName ? `${p.placeName} · ` : ""}
            {p.address} {p.source === "keyword" && <span>(장소 검색)</span>}
        </div>
    );
}

const S: Record<string, CSSProperties> = {
    page: { padding: 24, maxWidth: 1100 },
    headerRow: { display: "flex", justifyContent: "space-between", alignItems: "flex-end", gap: 12, flexWrap: "wrap", marginBottom: 16 },
    title: { margin: 0, fontSize: 22, color: INK },
    sub: { margin: "6px 0 0", color: MUTED, fontSize: 14 },
    yearRow: { display: "flex", alignItems: "center", gap: 10, flexWrap: "wrap", marginBottom: 12 },
    yearTabs: { display: "flex", gap: 4, background: "#f3f4f6", padding: 3, borderRadius: 8 },
    yearTab: { border: "none", background: "transparent", padding: "6px 14px", borderRadius: 6, cursor: "pointer", color: MUTED, fontSize: 14 },
    yearTabOn: { background: "#fff", color: INK, fontWeight: 700, boxShadow: "0 1px 2px rgba(0,0,0,0.08)" },
    card: { border: "1px solid #e5e7eb", borderRadius: 10, padding: 16, marginBottom: 16, background: "#fff" },
    cardTitle: { fontWeight: 700, fontSize: 15, color: INK, marginBottom: 12 },
    field: { display: "flex", flexDirection: "column", gap: 4, minWidth: 0 },
    label: { fontSize: 12, color: MUTED, fontWeight: 600 },
    input: { padding: "7px 8px", border: "1px solid #d1d5db", borderRadius: 6, fontSize: 14, minWidth: 0, width: "100%", boxSizing: "border-box" },
    previewGrid: { display: "grid", gridTemplateColumns: "minmax(0, 1.1fr) minmax(0, 1fr)", gap: 16 },
    formGrid: { display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(130px, 1fr))", gap: 10, marginBottom: 12 },
    placeBlock: { display: "flex", gap: 10, alignItems: "flex-start", marginBottom: 12 },
    placeFields: { flex: 1, minWidth: 0, display: "grid", gridTemplateColumns: "minmax(0, 1fr)", gap: 6 },
    dot: { width: 10, height: 10, borderRadius: 5, marginTop: 26, flexShrink: 0 },
    hint: { fontSize: 12, color: MUTED },
    routeSummary: { marginTop: 12, fontSize: 14, fontWeight: 700, color: ACCENT },
    warn: { marginTop: 6, fontSize: 13, color: "#92400e", background: "#fffbeb", border: "1px solid #fde68a", borderRadius: 6, padding: "6px 8px" },
    btnRow: { display: "flex", gap: 8, marginTop: 14, flexWrap: "wrap" },
    btn: { padding: "8px 14px", borderRadius: 6, border: "1px solid #d1d5db", background: "#fff", cursor: "pointer", fontSize: 14 },
    btnPrimary: { padding: "8px 14px", borderRadius: 6, border: "1px solid " + ACCENT, background: ACCENT, color: "#fff", cursor: "pointer", fontSize: 14, fontWeight: 600 },
    thumb: { width: "100%", maxHeight: 360, objectFit: "contain", border: "1px solid #e5e7eb", borderRadius: 8, background: "#f9fafb" },
    table: { width: "100%", borderCollapse: "collapse", fontSize: 14, fontVariantNumeric: "tabular-nums", minWidth: 640 },
    th: { textAlign: "right", padding: "8px 10px", color: MUTED, fontWeight: 600, fontSize: 12, borderBottom: "1px solid #e5e7eb", whiteSpace: "nowrap" },
    thL: { textAlign: "left", padding: "8px 10px", color: MUTED, fontWeight: 600, fontSize: 12, borderBottom: "1px solid #e5e7eb", whiteSpace: "nowrap" },
    td: { textAlign: "right", padding: "9px 10px", borderBottom: "1px solid #f3f4f6", color: INK, whiteSpace: "nowrap" },
    tdL: { textAlign: "left", padding: "9px 10px", borderBottom: "1px solid #f3f4f6", color: INK, fontWeight: 600, whiteSpace: "nowrap" },
    tdMutedL: { textAlign: "left", padding: "9px 10px", borderBottom: "1px solid #f3f4f6", color: MUTED, whiteSpace: "nowrap" },
    footnote: { margin: "12px 0 0", fontSize: 12, color: MUTED, lineHeight: 1.6 },
    link: { fontSize: 13, color: ACCENT, textDecoration: "none", fontWeight: 600 },
    linkBtn: { border: "none", background: "transparent", color: ACCENT, cursor: "pointer", fontSize: 13, fontWeight: 600, padding: 0 },
    delBtn: { border: "none", background: "transparent", cursor: "pointer", color: "#9ca3af", fontSize: 13, marginLeft: 8, padding: 2 },
    empty: { padding: "28px 0", textAlign: "center", color: MUTED, fontSize: 14 },
    error: { padding: "10px 12px", background: "#fef2f2", color: "#b91c1c", border: "1px solid #fecaca", borderRadius: 8, marginBottom: 16, fontSize: 14, whiteSpace: "pre-line" },
    notice: { padding: "10px 12px", background: "#eff6ff", color: "#1e40af", border: "1px solid #bfdbfe", borderRadius: 8, marginBottom: 16, fontSize: 14 },
};
