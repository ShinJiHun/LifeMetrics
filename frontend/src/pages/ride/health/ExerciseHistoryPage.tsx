// src/pages/ExerciseHistoryPage.tsx
import { useState, useEffect, useMemo } from "react";
import MuscleImageMap from "@/components/human/MuscleImageMap.tsx";
import "@/styles/global.css";
import "@/styles/exercise-history.css";
import "@/styles/muscle-image-map.css";

const API_BASE = import.meta.env.VITE_API_URL || "";

interface ExerciseSet { id: number; setNumber: number; weight: number; reps: number; }
interface MuscleMapping { muscleGroupId: number; muscleName: string; role: "PRIMARY" | "SECONDARY" | "SYNERGIST"; activationLevel: number; }
interface ExerciseLog { id: number; exerciseItemId: number; exerciseNameKo: string; exerciseNameEn: string; categoryName: string; sets: ExerciseSet[]; restTimeSec: number; memo: string; muscleMappings?: MuscleMapping[]; }
interface ExerciseSession {
    id: number; sessionDate: string; isPT: boolean; memo: string; logs: ExerciseLog[];
    estimatedCalories?: number; totalVolume?: number; totalSets?: number; estimatedMinutes?: number;
}
interface ExerciseAnalysis {
    id: number; sessionId: number; summary: string; targetMuscles: string;
    volumeLevel: string; intensityLevel: string; highlights: string[]; suggestions: string[]; score: number;
}

const MUSCLE_COLOR_MAP: Record<string, string> = {
    가슴: "#ef4444", 등: "#3b82f6", 어깨: "#f59e0b",
    팔: "#8b5cf6", 하체: "#10b981", 복근: "#06b6d4", 코어: "#06b6d4",
};

const MUSCLE_META: Record<number, { ko: string; group: string }> = {
    101: { ko: "윗가슴", group: "가슴" }, 102: { ko: "중간가슴", group: "가슴" },
    103: { ko: "아랫가슴", group: "가슴" }, 104: { ko: "가슴안쪽", group: "가슴" },
    201: { ko: "광배근", group: "등" }, 202: { ko: "승모상부", group: "등" },
    203: { ko: "승모중부", group: "등" }, 204: { ko: "승모하부", group: "등" },
    205: { ko: "능형근", group: "등" }, 206: { ko: "대원근", group: "등" },
    301: { ko: "전면삼각", group: "어깨" }, 302: { ko: "측면삼각", group: "어깨" },
    303: { ko: "후면삼각", group: "어깨" }, 304: { ko: "회전근개", group: "어깨" },
    4: { ko: "이두", group: "팔" }, 5: { ko: "삼두", group: "팔" }, 6: { ko: "전완", group: "팔" },
    7: { ko: "대퇴사두", group: "하체" }, 8: { ko: "햄스트링", group: "하체" },
    901: { ko: "대둔근", group: "하체" }, 902: { ko: "중둔근", group: "하체" },
    1101: { ko: "상복부", group: "복근" }, 1102: { ko: "하복부", group: "복근" },
    1103: { ko: "복사근", group: "복근" }, 12: { ko: "척추기립근", group: "코어" },
};

const INTENSITY_COLOR: Record<string, string> = {
    "낮음": "#10b981", "보통": "#3b82f6", "높음": "#f59e0b", "매우높음": "#ef4444"
};

function SessionHeatmap({ logs, selectedLogId, onSelectLog }: { logs: ExerciseLog[]; selectedLogId: number | null; onSelectLog: (id: number | null) => void; }) {
    const allMappings = useMemo(() => {
        const targetLogs = selectedLogId ? logs.filter((l) => l.id === selectedLogId) : logs;
        return targetLogs.flatMap((l) => l.muscleMappings || []);
    }, [logs, selectedLogId]);

    const activeMuscles = useMemo(() => {
        const all = logs.flatMap((l) => l.muscleMappings || []);
        const grouped: Record<number, { name: string; group: string; maxRole: string }> = {};
        all.forEach((mm) => {
            const meta = MUSCLE_META[mm.muscleGroupId];
            if (!meta) return;
            if (!grouped[mm.muscleGroupId] || mm.role === "PRIMARY") {
                grouped[mm.muscleGroupId] = { name: meta.ko, group: meta.group, maxRole: grouped[mm.muscleGroupId]?.maxRole === "PRIMARY" ? "PRIMARY" : mm.role };
            }
        });
        return Object.entries(grouped).sort(([, a], [, b]) => a.maxRole === b.maxRole ? 0 : a.maxRole === "PRIMARY" ? -1 : 1);
    }, [logs]);

    const hasAnyMappings = logs.some((l) => l.muscleMappings && l.muscleMappings.length > 0);
    if (!hasAnyMappings) return null;

    return (
        <div className="session-heatmap">
            <MuscleImageMap muscleMappings={allMappings} imageHeight={220} />
            {selectedLogId && (
                <div className="heatmap-focus-badge">
                    <span>{logs.find((l) => l.id === selectedLogId)?.exerciseNameKo}</span>
                    <button onClick={() => onSelectLog(null)}>✕ 전체</button>
                </div>
            )}
            {activeMuscles.length > 0 && (
                <div className="heatmap-legend">
                    {activeMuscles.map(([id, info]) => {
                        const color = MUSCLE_COLOR_MAP[info.group] || "#888";
                        return (
                            <span key={id} className={`muscle-tag ${info.maxRole === "PRIMARY" ? "primary" : "secondary"}`} style={{ borderColor: `${color}50`, color, backgroundColor: `${color}12` }}>
                                <span className="muscle-dot" style={{ backgroundColor: color }} />
                                {info.name}
                            </span>
                        );
                    })}
                </div>
            )}
        </div>
    );
}

function AnalysisCard({ analysis }: { analysis: ExerciseAnalysis }) {
    const intensityColor = INTENSITY_COLOR[analysis.intensityLevel] || "#888";
    return (
        <div className="analysis-card">
            <div className="analysis-header">
                <span className="analysis-icon">🤖</span>
                <span className="analysis-title">AI 분석</span>
                <span className="analysis-score" style={{
                    color: analysis.score >= 80 ? "#10b981" : analysis.score >= 60 ? "#f59e0b" : "#ef4444"
                }}>{analysis.score}점</span>
            </div>
            <p className="analysis-summary">{analysis.summary}</p>
            <div className="analysis-meta">
                <span className="analysis-badge" style={{ color: intensityColor, borderColor: `${intensityColor}40`, backgroundColor: `${intensityColor}10` }}>
                    강도: {analysis.intensityLevel}
                </span>
                <span className="analysis-badge">{analysis.volumeLevel} 볼륨</span>
                <span className="analysis-badge">타겟: {analysis.targetMuscles}</span>
            </div>
            {analysis.highlights.length > 0 && (
                <div className="analysis-section">
                    <span className="section-label">👍 잘한 점</span>
                    {analysis.highlights.map((h, i) => <p key={i} className="analysis-item good">{h}</p>)}
                </div>
            )}
            {analysis.suggestions.length > 0 && (
                <div className="analysis-section">
                    <span className="section-label">💡 개선점</span>
                    {analysis.suggestions.map((s, i) => <p key={i} className="analysis-item tip">{s}</p>)}
                </div>
            )}
        </div>
    );
}

function StatsCard({ session }: { session: ExerciseSession }) {
    return (
        <div className="session-stats">
            <div className="stat-item">
                <span className="stat-icon">🔥</span>
                <span className="stat-value">{Math.round(session.estimatedCalories || 0)}</span>
                <span className="stat-label">kcal</span>
            </div>
            <div className="stat-item">
                <span className="stat-icon">🏋️</span>
                <span className="stat-value">{(session.totalVolume || 0).toLocaleString()}</span>
                <span className="stat-label">kg 볼륨</span>
            </div>
            <div className="stat-item">
                <span className="stat-icon">⏱</span>
                <span className="stat-value">{session.estimatedMinutes || 0}</span>
                <span className="stat-label">분</span>
            </div>
            <div className="stat-item">
                <span className="stat-icon">📊</span>
                <span className="stat-value">{session.totalSets || 0}</span>
                <span className="stat-label">세트</span>
            </div>
        </div>
    );
}

export default function ExerciseHistoryPage() {
    const [sessions, setSessions] = useState<ExerciseSession[]>([]);
    const [loading, setLoading] = useState(true);
    const [selectedMonth, setSelectedMonth] = useState(() => {
        const now = new Date();
        return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
    });
    const [expandedSession, setExpandedSession] = useState<number | null>(null);
    const [selectedLog, setSelectedLog] = useState<number | null>(null);
    const [analysisMap, setAnalysisMap] = useState<Record<number, ExerciseAnalysis>>({});

    useEffect(() => { setSelectedLog(null); }, [expandedSession]);

    useEffect(() => {
        setLoading(true);
        fetch(`${API_BASE}/api/exercise/history?month=${selectedMonth}&userId=1`)
            .then((res) => { if (!res.ok) throw new Error("API 에러"); return res.json(); })
            .then((data) => { setSessions(Array.isArray(data) ? data : []); setLoading(false); })
            .catch((err) => { console.error("기록 로드 실패:", err); setSessions([]); setLoading(false); });
    }, [selectedMonth]);

    useEffect(() => {
        if (!expandedSession || analysisMap[expandedSession]) return;
        fetch(`${API_BASE}/api/ai/analysis/exercise/${expandedSession}?userId=1`)
            .then((res) => { if (!res.ok) return null; return res.json(); })
            .then((data) => {
                if (data && data.summary && data.summary !== "분석 실패" && data.summary !== "API 키 없음") {
                    setAnalysisMap((prev) => ({ ...prev, [expandedSession]: data }));
                }
            })
            .catch(() => {});
    }, [expandedSession]);

    const formatDate = (dateStr: string) => { const date = new Date(dateStr); const days = ["일","월","화","수","목","금","토"]; return `${date.getMonth()+1}/${date.getDate()} (${days[date.getDay()]})`; };
    const getMonthOptions = () => { const o=[]; const now=new Date(); for(let i=0;i<36;i++){const d=new Date(now.getFullYear(),now.getMonth()-i,1); o.push({value:`${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,"0")}`,label:`${d.getFullYear()}년 ${d.getMonth()+1}월`});} return o; };  // 36개월(3년)까지 확장: 시트 백필 데이터가 2024-03부터 있어서 12개월로는 안 보였음

    const monthlyCalories = sessions.reduce((sum, s) => sum + (s.estimatedCalories || 0), 0);

    return (
        <div className="exercise-history-page">
            <h2>📋 운동 기록 보기</h2>
            <div className="month-selector">
                <select value={selectedMonth} onChange={(e) => setSelectedMonth(e.target.value)}>
                    {getMonthOptions().map((opt) => (<option key={opt.value} value={opt.value}>{opt.label}</option>))}
                </select>
            </div>

            {!loading && sessions.length > 0 && (
                <div className="monthly-summary">
                    <div className="summary-item"><span className="summary-label">총 운동일</span><span className="summary-value">{sessions.length}일</span></div>
                    <div className="summary-item"><span className="summary-label">PT 횟수</span><span className="summary-value">{sessions.filter(s=>s.isPT).length}회</span></div>
                    <div className="summary-item"><span className="summary-label">총 세트</span><span className="summary-value">{sessions.reduce((s,ss)=>s+(ss.totalSets||0),0)}세트</span></div>
                    <div className="summary-item"><span className="summary-label">총 칼로리</span><span className="summary-value">{Math.round(monthlyCalories).toLocaleString()}kcal</span></div>
                </div>
            )}

            {loading ? (<div className="loading">로딩 중...</div>) : sessions.length === 0 ? (<div className="empty-state"><p>📭 이 달에는 운동 기록이 없습니다</p></div>) : (
                <div className="session-list">
                    {sessions.map((session) => (
                        <div key={session.id} className={`session-card ${expandedSession === session.id ? "expanded" : ""}`}>
                            <div className="session-header" onClick={() => setExpandedSession(expandedSession === session.id ? null : session.id)}>
                                <div className="session-date">
                                    <span className="date-text">{formatDate(session.sessionDate)}</span>
                                    {session.isPT && <span className="pt-badge">🏆 PT</span>}
                                </div>
                                <div className="session-summary">
                                    <span>{session.logs.length}종목</span>
                                    <span>{session.totalSets || 0}세트</span>
                                    <span>{(session.totalVolume || 0).toLocaleString()}kg</span>
                                </div>
                                <span className="expand-icon">{expandedSession === session.id ? "▲" : "▼"}</span>
                            </div>

                            {expandedSession !== session.id && session.estimatedCalories ? (
                                <div className="session-calorie-bar">
                                    <span>🔥 {Math.round(session.estimatedCalories)}kcal</span>
                                    <span>⏱ {session.estimatedMinutes}분</span>
                                </div>
                            ) : null}

                            {expandedSession === session.id && (
                                <div className="session-detail">
                                    <StatsCard session={session} />
                                    <SessionHeatmap logs={session.logs} selectedLogId={selectedLog} onSelectLog={setSelectedLog} />

                                    {session.logs.map((log) => (
                                        <div key={log.id} className={`log-card ${selectedLog === log.id ? "log-card--selected" : ""}`} onClick={() => setSelectedLog(selectedLog === log.id ? null : log.id)}>
                                            <div className="log-header">
                                                <span className="category-badge">{log.categoryName}</span>
                                                <h4>{log.exerciseNameKo}</h4>
                                                <span className="exercise-en">{log.exerciseNameEn}</span>
                                            </div>
                                            {log.muscleMappings && log.muscleMappings.length > 0 && (
                                                <div className="muscle-tags">
                                                    {log.muscleMappings.map((mm, i) => {
                                                        const meta = MUSCLE_META[mm.muscleGroupId]; if (!meta) return null;
                                                        const color = MUSCLE_COLOR_MAP[meta.group] || "#888";
                                                        return (<span key={i} className="muscle-tag-inline" style={{ color, borderColor: `${color}40`, backgroundColor: `${color}10` }}>{meta.ko}<span className="muscle-role">{mm.role === "PRIMARY" ? "주동" : "보조"}</span></span>);
                                                    })}
                                                </div>
                                            )}
                                            {log.memo && <div className="log-memo">📝 {log.memo}</div>}
                                            {log.restTimeSec > 0 && <div className="log-rest">⏱️ 휴식 {log.restTimeSec}초</div>}
                                            <div className="sets-table">
                                                <div className="sets-header"><span>세트</span><span>무게</span><span>횟수</span><span>볼륨</span></div>
                                                {log.sets.map((set) => (<div key={set.id} className="set-row"><span>{set.setNumber}</span><span>{set.weight}kg</span><span>{set.reps}회</span><span className="volume">{(set.weight*set.reps).toLocaleString()}kg</span></div>))}
                                            </div>
                                            <div className="log-total">총 볼륨: {log.sets.reduce((s,st)=>s+st.weight*st.reps,0).toLocaleString()}kg</div>
                                        </div>
                                    ))}

                                    {/* 모바일: AI 분석을 카드 내부 하단에 표시 */}
                                    {analysisMap[session.id] && (
                                        <div className="session-detail-side-mobile">
                                            <AnalysisCard analysis={analysisMap[session.id]} />
                                        </div>
                                    )}
                                </div>
                            )}

                            {/* PC: 펼쳐진 카드 기준 우측 바깥에 AI 분석 표시 */}
                            {expandedSession === session.id && analysisMap[session.id] && (
                                <div className="analysis-side-panel">
                                    <AnalysisCard analysis={analysisMap[session.id]} />
                                </div>
                            )}
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}
