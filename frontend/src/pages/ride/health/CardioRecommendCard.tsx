// 유산소 추천 카드: 한 달 감량 목표(직접 입력) → 오늘 먹은 것·실제 활동(걸음, 따릉이, 헬스장, 웨이트)을 반영해
// 헬스장 유산소 시간·강도를 추천한다. 계산은 서버(/api/exercise/cardio/recommend).
import { useEffect, useState } from "react";
import { useAdmin } from "@/lib/admin";

const API_BASE = import.meta.env.VITE_API_URL || "";

interface Part { key: string; label: string; kcal: number | null; estimated: boolean; detail: string | null }
interface Option { name: string; setting: string; minutes: number; kcal: number; kcalPerMin: number }
interface Recommendation {
    status: "NEED_TARGET" | "DONE" | "CARDIO";
    minutes: number | null; kcal: number | null; hrLow: number; hrHigh: number;
    headline: string; note: string | null; options: Option[];
}
interface Result {
    date: string; today: boolean;
    monthlyLossKg: number | null; suggestedMonthlyLossKg: number | null; dailyDeficitTarget: number | null;
    weightKg: number; bodyRecordDate: string | null; bmr: number;
    intake: Part; burns: Part[]; expenditure: number;
    currentDeficit: number; remainingKcal: number | null;
    recommendation: Recommendation;
    week: { avgDeficit: number | null; days: number; kgPerMonth: number | null };
    notes: string[];
}

const ymd = (d: Date) =>
    `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
const fmt = (n: number | null | undefined) => (n == null ? "-" : n.toLocaleString());

export default function CardioRecommendCard() {
    const { isAdmin } = useAdmin();
    const [dayOffset, setDayOffset] = useState(0);   // 0 = 오늘, -1 = 어제
    const [data, setData] = useState<Result | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [editing, setEditing] = useState(false);
    const [input, setInput] = useState("");
    const [saving, setSaving] = useState(false);
    const [reload, setReload] = useState(0);
    const [showDetail, setShowDetail] = useState(false);

    const date = ymd(new Date(Date.now() + dayOffset * 86400000));

    useEffect(() => {
        setError(null);
        fetch(`${API_BASE}/api/exercise/cardio/recommend?date=${date}&userId=1`)
            .then((r) => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json(); })
            .then((d: Result) => {
                setData(d);
                if (d.monthlyLossKg == null) {
                    setEditing(true);
                    setInput(String(d.suggestedMonthlyLossKg ?? 2));
                }
            })
            .catch((e) => { console.error(e); setError("추천을 계산하지 못했습니다."); });
    }, [date, reload]);

    const saveTarget = async () => {
        const kg = Number(input);
        if (!Number.isFinite(kg) || kg < 0.1 || kg > 6) { setError("0.1 ~ 6kg 사이로 입력하세요."); return; }
        setSaving(true);
        setError(null);
        try {
            const r = await fetch(`${API_BASE}/api/exercise/cardio/target?userId=1`, {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ monthlyLossKg: kg }),
            });
            if (!r.ok) throw new Error(r.status === 403 ? "관리자 로그인이 필요합니다." : `저장 실패 (HTTP ${r.status})`);
            setEditing(false);
            setReload((n) => n + 1);
        } catch (e: any) {
            setError(e.message);
        } finally {
            setSaving(false);
        }
    };

    if (!data) {
        return <section className="reco-card">{error ? <div className="cardio-error">{error}</div> : <p className="reco-muted">추천 계산 중…</p>}</section>;
    }

    const rec = data.recommendation;
    const target = data.dailyDeficitTarget;
    const progressPct = target ? Math.max(0, Math.min(100, (data.currentDeficit / target) * 100)) : 0;

    return (
        <section className="reco-card">
            <div className="reco-head">
                <div>
                    <div className="cardio-card-label">유산소 추천</div>
                    <div className="reco-days">
                        <button className={dayOffset === 0 ? "active" : ""} onClick={() => setDayOffset(0)}>오늘</button>
                        <button className={dayOffset === -1 ? "active" : ""} onClick={() => setDayOffset(-1)}>어제</button>
                    </div>
                </div>

                <div className="reco-target">
                    {editing ? (
                        <div className="reco-target-edit">
                            <span>한 달</span>
                            <input
                                type="number" step="0.1" min="0.1" max="6" value={input}
                                onChange={(e) => setInput(e.target.value)}
                                onKeyDown={(e) => e.key === "Enter" && saveTarget()}
                                aria-label="한 달 감량 목표(kg)"
                            />
                            <span>kg 감량</span>
                            <button className="reco-save" onClick={saveTarget} disabled={saving}>{saving ? "저장 중" : "저장"}</button>
                            {data.monthlyLossKg != null && <button className="reco-cancel" onClick={() => setEditing(false)}>취소</button>}
                        </div>
                    ) : (
                        <>
                            <span className="reco-target-value">한 달 <strong>{data.monthlyLossKg}kg</strong> 감량</span>
                            <span className="reco-muted">하루 적자 목표 {fmt(target)}kcal</span>
                            {isAdmin && (
                                <button className="reco-edit" onClick={() => { setInput(String(data.monthlyLossKg ?? "")); setEditing(true); }}>
                                    목표 수정
                                </button>
                            )}
                        </>
                    )}
                    {editing && data.suggestedMonthlyLossKg != null && (
                        <span className="reco-muted">인바디 목표 기준 페이스: 한 달 {data.suggestedMonthlyLossKg}kg</span>
                    )}
                </div>
            </div>

            {error && <div className="cardio-error">{error}</div>}

            {rec.status !== "NEED_TARGET" && (
                <>
                    <div className={`reco-main ${rec.status === "DONE" ? "done" : ""}`}>
                        <div className="reco-headline">{rec.status === "DONE" ? "✅ " : "🏃 "}{rec.headline}</div>
                        <div className="reco-hr">
                            심박 <strong>{rec.hrLow}~{rec.hrHigh}bpm</strong> 유지 (존 2, 말은 할 수 있지만 노래는 힘든 정도)
                            {rec.kcal ? <> · 약 {fmt(rec.kcal)}kcal</> : null}
                        </div>
                        {rec.note && <div className="reco-note">{rec.note}</div>}
                    </div>

                    {rec.options.length > 0 && (
                        <div className="reco-options">
                            {rec.options.map((o) => (
                                <div key={o.name} className="reco-option">
                                    <div className="reco-option-name">{o.name}</div>
                                    <div className="reco-option-min">{o.minutes}분</div>
                                    <div className="reco-muted">{o.setting}</div>
                                    <div className="reco-muted">약 {fmt(o.kcal)}kcal ({o.kcalPerMin}/분)</div>
                                </div>
                            ))}
                        </div>
                    )}

                    <div className="reco-balance">
                        <div className="reco-balance-row">
                            <span>{data.today ? "지금까지 적자" : "그날 적자"}</span>
                            <strong className={data.currentDeficit < 0 ? "neg" : ""}>{fmt(data.currentDeficit)}kcal</strong>
                            <span className="reco-muted">/ 목표 {fmt(target)}kcal</span>
                        </div>
                        <div className="cardio-progress"><div style={{ width: `${progressPct}%` }} /></div>
                        <button className="reco-toggle" onClick={() => setShowDetail(!showDetail)}>
                            {showDetail ? "계산 내역 접기 ▲" : "계산 내역 보기 ▼"}
                        </button>
                        {showDetail && (
                            <table className="reco-table">
                                <tbody>
                                <tr className="in">
                                    <td>🍽 {data.intake.label}</td>
                                    <td>{data.intake.detail}{data.intake.estimated && <em> 추정</em>}</td>
                                    <td>{fmt(data.intake.kcal)}</td>
                                </tr>
                                {data.burns.map((b) => (
                                    <tr key={b.key}>
                                        <td>🔥 {b.label}</td>
                                        <td>{b.detail}{b.estimated && <em> 추정</em>}</td>
                                        <td>−{fmt(b.kcal)}</td>
                                    </tr>
                                ))}
                                <tr className="sum">
                                    <td>소비 합계</td>
                                    <td>체중 {data.weightKg}kg{data.bodyRecordDate ? ` (인바디 ${data.bodyRecordDate})` : ""}</td>
                                    <td>{fmt(data.expenditure)}</td>
                                </tr>
                                </tbody>
                            </table>
                        )}
                    </div>

                    {data.week.avgDeficit != null && (
                        <div className="reco-week">
                            최근 {data.week.days}일 평균 적자 <strong>{fmt(data.week.avgDeficit)}kcal/일</strong>
                            {" → "}이 페이스면 한 달 약 <strong>{data.week.kgPerMonth}kg</strong>
                            {data.monthlyLossKg != null && data.week.kgPerMonth != null && (
                                <span className={data.week.kgPerMonth >= data.monthlyLossKg ? "ok" : "warn"}>
                                    {data.week.kgPerMonth >= data.monthlyLossKg ? " 목표 페이스 👍" : " 목표보다 느려요"}
                                </span>
                            )}
                        </div>
                    )}

                    {data.notes.length > 0 && (
                        <ul className="reco-notes">
                            {data.notes.map((n) => <li key={n}>{n}</li>)}
                        </ul>
                    )}
                </>
            )}
        </section>
    );
}
