import AdminOnly from "@/components/common/AdminOnly";
import {useState, useEffect, useCallback} from "react";
import {useNavigate} from "react-router-dom";
import {fetchActivities} from "@/api/activity";
import type {Activity} from "@/api/activity";
import ActivityMap from "@/pages/ride/riding/ActivityMap";
import {useDropzone} from "react-dropzone";
import "@/styles/riding-record.css";

const formatDistance = (meters: number) => (meters / 1000).toFixed(1);
const formatTime = (seconds: number) => {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = seconds % 60;
    return h > 0
        ? `${h}:${m.toString().padStart(2, "0")}:${s.toString().padStart(2, "0")}`
        : `${m}:${s.toString().padStart(2, "0")}`;
};
const formatDate = (dateStr: string) => {
    const date = new Date(dateStr);
    const year = date.getFullYear();
    const month = date.getMonth() + 1;
    const day = date.getDate();
    const weekday = ["일", "월", "화", "수", "목", "금", "토"][date.getDay()];
    const hour = date.getHours();
    const minute = date.getMinutes().toString().padStart(2, "0");
    return `${year}년 ${month}월 ${day}일 (${weekday}) ${hour}:${minute}`;
};

interface AnalysisData {
    id: number;
    summary: string;
    intensity: string;
    weatherImpact?: string;
    highlights: string[];
    suggestions: string[];
    score: number;
}

// ── 좌측 카드 ────────────────────────────────────────────────────
function ActivityCard({activity, selected, onClick}: {
    activity: Activity;
    selected: boolean;
    onClick: () => void;
}) {
    const navigate = useNavigate();

    return (
        <div className={`activity-card ${selected ? "selected" : ""}`} onClick={onClick}>
            <div className="activity-header">
                <div className="activity-date">{formatDate(activity.startTime)}</div>
                <div style={{display: "flex", alignItems: "center", gap: 8}}>
                    <div className="activity-gear">{activity.gearContext?.bikeLabel || ""}</div>
                    <button
                        style={{
                            padding: "3px 10px",
                            fontSize: 11,
                            background: "#3a1e1e",
                            border: "1px solid #ef4444",
                            borderRadius: 6,
                            color: "#fca5a5",
                            cursor: "pointer",
                            flexShrink: 0,
                            display: "flex",
                            alignItems: "center",
                            gap: 4
                        }}
                        onClick={(e) => {
                            e.stopPropagation();
                            navigate(`/records/riding/${activity.id}/live`);
                        }}
                    >
                        <span style={{
                            width: 6,
                            height: 6,
                            borderRadius: "50%",
                            background: "#ef4444",
                            display: "inline-block"
                        }}/>
                        라이브
                    </button>
                    <button
                        style={{
                            padding: "3px 10px",
                            fontSize: 11,
                            background: "#1e3a5f",
                            border: "1px solid #2563eb",
                            borderRadius: 6,
                            color: "#93c5fd",
                            cursor: "pointer",
                            flexShrink: 0
                        }}
                        onClick={(e) => {
                            e.stopPropagation();
                            navigate(`/records/riding/${activity.id}`);
                        }}
                    >
                        자세히
                    </button>
                </div>
            </div>
            <div className="activity-map">
                {activity.polyline
                    ? <ActivityMap polyline={activity.polyline} height={160}/>
                    : <div className="map-placeholder">경로 데이터 없음</div>}
            </div>
            {/* 상단 4개 */}
            <div className="activity-stats-main">
                <div className="stat-item large">
                    <span className="stat-value">{formatDistance(activity.totalDistance)}</span>
                    <span className="stat-label">km</span>
                </div>
                <div className="stat-item large">
                    <span className="stat-value">{formatTime(activity.movingTime)}</span>
                    <span className="stat-label">이동 시간</span>
                </div>
                <div className="stat-item large">
                    <span className="stat-value">{formatTime(activity.elapsedTime)}</span>
                    <span className="stat-label">전체 시간</span>
                </div>
                <div className="stat-item large">
                    <span className="stat-value">{activity.totalAscent?.toFixed(0) || 0}</span>
                    <span className="stat-label">m 획득고도</span>
                </div>
            </div>
            {/* 구동계 (활동일 기준 effective) */}
            {activity.gearContext && (activity.gearContext.chainring || activity.gearContext.cassette || activity.gearContext.tire) && (
                <div
                    style={{
                        display: "flex",
                        gap: 10,
                        padding: "6px 10px",
                        margin: "6px 0",
                        fontSize: 11,
                        color: "#cbd5e1",
                        background: "#0f172a",
                        border: "1px solid #1e293b",
                        borderRadius: 6,
                        flexWrap: "wrap",
                    }}
                >
                    {activity.gearContext.chainring && (
                        <span><span style={{color: "#64748b"}}>체인링</span> {activity.gearContext.chainring}</span>
                    )}
                    {activity.gearContext.cassette && (
                        <span><span style={{color: "#64748b"}}>카세트</span> {activity.gearContext.cassette}</span>
                    )}
                    {activity.gearContext.tire && (
                        <span><span style={{color: "#64748b"}}>타이어</span> {activity.gearContext.tire}</span>
                    )}
                    {activity.gearContext.etc && Object.entries(activity.gearContext.etc).map(([k, v]) => (
                        <span key={k}><span style={{color: "#64748b"}}>{k}</span> {v}</span>
                    ))}
                </div>
            )}
            {/* 하단 2열 */}
            <div className="activity-stats-grid">
                <div className="stat-cell">
                    <span className="stat-icon">⚡</span>
                    <span className="stat-label">평균 속도</span>
                    <span className="stat-value">{activity.avgSpeed?.toFixed(1) || 0} km/h</span>
                </div>
                <div className="stat-cell">
                    <span className="stat-icon">🚀</span>
                    <span className="stat-label">최대 속도</span>
                    <span className="stat-value">{activity.maxSpeed?.toFixed(1) || 0} km/h</span>
                </div>
                <div className="stat-cell">
                    <span className="stat-icon">❤️</span>
                    <span className="stat-label">평균 심박</span>
                    <span
                        className="stat-value">{activity.avgHeartRate ? `${activity.avgHeartRate.toFixed(0)} bpm` : "-"}</span>
                </div>
                <div className="stat-cell">
                    <span className="stat-icon">🔥</span>
                    <span className="stat-label">칼로리</span>
                    <span
                        className="stat-value">{activity.calories ? `${activity.calories.toFixed(0)} kcal` : "-"}</span>
                </div>
            </div>
        </div>
    );
}

// ── 우측 분석 패널 (항상 고정) ────────────────────────────────────
function AnalysisPanel({activity}: { activity: Activity | null }) {
    const [analysis, setAnalysis] = useState<AnalysisData | null>(null);
    const [loading, setLoading] = useState(false);
    const [analyzing, setAnalyzing] = useState(false);

    useEffect(() => {
        if (!activity) {
            setAnalysis(null);
            return;
        }
        setAnalysis(null);
        setLoading(true);
        fetch(`/api/ai/analysis/activity/${activity.id}?userId=1`)
            .then((r) => (r.ok ? r.json() : null))
            .then(setAnalysis)
            .catch(() => setAnalysis(null))
            .finally(() => setLoading(false));
    }, [activity?.id]);

    const handleAnalyze = async () => {
        if (!activity) return;
        setAnalyzing(true);
        try {
            const res = await fetch(`/api/ai/analysis/activity/${activity.id}?userId=1`, {method: "POST"});
            if (res.ok) setAnalysis(await res.json());
        } catch (e) {
            console.error("분석 실패", e);
        } finally {
            setAnalyzing(false);
        }
    };

    const intensityColor = (v: string) =>
        ({"낮음": "#22c55e", "보통": "#3b82f6", "높음": "#f59e0b", "매우높음": "#ef4444"}[v] ?? "#64748b");

    // 스탯 6개만 (3행 2열)
    const stats = activity ? [
        {icon: "📏", label: "거리", val: `${formatDistance(activity.totalDistance)} km`},
        {icon: "⏱", label: "이동시간", val: formatTime(activity.movingTime)},
        {icon: "⚡", label: "평균속도", val: `${activity.avgSpeed?.toFixed(1)} km/h`},
        {icon: "⛰️", label: "획득고도", val: `${activity.totalAscent?.toFixed(0)} m`},
        {icon: "❤️", label: "평균심박", val: activity.avgHeartRate ? `${activity.avgHeartRate.toFixed(0)} bpm` : "-"},
        {icon: "🔥", label: "칼로리", val: activity.calories ? `${activity.calories.toFixed(0)} kcal` : "-"},
    ] : [];

    return (
        <div className="analysis-panel">
            <div className="analysis-panel-title">🤖 AI 분석</div>

            {!activity && (
                <div className="analysis-empty-state">
                    <div className="analysis-empty-icon">🚴</div>
                    <p>좌측에서 라이딩 기록을<br/>선택해주세요</p>
                </div>
            )}

            {activity && (
                <>
                    <div className="analysis-selected-info">
                        <div className="analysis-selected-date">{formatDate(activity.startTime)}</div>
                        <div className="analysis-selected-dist">
                            {formatDistance(activity.totalDistance)} km · {formatTime(activity.movingTime)}
                        </div>
                    </div>

                    {/* 3행 2열 스탯 */}
                    <div className="analysis-stats-grid-panel">
                        {stats.map((s) => (
                            <div key={s.label} className="analysis-stat-card">
                                <span className="analysis-stat-icon">{s.icon}</span>
                                <span className="analysis-stat-label">{s.label}</span>
                                <span className="analysis-stat-val">{s.val}</span>
                            </div>
                        ))}
                    </div>

                    {/* AI 분석 */}
                    <div className="analysis-ai-section">
                        <div className="analysis-ai-title">💬 분석 결과</div>

                        {loading && <div className="analysis-loading">불러오는 중...</div>}

                        {!loading && !analysis && (
                            <div className="analysis-empty">
                                <p>아직 분석 결과가 없어요.</p>
                                <button className="analysis-btn" onClick={handleAnalyze} disabled={analyzing}>
                                    {analyzing ? "⏳ 분석 중..." : "✨ Claude로 분석하기"}
                                </button>
                            </div>
                        )}

                        {analysis && (
                            <div className="analysis-result">
                                <div className="analysis-score-row">
                                    <div className="analysis-score">{analysis.score}</div>
                                    <div>
                                        <div className="analysis-summary">{analysis.summary}</div>
                                        {analysis.intensity && (
                                            <span className="analysis-intensity-badge"
                                                  style={{background: intensityColor(analysis.intensity)}}>
                                                {analysis.intensity}
                                            </span>
                                        )}
                                    </div>
                                </div>

                                {analysis.weatherImpact && (
                                    <div className="analysis-weather-impact">🌤 {analysis.weatherImpact}</div>
                                )}

                                {analysis.highlights?.length > 0 && (
                                    <div className="analysis-section">
                                        <div className="analysis-section-title">✅ 잘한 점</div>
                                        {analysis.highlights.map((h, i) => (
                                            <div key={i} className="analysis-item highlight">· {h}</div>
                                        ))}
                                    </div>
                                )}

                                {analysis.suggestions?.length > 0 && (
                                    <div className="analysis-section">
                                        <div className="analysis-section-title">💡 개선점</div>
                                        {analysis.suggestions.map((s, i) => (
                                            <div key={i} className="analysis-item suggestion">· {s}</div>
                                        ))}
                                    </div>
                                )}

                                <button className="analysis-btn reanalyze" onClick={handleAnalyze} disabled={analyzing}>
                                    {analyzing ? "⏳ 분석 중..." : "🔄 재분석"}
                                </button>
                            </div>
                        )}
                    </div>
                </>
            )}
        </div>
    );
}

function FitUploadModal({onClose, onSuccess}: {
    onClose: () => void;
    onSuccess: () => void;
}) {
    const [results, setResults] = useState<Array<{ filename: string; status: string; error?: string }>>([]);
    const [uploading, setUploading] = useState(false);

    const onDrop = useCallback(async (acceptedFiles: File[]) => {
        const fitFiles = acceptedFiles.filter(f => f.name.toLowerCase().endsWith(".fit"));
        if (!fitFiles.length) return;

        setUploading(true);
        setResults([]);

        const formData = new FormData();
        fitFiles.forEach(f => formData.append("files", f));

        try {
            const res = await fetch("/api/activities/upload", {
                method: "POST",
                body: formData,
            });
            const data = await res.json();
            setResults(data.results);

            const hasSuccess = data.results.some((r: any) => r.status === "success");
            if (hasSuccess) onSuccess();
        } catch (e) {
            console.error("업로드 실패", e);
        } finally {
            setUploading(false);
        }
    }, [onSuccess]);

    const {getRootProps, getInputProps, isDragActive} = useDropzone({
        onDrop,
        accept: {"application/octet-stream": [".fit"]},
        multiple: true,
    });

    const statusIcon = (status: string) => {
        if (status === "success") return "✅";
        if (status === "skip") return "⏭️";
        return "❌";
    };

    return (
        <div style={{
            position: "fixed", inset: 0, background: "rgba(0,0,0,0.6)",
            display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000
        }}>
            <div style={{
                background: "#1e293b", borderRadius: 12, padding: 24,
                width: 480, maxWidth: "90vw"
            }}>
                <div style={{display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16}}>
                    <h3 style={{color: "#e2e8f0", margin: 0}}>📁 FIT 파일 업로드</h3>
                    <button onClick={onClose} style={{
                        background: "none", border: "none", color: "#94a3b8",
                        fontSize: 20, cursor: "pointer"
                    }}>✕
                    </button>
                </div>

                <div {...getRootProps()} style={{
                    border: `2px dashed ${isDragActive ? "#3b82f6" : "#334155"}`,
                    borderRadius: 8, padding: 40, textAlign: "center", cursor: "pointer",
                    background: isDragActive ? "rgba(59,130,246,0.1)" : "transparent",
                    transition: "all 0.2s"
                }}>
                    <input {...getInputProps()} />
                    <div style={{fontSize: 36, marginBottom: 8}}>📂</div>
                    {isDragActive
                        ? <p style={{color: "#3b82f6", margin: 0}}>파일을 놓으세요</p>
                        : <p style={{color: "#94a3b8", margin: 0}}>FIT 파일을 드래그하거나 클릭해서 선택</p>
                    }
                    <p style={{color: "#475569", fontSize: 12, marginTop: 4}}>다중 파일 업로드 가능</p>
                </div>

                {uploading && (
                    <p style={{color: "#94a3b8", textAlign: "center", marginTop: 16}}>⏳ 파싱 중...</p>
                )}

                {results.length > 0 && (
                    <div style={{marginTop: 16}}>
                        <p style={{color: "#94a3b8", fontSize: 13, marginBottom: 8}}>
                            결과 ({results.length}개)
                        </p>
                        <div style={{
                            maxHeight: 200,
                            overflowY: "auto",
                            display: "flex",
                            flexDirection: "column",
                            gap: 6
                        }}>
                            {results.map((r, i) => (
                                <div key={i} style={{
                                    display: "flex", alignItems: "center", justifyContent: "space-between",
                                    background: "#0f172a", borderRadius: 6, padding: "8px 12px"
                                }}>
                                    <span style={{
                                        color: "#cbd5e1",
                                        fontSize: 13,
                                        overflow: "hidden",
                                        textOverflow: "ellipsis",
                                        whiteSpace: "nowrap",
                                        flex: 1
                                    }}>
                                        {r.filename}
                                    </span>
                                    <span style={{marginLeft: 8}}>{statusIcon(r.status)}</span>
                                    {r.error && (
                                        <span style={{marginLeft: 8, color: "#f87171", fontSize: 11}}>
                                            {r.error}
                                        </span>
                                    )}
                                </div>
                            ))}
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
}

// ── 메인 ─────────────────────────────────────────────────────────
export default function RidingRecordPage() {
    const [activities, setActivities] = useState<Activity[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [selectedActivity, setSelectedActivity] = useState<Activity | null>(null);
    const [showUpload, setShowUpload] = useState(false);

    const loadActivities = () => {
        setLoading(true);
        fetchActivities()
            .then(setActivities)
            .catch((e) => setError(e.message))
            .finally(() => setLoading(false));
    };

    useEffect(() => {
        loadActivities();
    }, []);

    if (loading) return <div className="riding-page loading">로딩 중...</div>;
    if (error) return <div className="riding-page error">에러: {error}</div>;
    return (
        <div className="riding-page">
            {showUpload && (
                <FitUploadModal
                    onClose={() => setShowUpload(false)}
                    onSuccess={() => {
                        setShowUpload(false);
                        loadActivities();
                    }}
                />
            )}

            <div style={{display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 16}}>
                <h2 style={{margin: 0}}>🚴 라이딩 기록</h2>
                <AdminOnly>
                    <button
                        onClick={() => setShowUpload(true)}
                        style={{
                            padding: "6px 14px", background: "#1e3a5f", border: "1px solid #2563eb",
                            borderRadius: 8, color: "#93c5fd", cursor: "pointer", fontSize: 13
                        }}
                    >
                        📁 FIT 업로드
                    </button>
                </AdminOnly>
            </div>

            <div className="riding-layout">
                <div className="activity-feed">
                    {activities.length === 0 ? <p>라이딩 기록이 없습니다.</p> : (
                        activities.map((activity) => (
                            <ActivityCard
                                key={activity.id}
                                activity={activity}
                                selected={selectedActivity?.id === activity.id}
                                onClick={() => setSelectedActivity(
                                    selectedActivity?.id === activity.id ? null : activity
                                )}
                            />
                        ))
                    )}
                </div>
                <AnalysisPanel activity={selectedActivity}/>
            </div>
        </div>
    );
}
