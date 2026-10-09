// 체성분 목표: 진행 중인 목표의 진행률·W/kg, 인바디 값으로 목표 채우기, 목표 이력
// 목표 숫자는 인바디 기록지 조절값(없으면 계산)으로 정하고, AI 는 해설만 한다.

import {useEffect, useState} from "react";
import AdminOnly from "@/components/common/AdminOnly";
import {
    fetchCurrentGoal, fetchGoalNarrative, fetchGoals, fetchGoalSuggestion, readSheetSuggestion, saveGoal, SOURCE_LABEL,
} from "@/api/bodyGoal";
import type {BodyGoal, GoalStatusView, GoalSuggestion} from "@/api/bodyGoal";

import "@/styles/global.css";
import "@/styles/weight-loss.css";
import "@/styles/body-goal.css";

const STATUS_LABEL = {ACTIVE: "진행 중", ACHIEVED: "달성", ABANDONED: "중단"} as const;

type NumKey = "startWeight" | "startFatMass" | "startMuscleMass" | "targetWeight" | "targetFatMass" | "targetMuscleMass";

const today = () => new Date().toISOString().slice(0, 10);

const emptyGoal = (): BodyGoal => ({
    source: "MANUAL", baseRecordId: null, startDate: today(),
    startWeight: null, startFatMass: null, startMuscleMass: null,
    targetWeight: null, targetFatMass: null, targetMuscleMass: null,
    targetDate: null, notes: null,
});

const fmt = (v: number | null | undefined, unit = "kg") => (v == null ? "-" : `${v} ${unit}`);

export default function BodyGoalPage() {
    const [status, setStatus] = useState<GoalStatusView | null>(null);
    const [goals, setGoals] = useState<BodyGoal[]>([]);
    const [loaded, setLoaded] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [editing, setEditing] = useState<BodyGoal | null>(null);

    const load = () => {
        Promise.all([fetchCurrentGoal(), fetchGoals()])
            .then(([s, list]) => { setStatus(s); setGoals(list); })
            .catch((e: Error) => setError(e.message))
            .finally(() => setLoaded(true));
    };

    useEffect(load, []);

    const changeStatus = async (next: "ACHIEVED" | "ABANDONED") => {
        if (!status) return;
        try {
            await saveGoal({...status.goal, status: next});
            load();
        } catch (e) {
            setError((e as Error).message);
        }
    };

    if (!loaded) return <div className="wl-page"><div className="wl-state">불러오는 중…</div></div>;

    return (
        <div className="wl-page">
            <h1>체성분 목표</h1>
            <div className="wl-subtitle">
                인바디 기록지의 조절값으로 목표를 잡고, 이후 측정으로 진행률을 봅니다.
            </div>

            {error && <div className="bg-error">{error}</div>}

            {editing ? (
                <GoalForm initial={editing} onCancel={() => setEditing(null)}
                          onSaved={() => { setEditing(null); load(); }}/>
            ) : (
                <AdminOnly>
                    <div className="bg-toolbar">
                        <button className="bg-btn bg-btn-primary" onClick={() => setEditing(emptyGoal())}>
                            ➕ 새 목표
                        </button>
                        {status && (
                            <>
                                <button className="bg-btn" onClick={() => setEditing(status.goal)}>✏️ 수정</button>
                                <button className="bg-btn" onClick={() => changeStatus("ACHIEVED")}>🏁 달성 처리</button>
                                <button className="bg-btn" onClick={() => changeStatus("ABANDONED")}>중단</button>
                            </>
                        )}
                    </div>
                </AdminOnly>
            )}

            {!editing && (status ? <GoalStatus s={status}/> : (
                <div className="wl-headline">
                    <div className="wl-headline-label">진행 중인 목표가 없습니다.</div>
                    <div className="wl-headline-note">
                        "새 목표"에서 인바디 값으로 채우기를 누르면 최신 인바디 기록지 기준 목표가 들어갑니다.
                    </div>
                </div>
            ))}

            {!editing && goals.length > 0 && (
                <>
                    <div className="wl-section-title">목표 이력</div>
                    <div className="wl-table-wrap">
                        <table className="wl-table bg-history">
                            <thead>
                            <tr>
                                <th>상태</th>
                                <th>기간</th>
                                <th>체중</th>
                                <th>체지방량</th>
                                <th>골격근량</th>
                                <th>근거</th>
                            </tr>
                            </thead>
                            <tbody>
                            {goals.map((g) => (
                                <tr key={g.id}>
                                    <td><span className={`bg-status ${g.status}`}>{STATUS_LABEL[g.status ?? "ACTIVE"]}</span></td>
                                    <td>{g.startDate} ~ {g.targetDate ?? ""}</td>
                                    <td>{g.startWeight ?? "-"} → {g.targetWeight ?? "-"}</td>
                                    <td>{g.startFatMass ?? "-"} → {g.targetFatMass ?? "-"}</td>
                                    <td>{g.startMuscleMass ?? "-"} → {g.targetMuscleMass ?? "-"}</td>
                                    <td>{SOURCE_LABEL[g.source]}</td>
                                </tr>
                            ))}
                            </tbody>
                        </table>
                    </div>
                </>
            )}
        </div>
    );
}

function GoalStatus({s}: { s: GoalStatusView }) {
    const g = s.goal;
    const [narrative, setNarrative] = useState<string | null>(null);
    const [aiLoading, setAiLoading] = useState(false);
    const [aiError, setAiError] = useState<string | null>(null);

    const runAi = async () => {
        setAiLoading(true);
        setAiError(null);
        try {
            setNarrative(await fetchGoalNarrative());
        } catch (e) {
            setAiError((e as Error).message);
        } finally {
            setAiLoading(false);
        }
    };

    const dday = s.progress.daysLeft;

    return (
        <>
            <div className="wl-headline">
                <div className="wl-headline-label">
                    {g.startDate} 시작 · {SOURCE_LABEL[g.source]} 기준
                    {g.targetDate && ` · 목표일 ${g.targetDate}`}
                </div>
                <div className="bg-progress-list">
                    <ProgressRow label="체지방량" start={g.startFatMass} current={s.current.fatMass}
                                 target={g.targetFatMass} pct={s.progress.fatPct}
                                 sub={s.current.inbodyDate && `현재값: ${s.current.inbodyDate} 인바디`}/>
                    <ProgressRow label="체중" start={g.startWeight} current={s.current.weight}
                                 target={g.targetWeight} pct={s.progress.weightPct}
                                 sub={s.current.weightDate && `현재값: ${s.current.weightDate} 측정 (체중계 포함)`}/>
                    <ProgressRow label="골격근량" start={g.startMuscleMass} current={s.current.muscleMass}
                                 target={g.targetMuscleMass} pct={s.progress.musclePct}
                                 sub="유지 또는 증가가 목표입니다"/>
                </div>
                <div className="wl-headline-note">
                    {s.progress.daysElapsed}일 경과
                    {dday != null && (dday >= 0 ? ` · D-${dday}` : ` · 목표일 ${-dday}일 지남`)}
                    {g.notes && <><br/>{g.notes}</>}
                </div>
            </div>

            <div className="wl-section-title">
                W/kg — FTP {s.ftp ?? "-"} W{s.ftpEstimated && " (설정값 없음, 체중×3.0 추정)"}
            </div>
            <div className="wl-grid">
                <Card title="시작" value={s.wkgStart} unit="W/kg"/>
                <Card title="현재" value={s.wkgCurrent} unit="W/kg"/>
                <Card title="목표 체중 도달 시" value={s.wkgTarget} unit="W/kg"/>
            </div>

            <div className="wl-section-title">AI 코칭</div>
            <div className="wl-ai">
                {narrative ? (
                    <p className="wl-ai-text">{narrative}</p>
                ) : (
                    <>
                        <button className="wl-ai-btn" onClick={runAi} disabled={aiLoading}>
                            {aiLoading ? "분석 중…" : "🤖 AI 분석 실행"}
                        </button>
                        <span className="wl-ai-hint">목표와 진행 상황을 근거로 페이스·식단·훈련 의견을 생성합니다.</span>
                    </>
                )}
                {aiError && <div className="wl-ai-error">{aiError}</div>}
            </div>
        </>
    );
}

function ProgressRow({label, start, current, target, pct, sub}: {
    label: string; start: number | null; current: number | null; target: number | null;
    pct: number | null; sub?: string | null;
}) {
    if (start == null && target == null) return null;
    const width = pct == null ? 0 : Math.max(0, Math.min(100, pct));
    const cls = pct == null ? "" : pct >= 100 ? "done" : pct < 0 ? "reverse" : "";
    return (
        <div>
            <div className="bg-progress-head">
                <span><strong>{label}</strong> {fmt(start)} → {fmt(current)} → 목표 {fmt(target)}</span>
                <span className="bg-progress-pct">{pct == null ? "-" : `${pct}%`}</span>
            </div>
            <div className="bg-bar">
                <div className={`bg-bar-fill ${cls}`} style={{width: `${pct != null && pct < 0 ? 100 : width}%`}}/>
            </div>
            {pct != null && pct < 0 && <div className="bg-sub">시작값보다 목표에서 멀어졌습니다</div>}
            {sub && <div className="bg-sub">{sub}</div>}
        </div>
    );
}

function GoalForm({initial, onCancel, onSaved}: {
    initial: BodyGoal; onCancel: () => void; onSaved: () => void;
}) {
    const [goal, setGoal] = useState<BodyGoal>(initial);
    const [suggestion, setSuggestion] = useState<GoalSuggestion | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [busy, setBusy] = useState(false);

    const [reading, setReading] = useState(false);

    const applySuggestion = (s: GoalSuggestion) => {
        setSuggestion(s);
        setGoal((g) => ({
            ...g,
            source: s.source,
            baseRecordId: s.baseRecordId,
            startDate: g.id ? g.startDate : today(),
            startWeight: s.startWeight,
            startFatMass: s.startFatMass,
            startMuscleMass: s.startMuscleMass,
            targetWeight: s.targetWeight,
            targetFatMass: s.targetFatMass,
            targetMuscleMass: s.targetMuscleMass,
            targetDate: s.targetDate,
        }));
    };

    const fillFromInbody = async () => {
        setError(null);
        try {
            applySuggestion(await fetchGoalSuggestion());
        } catch (e) {
            setError((e as Error).message);
        }
    };

    const readSheet = async () => {
        setError(null);
        setReading(true);
        try {
            applySuggestion(await readSheetSuggestion());
        } catch (e) {
            setError((e as Error).message);
        } finally {
            setReading(false);
        }
    };

    // 제안값을 고치면 근거가 사용자 입력으로 바뀐다
    const setNum = (key: NumKey, raw: string) => {
        const v = raw === "" ? null : Number(raw);
        setGoal((g) => ({...g, [key]: v, source: key.startsWith("target") ? "MANUAL" : g.source}));
    };

    const submit = async () => {
        setBusy(true);
        setError(null);
        try {
            await saveGoal(goal);
            onSaved();
        } catch (e) {
            setError((e as Error).message);
        } finally {
            setBusy(false);
        }
    };

    const numField = (key: NumKey, label: string) => (
        <div className="bg-field">
            <label>{label} (kg)</label>
            <input type="number" step="0.1" value={goal[key] ?? ""} onChange={(e) => setNum(key, e.target.value)}/>
        </div>
    );

    return (
        <div className="bg-form">
            <div className="bg-toolbar">
                <button className="bg-btn" onClick={fillFromInbody}>📥 인바디 값으로 채우기</button>
            </div>

            {suggestion && suggestion.source === "INBODY" && (
                <>
                    <div className="wl-section-title">체중조절 — {suggestion.baseRecordDate} 인바디 기록지</div>
                    <div className="wl-grid">
                        <SheetCard title="적정체중" value={suggestion.sheetTargetWeight}/>
                        <SheetCard title="체중조절" value={suggestion.weightControl} signed/>
                        <SheetCard title="지방조절" value={suggestion.fatControl} signed/>
                        <SheetCard title="근육조절" value={suggestion.muscleControl} signed/>
                    </div>
                </>
            )}

            {suggestion && suggestion.source === "FORMULA" && (
                <div className="wl-notes wl-warn">
                    {suggestion.baseRecordDate} 인바디 기록에 체중조절 값(적정체중·지방조절·근육조절)이 없어
                    계산값으로 채웠습니다.
                    {suggestion.canReadSheet ? (
                        <div style={{marginTop: 8}}>
                            <button className="bg-btn" onClick={readSheet} disabled={reading}>
                                {reading ? "기록지 읽는 중…" : "📄 기록지에서 체중조절 값 읽기"}
                            </button>
                        </div>
                    ) : " 원본 기록지 이미지가 없어 읽어올 수 없습니다. 목표값을 직접 입력해 주세요."}
                </div>
            )}

            {suggestion && (
                <div className="bg-basis">
                    근거: {suggestion.basis}
                    {suggestion.standardWeight != null && (
                        <><br/>참고: 키 {suggestion.heightCm}cm(체중/BMI 역산), 표준 체중(BMI 22) {suggestion.standardWeight}kg</>
                    )}
                    {suggestion.weeksAtHalfPercent != null && (
                        <><br/>페이스: 주당 체중 0.5% 감량이면 {suggestion.weeksAtHalfPercent}주,
                            1%면 {suggestion.weeksAtOnePercent}주. 목표일은 0.5% 페이스로 채웠습니다.</>
                    )}
                </div>
            )}

            {error && <div className="bg-error">{error}</div>}

            <div className="wl-section-title">시작값</div>
            <div className="bg-form-grid">
                <div className="bg-field">
                    <label>시작일</label>
                    <input type="date" value={goal.startDate}
                           onChange={(e) => setGoal({...goal, startDate: e.target.value})}/>
                </div>
                {numField("startWeight", "체중")}
                {numField("startFatMass", "체지방량")}
                {numField("startMuscleMass", "골격근량")}
            </div>

            <div className="wl-section-title">목표값 — {SOURCE_LABEL[goal.source]}</div>
            <div className="bg-form-grid">
                <div className="bg-field">
                    <label>목표일</label>
                    <input type="date" value={goal.targetDate ?? ""}
                           onChange={(e) => setGoal({...goal, targetDate: e.target.value || null})}/>
                </div>
                {numField("targetWeight", "체중")}
                {numField("targetFatMass", "체지방량")}
                {numField("targetMuscleMass", "골격근량")}
            </div>

            <div className="bg-field" style={{marginBottom: 16}}>
                <label>메모</label>
                <textarea rows={2} value={goal.notes ?? ""}
                          onChange={(e) => setGoal({...goal, notes: e.target.value || null})}/>
            </div>

            <div className="bg-toolbar" style={{marginBottom: 0}}>
                <button className="bg-btn bg-btn-primary" onClick={submit} disabled={busy}>
                    {busy ? "저장 중…" : "저장"}
                </button>
                <button className="bg-btn" onClick={onCancel}>취소</button>
                {!goal.id && <span className="wl-ai-hint">저장하면 진행 중인 기존 목표는 중단 처리됩니다.</span>}
            </div>
        </div>
    );
}

function SheetCard({title, value, signed}: { title: string; value: number | null; signed?: boolean }) {
    const text = value == null ? "-" : signed && value > 0 ? `+${value}` : `${value}`;
    return (
        <div className="wl-card">
            <div className="wl-card-title">{title}</div>
            <div className="wl-card-value">
                {text}
                <span className="wl-card-unit">kg</span>
            </div>
        </div>
    );
}

function Card({title, value, unit}: { title: string; value: number | null; unit: string }) {
    return (
        <div className="wl-card">
            <div className="wl-card-title">{title}</div>
            <div className="wl-card-value">
                {value ?? "-"}
                <span className="wl-card-unit">{unit}</span>
            </div>
        </div>
    );
}
