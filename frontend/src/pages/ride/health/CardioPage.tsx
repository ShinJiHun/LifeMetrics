// src/pages/ride/health/CardioPage.tsx
// 유산소 모아보기: 헬스장 유산소(시트 '유산소' 탭/앱 입력) + 라이딩 + 폰 걷기를 주 단위로 본다.
import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import "@/styles/global.css";
import "@/styles/cardio.css";
import CardioRecommendCard from "./CardioRecommendCard";

const API_BASE = import.meta.env.VITE_API_URL || "";
const WEEKLY_GYM_GOAL = 3;   // 헬스장 유산소 주 3회 목표
const TREND_WEEKS = 8;

type Source = "GYM" | "RIDE" | "WALK";

interface CardioEntry {
    source: Source;
    date: string;
    startTime: string | null;
    name: string;
    durationMin: number | null;
    distanceKm: number | null;
    kcal: number | null;
    avgHr: number | null;
    memo: string | null;
    refId: number | null;
    sameDayWeights: boolean | null;
}

const SOURCE_META: Record<Source, { label: string; icon: string; cls: string }> = {
    GYM: { label: "헬스장", icon: "🏃", cls: "gym" },
    RIDE: { label: "라이딩", icon: "🚴", cls: "ride" },
    WALK: { label: "걷기", icon: "🚶", cls: "walk" },
};
const SOURCES: Source[] = ["GYM", "RIDE", "WALK"];
const RIDE_TYPE_KO: Record<string, string> = {
    PERMANENT: "퍼머넌트", BREVET: "브레베", DDAREUNGI: "따릉이", TOURING: "투어링", FLECHE: "플레쉬", GENERAL: "일반",
};
const DAY_KO = ["월", "화", "수", "목", "금", "토", "일"];

// ── 날짜 유틸 (로컬 기준, 월요일 시작 주) ──
const ymd = (d: Date) =>
    `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
const addDays = (d: Date, n: number) => new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);
const mondayOf = (d: Date) => addDays(d, -((d.getDay() + 6) % 7));
const parseYmd = (s: string) => { const [y, m, d] = s.split("-").map(Number); return new Date(y, m - 1, d); };

const fmtMin = (min: number) => {
    const h = Math.floor(min / 60), m = Math.round(min % 60);
    return h ? `${h}시간 ${m}분` : `${m}분`;
};
const fmtDur = (min: number | null) => {
    if (min == null) return null;
    const totalSec = Math.round(min * 60);
    const h = Math.floor(totalSec / 3600), m = Math.floor((totalSec % 3600) / 60), s = totalSec % 60;
    if (h) return `${h}시간 ${m}분`;
    return s ? `${m}분 ${s}초` : `${m}분`;
};

export default function CardioPage() {
    const thisMonday = useMemo(() => mondayOf(new Date()), []);
    const [weekStart, setWeekStart] = useState<Date>(thisMonday);
    const [entries, setEntries] = useState<CardioEntry[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const weekEnd = addDays(weekStart, 6);
    const trendStart = addDays(weekStart, -7 * (TREND_WEEKS - 1));
    const isThisWeek = ymd(weekStart) === ymd(thisMonday);

    useEffect(() => {
        setLoading(true);
        setError(null);
        fetch(`${API_BASE}/api/exercise/cardio?from=${ymd(trendStart)}&to=${ymd(weekEnd)}&userId=1`)
            .then((r) => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json(); })
            .then((data: CardioEntry[]) => setEntries(data))
            .catch((e) => { console.error(e); setError("유산소 기록을 불러오지 못했습니다."); })
            .finally(() => setLoading(false));
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [ymd(weekStart)]);

    const weekEntries = useMemo(
        () => entries.filter((e) => e.date >= ymd(weekStart) && e.date <= ymd(weekEnd)),
        // eslint-disable-next-line react-hooks/exhaustive-deps
        [entries, ymd(weekStart)],
    );

    // ── 이번 주 요약 ──
    const gymDays = new Set(weekEntries.filter((e) => e.source === "GYM").map((e) => e.date));
    const minutesBySource = SOURCES.reduce((acc, s) => {
        acc[s] = weekEntries.filter((e) => e.source === s).reduce((m, e) => m + (e.durationMin || 0), 0);
        return acc;
    }, {} as Record<Source, number>);
    const totalMinutes = SOURCES.reduce((m, s) => m + minutesBySource[s], 0);
    const gymEntries = weekEntries.filter((e) => e.source === "GYM");
    const gymKm = gymEntries.reduce((m, e) => m + (e.distanceKm || 0), 0);
    const gymKcal = gymEntries.reduce((m, e) => m + (e.kcal || 0), 0);
    const goalPct = Math.min(100, (gymDays.size / WEEKLY_GYM_GOAL) * 100);

    // ── 요일 줄 ──
    const days = Array.from({ length: 7 }, (_, i) => {
        const d = addDays(weekStart, i);
        const key = ymd(d);
        const list = weekEntries.filter((e) => e.date === key);
        return { key, d, sources: SOURCES.filter((s) => list.some((e) => e.source === s)) };
    });

    // ── 최근 8주 헬스장 유산소 횟수 ──
    const trend = Array.from({ length: TREND_WEEKS }, (_, i) => {
        const ws = addDays(trendStart, i * 7);
        const we = addDays(ws, 6);
        const n = new Set(entries
            .filter((e) => e.source === "GYM" && e.date >= ymd(ws) && e.date <= ymd(we))
            .map((e) => e.date)).size;
        return { ws, n, current: ymd(ws) === ymd(weekStart) };
    });
    // 막대 위 숫자 자리를 남기려고 최대값보다 조금 크게 잡는다
    const trendScale = Math.max(WEEKLY_GYM_GOAL, ...trend.map((t) => t.n)) * 1.25;

    // ── 날짜별 목록 ──
    const byDate = useMemo(() => {
        const m = new Map<string, CardioEntry[]>();
        for (const e of weekEntries) { if (!m.has(e.date)) m.set(e.date, []); m.get(e.date)!.push(e); }
        return [...m.entries()].sort((a, b) => b[0].localeCompare(a[0]));
    }, [weekEntries]);

    const weekLabel = `${weekStart.getMonth() + 1}/${weekStart.getDate()} – ${weekEnd.getMonth() + 1}/${weekEnd.getDate()}`;

    return (
        <div className="cardio-page">
            <h2>🫀 유산소</h2>

            <CardioRecommendCard />

            <div className="cardio-week-nav">
                <button onClick={() => setWeekStart(addDays(weekStart, -7))} aria-label="이전 주">◀</button>
                <span className="cardio-week-label">{isThisWeek ? "이번 주" : weekLabel}{isThisWeek && <small> {weekLabel}</small>}</span>
                <button onClick={() => setWeekStart(addDays(weekStart, 7))} disabled={isThisWeek} aria-label="다음 주">▶</button>
            </div>

            {error && <div className="cardio-error">{error}</div>}

            <section className="cardio-summary">
                <div className="cardio-card cardio-goal">
                    <div className="cardio-card-label">헬스장 유산소</div>
                    <div className="cardio-goal-value">
                        <strong>{gymDays.size}</strong><span>/{WEEKLY_GYM_GOAL}회</span>
                        {gymDays.size >= WEEKLY_GYM_GOAL && <span className="cardio-goal-done">달성 🎉</span>}
                    </div>
                    <div className="cardio-progress"><div style={{ width: `${goalPct}%` }} /></div>
                    <div className="cardio-card-sub">
                        {gymEntries.length > 0
                            ? `${fmtMin(minutesBySource.GYM)}${gymKm ? ` · ${Math.round(gymKm * 100) / 100}km` : ""}${gymKcal ? ` · ${gymKcal}kcal` : ""}`
                            : "아직 기록 없음"}
                    </div>
                </div>

                <div className="cardio-card">
                    <div className="cardio-card-label">총 유산소 시간</div>
                    <div className="cardio-total">{fmtMin(totalMinutes)}</div>
                    {totalMinutes > 0 && (
                        <div className="cardio-stack" role="img" aria-label="출처별 시간">
                            {SOURCES.filter((s) => minutesBySource[s] > 0).map((s) => (
                                <div key={s} className={`seg ${SOURCE_META[s].cls}`}
                                     style={{ width: `${(minutesBySource[s] / totalMinutes) * 100}%` }} />
                            ))}
                        </div>
                    )}
                    <div className="cardio-legend">
                        {SOURCES.map((s) => (
                            <span key={s} className={minutesBySource[s] ? "" : "muted"}>
                                <i className={`dot ${SOURCE_META[s].cls}`} />{SOURCE_META[s].label} {fmtMin(minutesBySource[s])}
                            </span>
                        ))}
                    </div>
                </div>
            </section>

            <section className="cardio-days">
                {days.map(({ key, d, sources }, i) => (
                    <div key={key} className={`cardio-day ${key === ymd(new Date()) ? "today" : ""}`}>
                        <span className="dow">{DAY_KO[i]}</span>
                        <span className="dnum">{d.getDate()}</span>
                        <span className="icons">{sources.length ? sources.map((s) => SOURCE_META[s].icon).join("") : "·"}</span>
                    </div>
                ))}
            </section>

            <section className="cardio-trend">
                <div className="cardio-section-title">최근 {TREND_WEEKS}주 헬스장 유산소 <small>(목표 주 {WEEKLY_GYM_GOAL}회)</small></div>
                <div className="cardio-trend-plot">
                    <div className="goal-line" style={{ bottom: `${(WEEKLY_GYM_GOAL / trendScale) * 100}%` }}>
                        <span>목표 {WEEKLY_GYM_GOAL}</span>
                    </div>
                    {trend.map((t) => (
                        <button key={ymd(t.ws)} className={`bar-col ${t.current ? "current" : ""}`}
                                onClick={() => setWeekStart(t.ws)} title={`${t.ws.getMonth() + 1}/${t.ws.getDate()} 주: ${t.n}회`}>
                            <span className={`bar ${t.n >= WEEKLY_GYM_GOAL ? "met" : ""}`} style={{ height: `${(t.n / trendScale) * 100}%` }} />
                            <span className="bar-n" style={{ bottom: `calc(${(t.n / trendScale) * 100}% + 2px)` }}>{t.n}</span>
                        </button>
                    ))}
                </div>
                <div className="cardio-trend-labels">
                    {trend.map((t) => (
                        <span key={ymd(t.ws)} className={t.current ? "current" : ""}>{t.ws.getMonth() + 1}/{t.ws.getDate()}</span>
                    ))}
                </div>
            </section>

            {loading ? (
                <div className="loading">로딩 중...</div>
            ) : byDate.length === 0 ? (
                <div className="empty-state"><p>📭 이 주에는 유산소 기록이 없습니다</p></div>
            ) : (
                <section className="cardio-list">
                    {byDate.map(([date, list]) => {
                        const d = parseYmd(date);
                        const weights = list.some((e) => e.sameDayWeights);
                        return (
                            <div key={date} className="cardio-date-group">
                                <div className="cardio-date">
                                    {d.getMonth() + 1}월 {d.getDate()}일 ({DAY_KO[(d.getDay() + 6) % 7]})
                                    {weights && <Link to="/records/health/history" className="weights-badge">🏋️ 웨이트도 함</Link>}
                                </div>
                                {list.map((e, idx) => {
                                    const meta = SOURCE_META[e.source];
                                    const memo = e.source === "RIDE" ? (e.memo ? RIDE_TYPE_KO[e.memo] || e.memo : null) : e.memo;
                                    const body = (
                                        <>
                                            <div className="cardio-entry-head">
                                                <span className={`src-badge ${meta.cls}`}>{meta.icon} {meta.label}</span>
                                                <span className="cardio-name">{e.name}</span>
                                                {e.startTime && <span className="cardio-time">{e.startTime}</span>}
                                            </div>
                                            <div className="cardio-metrics">
                                                {fmtDur(e.durationMin) && <span>⏱ {fmtDur(e.durationMin)}</span>}
                                                {e.distanceKm != null && <span>📏 {e.distanceKm}km</span>}
                                                {e.kcal != null && <span>🔥 {e.kcal}kcal</span>}
                                                {e.avgHr != null && <span>❤️ {e.avgHr}bpm</span>}
                                            </div>
                                            {memo && <div className="cardio-memo">{memo}</div>}
                                        </>
                                    );
                                    return e.source === "RIDE" && e.refId ? (
                                        <Link key={idx} to={`/records/riding/${e.refId}`} className={`cardio-entry ${meta.cls} link`}>{body}</Link>
                                    ) : (
                                        <div key={idx} className={`cardio-entry ${meta.cls}`}>{body}</div>
                                    );
                                })}
                            </div>
                        );
                    })}
                </section>
            )}
        </div>
    );
}
