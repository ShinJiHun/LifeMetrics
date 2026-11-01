import { useEffect, useState } from "react";
import { fetchDailyAnalysis, runDailyAnalysis } from "@/api/dailyAnalysis";
import type { DailyAnalysis, DailyAnalysisInput } from "@/api/dailyAnalysis";

import "@/styles/global.css";
import "@/styles/daily-analysis.css";

/** KST 기준 어제 (yyyy-MM-dd). 오늘은 하루가 끝나지 않아 분석 대상이 아니다. */
function yesterdayKst(): string {
    const kstNow = new Date(Date.now() + 9 * 60 * 60 * 1000);
    kstNow.setUTCDate(kstNow.getUTCDate() - 1);
    return kstNow.toISOString().slice(0, 10);
}

function shiftDate(date: string, days: number): string {
    const d = new Date(`${date}T00:00:00Z`);
    d.setUTCDate(d.getUTCDate() + days);
    return d.toISOString().slice(0, 10);
}

function sum<T>(rows: T[], pick: (row: T) => number | null | undefined): number {
    return rows.reduce((acc, row) => acc + (pick(row) ?? 0), 0);
}

/** 그날 입력에서 값이 있는 지표만 고른다. 재지 않은 항목은 0 으로 보이지 않게 뺀다. */
function statsOf(input: DailyAnalysisInput): { label: string; value: string }[] {
    const stats: { label: string; value: string }[] = [];
    const day = input.dailyActivity[0];
    if (day?.steps != null) stats.push({ label: "걸음 수", value: day.steps.toLocaleString() });
    if (input.rides.length > 0) {
        const km = sum(input.rides, (r) => r.distanceM) / 1000;
        stats.push({ label: "라이딩", value: `${km.toFixed(1)} km` });
    }
    if (input.fitness.length > 0) {
        stats.push({ label: "웨이트 볼륨", value: `${Math.round(sum(input.fitness, (f) => f.totalVolumeKg)).toLocaleString()} kg` });
    }
    if (input.meals.length > 0) {
        stats.push({ label: "입력한 식단", value: `${Math.round(sum(input.meals, (m) => m.calories)).toLocaleString()} kcal` });
    }
    const body = input.bodyRecords[0];
    if (body?.weightKg != null) stats.push({ label: "체중", value: `${body.weightKg} kg` });
    return stats;
}

function BulletCard({ title, items, tone }: { title: string; items?: string[] | null; tone?: string }) {
    if (!items || items.length === 0) return null;
    return (
        <section className={`da-card ${tone ?? ""}`}>
            <h2>{title}</h2>
            <ul>
                {items.map((item) => <li key={item}>{item}</li>)}
            </ul>
        </section>
    );
}

function TextCard({ title, text }: { title: string; text?: string | null }) {
    if (!text) return null;
    return (
        <section className="da-card">
            <h2>{title}</h2>
            <p>{text}</p>
        </section>
    );
}

export default function DailyAnalysisPage() {
    const latest = yesterdayKst();
    const [date, setDate] = useState(latest);
    const [analysis, setAnalysis] = useState<DailyAnalysis | null>(null);
    const [loading, setLoading] = useState(true);
    const [running, setRunning] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [notice, setNotice] = useState<string | null>(null);

    useEffect(() => {
        let stale = false;
        setLoading(true);
        setError(null);
        setNotice(null);
        fetchDailyAnalysis(date)
            .then((res) => { if (!stale) setAnalysis(res.analysis); })
            .catch((e: Error) => { if (!stale) { setAnalysis(null); setError(e.message); } })
            .finally(() => { if (!stale) setLoading(false); });
        return () => { stale = true; };
    }, [date]);

    const run = async () => {
        const target = date;
        setRunning(true);
        setError(null);
        setNotice(null);
        try {
            const res = await runDailyAnalysis(target);
            if (res.analysis) setAnalysis(res.analysis);
            else setNotice("이 날은 분석할 기록이 없습니다.");
        } catch (e) {
            setError((e as Error).message);
        } finally {
            setRunning(false);
        }
    };

    const result = analysis?.result;
    const stats = analysis ? statsOf(analysis.input) : [];

    return (
        <div className="da-page">
            <h1>일일 분석</h1>
            <div className="da-subtitle">
                하루치 기록만으로 분석합니다. 매일 새벽 1시 30분에 전날 분석이 만들어집니다.
            </div>

            <div className="da-toolbar">
                <button type="button" onClick={() => setDate(shiftDate(date, -1))} aria-label="이전 날">‹</button>
                <input
                    type="date"
                    value={date}
                    max={latest}
                    onChange={(e) => { if (e.target.value) setDate(e.target.value); }}
                />
                <button type="button" onClick={() => setDate(shiftDate(date, 1))} disabled={date >= latest} aria-label="다음 날">›</button>
                <button type="button" className="da-run" onClick={run} disabled={running || loading}>
                    {running ? "분석 중…" : analysis ? "다시 분석" : "지금 분석"}
                </button>
            </div>

            {error && <div className="da-state da-error">{error}</div>}
            {notice && <div className="da-state">{notice}</div>}
            {loading && <div className="da-state">불러오는 중…</div>}
            {!loading && !error && !notice && !analysis && (
                <div className="da-state">이 날의 분석이 아직 없습니다.</div>
            )}

            {!loading && analysis && result && (
                <>
                    {result.summary && <div className="da-summary">{result.summary}</div>}

                    {stats.length > 0 && (
                        <div className="da-stats">
                            {stats.map((s) => (
                                <div key={s.label} className="da-stat">
                                    <div className="da-stat-label">{s.label}</div>
                                    <div className="da-stat-value">{s.value}</div>
                                </div>
                            ))}
                        </div>
                    )}

                    <div className="da-grid">
                        <TextCard title="🚴 활동량·유산소" text={result.activity} />
                        <TextCard title="🏋️ 웨이트" text={result.strength} />
                        <TextCard title="🍽️ 식단" text={result.nutrition} />
                        <TextCard title="⚖️ 체성분" text={result.body} />
                        <BulletCard title="잘한 점" items={result.goodPoints} tone="da-good" />
                        <BulletCard title="개선할 점" items={result.improvements} tone="da-improve" />
                    </div>

                    <BulletCard title="데이터 참고" items={result.dataNotes} tone="da-notes" />

                    <div className="da-meta">분석 시각 {analysis.analyzedAt.replace("T", " ")}</div>
                </>
            )}
        </div>
    );
}
