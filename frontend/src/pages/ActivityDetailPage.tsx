import { useState, useEffect, useRef } from "react";
import { useParams, useNavigate } from "react-router-dom";
import mapboxgl from "mapbox-gl";
import "mapbox-gl/dist/mapbox-gl.css";
import * as polylineLib from "@mapbox/polyline";

mapboxgl.accessToken = import.meta.env.VITE_MAPBOX_TOKEN || "";

// ── 타입 ─────────────────────────────────────────────────────────
interface ActivityDetail {
    id: number;
    startTime: string;
    endTime: string;
    totalDistance: number;
    movingTime: number;
    elapsedTime: number;
    avgSpeed: number;
    maxSpeed: number;
    totalAscent: number;
    totalDescent: number;
    avgHeartRate?: number;
    maxHeartRate?: number;
    avgPower?: number;
    avgCadence?: number;
    calories?: number;
    gearName?: string;
    polyline?: string;
}

interface SegmentEffort {
    effortId: number;
    segmentId: number;
    segmentName: string;
    distance?: number;
    elevationGain?: number;
    avgGrade?: number;
    polyline?: string;
    elapsedTimeSec?: number;
    movingTimeSec?: number;
    avgSpeed?: number;
    avgHeartRate?: number;
    avgPower?: number;
    startDistanceM?: number;
    endDistanceM?: number;
    prRank?: number;
}

interface AnalysisData {
    summary: string;
    intensity: string;
    highlights: string[];
    suggestions: string[];
    score: number;
    weatherImpact?: string;
}

// ── 유틸 ─────────────────────────────────────────────────────────
const formatDistance = (m: number) => (m / 1000).toFixed(1);
const formatTime = (sec: number) => {
    const h = Math.floor(sec / 3600);
    const m = Math.floor((sec % 3600) / 60);
    const s = sec % 60;
    return h > 0
        ? `${h}:${m.toString().padStart(2, "0")}:${s.toString().padStart(2, "0")}`
        : `${m}분 ${s}초`;
};
const formatDate = (dateStr: string) => {
    const d = new Date(dateStr);
    const weekday = ["일", "월", "화", "수", "목", "금", "토"][d.getDay()];
    return `${d.getFullYear()}년 ${d.getMonth() + 1}월 ${d.getDate()}일 (${weekday}) ${d.getHours()}:${d.getMinutes().toString().padStart(2, "0")}`;
};

// ── 지도 컴포넌트 ─────────────────────────────────────────────────
function ActivityMap({ activity, segments, selectedSegId, onSelectSeg }: {
    activity: ActivityDetail;
    segments: SegmentEffort[];
    selectedSegId: number | null;
    onSelectSeg: (id: number | null) => void;
}) {
    const mapRef = useRef<mapboxgl.Map | null>(null);
    const containerRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        if (!containerRef.current || !activity.polyline) return;

        const coords = polylineLib.decode(activity.polyline);
        if (!coords.length) return;

        const map = new mapboxgl.Map({
            container: containerRef.current,
            style: "mapbox://styles/mapbox/dark-v11",
            center: [coords[0][1], coords[0][0]],
            zoom: 11,
        });
        mapRef.current = map;

        map.on("load", () => {
            // 전체 경로
            const routeGeoJson: GeoJSON.Feature<GeoJSON.LineString> = {
                type: "Feature",
                geometry: {
                    type: "LineString",
                    coordinates: coords.map(([lat, lon]) => [lon, lat]),
                },
                properties: {},
            };

            map.addSource("route", { type: "geojson", data: routeGeoJson });
            map.addLayer({
                id: "route",
                type: "line",
                source: "route",
                paint: { "line-color": "#ef4444", "line-width": 3, "line-opacity": 0.8 },
            });

            // 세그먼트 경로들
            segments.forEach((seg, idx) => {
                if (!seg.polyline) return;
                try {
                    const segCoords = polylineLib.decode(seg.polyline);
                    const segGeoJson: GeoJSON.Feature<GeoJSON.LineString> = {
                        type: "Feature",
                        geometry: {
                            type: "LineString",
                            coordinates: segCoords.map(([lat, lon]) => [lon, lat]),
                        },
                        properties: {},
                    };
                    const sourceId = `seg-${seg.effortId}`;
                    map.addSource(sourceId, { type: "geojson", data: segGeoJson });
                    map.addLayer({
                        id: sourceId,
                        type: "line",
                        source: sourceId,
                        paint: {
                            "line-color": seg.prRank === 1 ? "#a855f7" : "#3b82f6",
                            "line-width": 4,
                            "line-opacity": 0.9,
                        },
                    });
                } catch (e) {}
            });

            // 전체 경로 fit
            const lngs = coords.map(([, lon]) => lon);
            const lats = coords.map(([lat]) => lat);
            map.fitBounds(
                [[Math.min(...lngs), Math.min(...lats)], [Math.max(...lngs), Math.max(...lats)]],
                { padding: 40 }
            );
        });

        return () => map.remove();
    }, [activity.polyline]);

    // 선택된 세그먼트 하이라이트
    useEffect(() => {
        const map = mapRef.current;
        if (!map || !map.isStyleLoaded()) return;

        segments.forEach((seg) => {
            const layerId = `seg-${seg.effortId}`;
            if (!map.getLayer(layerId)) return;
            const isSelected = seg.effortId === selectedSegId;
            map.setPaintProperty(layerId, "line-color",
                isSelected ? "#f59e0b" : seg.prRank === 1 ? "#a855f7" : "#3b82f6"
            );
            map.setPaintProperty(layerId, "line-width", isSelected ? 6 : 4);
        });
    }, [selectedSegId]);

    return <div ref={containerRef} style={{ width: "100%", height: "100%" }} />;
}

// ── 메인 ─────────────────────────────────────────────────────────
export default function ActivityDetailPage() {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();

    const [activity, setActivity] = useState<ActivityDetail | null>(null);
    const [segments, setSegments] = useState<SegmentEffort[]>([]);
    const [analysis, setAnalysis] = useState<AnalysisData | null>(null);
    const [analyzing, setAnalyzing] = useState(false);
    const [loading, setLoading] = useState(true);
    const [selectedSegId, setSelectedSegId] = useState<number | null>(null);

    useEffect(() => {
        if (!id) return;
        setLoading(true);

        // activity 먼저 독립적으로 호출
        fetch(`/api/activity/${id}/summary`)
            .then(r => r.json())
            .then(act => setActivity(act))
            .catch(() => {})
            .finally(() => setLoading(false));

        // 세그먼트 독립적으로 호출
        fetch(`/api/activity/${id}/segments`)
            .then(r => r.ok ? r.json() : [])
            .then(segs => setSegments(segs || []))
            .catch(() => setSegments([]));

        // AI 분석 독립적으로 호출
        fetch(`/api/ai/analysis/activity/${id}?userId=1`)
            .then(r => r.ok ? r.json() : null)
            .then(ai => setAnalysis(ai))
            .catch(() => setAnalysis(null));
    }, [id]);

    const handleAnalyze = async () => {
        setAnalyzing(true);
        try {
            const res = await fetch(`/api/ai/analysis/activity/${id}?userId=1`, { method: "POST" });
            if (res.ok) setAnalysis(await res.json());
        } catch (e) {
            console.error(e);
        } finally {
            setAnalyzing(false);
        }
    };

    const intensityColor = (v: string) =>
        ({ "낮음": "#22c55e", "보통": "#3b82f6", "높음": "#f59e0b", "매우높음": "#ef4444" }[v] ?? "#64748b");

    if (loading) return <div style={S.loading}>로딩 중...</div>;
    if (!activity) return <div style={S.loading}>활동을 찾을 수 없습니다.</div>;

    const stats = [
        { label: "거리", val: `${formatDistance(activity.totalDistance)} km` },
        { label: "이동시간", val: formatTime(activity.movingTime) },
        { label: "전체시간", val: formatTime(activity.elapsedTime) },
        { label: "평균속도", val: `${activity.avgSpeed?.toFixed(1)} km/h` },
        { label: "최대속도", val: `${activity.maxSpeed?.toFixed(1)} km/h` },
        { label: "획득고도", val: `${activity.totalAscent?.toFixed(0)} m` },
        { label: "평균심박", val: activity.avgHeartRate ? `${Math.round(activity.avgHeartRate)} bpm` : "-" },
        { label: "최대심박", val: activity.maxHeartRate ? `${Math.round(activity.maxHeartRate)} bpm` : "-" },
        { label: "케이던스", val: activity.avgCadence ? `${Math.round(activity.avgCadence)} rpm` : "-" },
        { label: "칼로리", val: activity.calories ? `${activity.calories} kcal` : "-" },
    ];

    return (
        <div style={S.page}>
            {/* 헤더 */}
            <div style={S.header}>
                <button style={S.backBtn} onClick={() => navigate(-1)}>← 목록</button>
                <div>
                    <h2 style={S.title}>{formatDate(activity.startTime)}</h2>
                    {activity.gearName && <div style={S.gear}>{activity.gearName}</div>}
                </div>
            </div>

            {/* 스탯 바 */}
            <div style={S.statsBar}>
                {stats.map(s => (
                    <div key={s.label} style={S.statItem}>
                        <div style={S.statLabel}>{s.label}</div>
                        <div style={S.statVal}>{s.val}</div>
                    </div>
                ))}
            </div>

            {/* 지도 */}
            {activity.polyline && (
                <div style={S.mapContainer}>
                    <ActivityMap
                        activity={activity}
                        segments={segments}
                        selectedSegId={selectedSegId}
                        onSelectSeg={setSelectedSegId}
                    />
                    <div style={S.mapLegend}>
                        <span style={{ color: "#ef4444" }}>━</span> 전체 경로 &nbsp;
                        <span style={{ color: "#3b82f6" }}>━</span> 세그먼트 &nbsp;
                        <span style={{ color: "#a855f7" }}>━</span> PR &nbsp;
                        <span style={{ color: "#f59e0b" }}>━</span> 선택됨
                    </div>
                </div>
            )}

            <div style={S.layout}>
                {/* 세그먼트 테이블 */}
                <div style={S.card}>
                    <h3 style={S.cardTitle}>🏁 세그먼트 ({segments.length}개)</h3>
                    {segments.length === 0 ? (
                        <div style={S.empty}>세그먼트 기록이 없습니다.</div>
                    ) : (
                        <div style={{ overflowX: "auto" }}>
                            <table style={S.table}>
                                <thead>
                                <tr style={S.thead}>
                                    <th style={S.th}>세그먼트</th>
                                    <th style={{ ...S.th, textAlign: "right" }}>거리</th>
                                    <th style={{ ...S.th, textAlign: "right" }}>시간</th>
                                    <th style={{ ...S.th, textAlign: "right" }}>평균속도</th>
                                    <th style={{ ...S.th, textAlign: "right" }}>심박</th>
                                    <th style={{ ...S.th, textAlign: "right" }}>경사</th>
                                    <th style={{ ...S.th, textAlign: "center" }}>순위</th>
                                </tr>
                                </thead>
                                <tbody>
                                {segments.map((seg) => (
                                    <tr
                                        key={seg.effortId}
                                        style={{
                                            ...S.tr,
                                            background: selectedSegId === seg.effortId ? "#1e3a5f" : "transparent",
                                            cursor: "pointer",
                                        }}
                                        onClick={() => setSelectedSegId(
                                            selectedSegId === seg.effortId ? null : seg.effortId
                                        )}
                                    >
                                        <td style={S.td}>
                                            <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                                                {seg.prRank === 1 && <span style={S.prBadge}>👑 PR</span>}
                                                <span style={{ color: "#93c5fd", fontWeight: 500 }}>
                                                        {seg.segmentName}
                                                    </span>
                                            </div>
                                        </td>
                                        <td style={{ ...S.td, textAlign: "right" }}>
                                            {seg.distance ? `${(seg.distance / 1000).toFixed(2)} km` : "-"}
                                        </td>
                                        <td style={{ ...S.td, textAlign: "right" }}>
                                            {seg.elapsedTimeSec ? formatTime(seg.elapsedTimeSec) : "-"}
                                        </td>
                                        <td style={{ ...S.td, textAlign: "right" }}>
                                            {seg.avgSpeed ? `${(seg.avgSpeed * 3.6).toFixed(1)} km/h` : "-"}
                                        </td>
                                        <td style={{ ...S.td, textAlign: "right" }}>
                                            {seg.avgHeartRate ? `${seg.avgHeartRate} bpm` : "-"}
                                        </td>
                                        <td style={{ ...S.td, textAlign: "right" }}>
                                            {seg.avgGrade ? `${seg.avgGrade.toFixed(1)}%` : "-"}
                                        </td>
                                        <td style={{ ...S.td, textAlign: "center" }}>
                                            {seg.prRank ? (
                                                <span style={{
                                                    padding: "2px 8px",
                                                    borderRadius: 10,
                                                    fontSize: 11,
                                                    fontWeight: 600,
                                                    background: seg.prRank === 1 ? "#7c3aed" : "#1e3a5f",
                                                    color: "#fff"
                                                }}>
                                                        {seg.prRank === 1 ? "PR" : `#${seg.prRank}`}
                                                    </span>
                                            ) : "-"}
                                        </td>
                                    </tr>
                                ))}
                                </tbody>
                            </table>
                        </div>
                    )}
                </div>

                {/* AI 분석 */}
                <div style={S.card}>
                    <div style={S.aiHeader}>
                        <h3 style={S.cardTitle}>🤖 AI 분석</h3>
                        <button
                            style={{ ...S.analyzeBtn, opacity: analyzing ? 0.6 : 1 }}
                            onClick={handleAnalyze}
                            disabled={analyzing}
                        >
                            {analyzing ? "⏳ 분석 중..." : analysis ? "🔄 재분석" : "✨ 분석하기"}
                        </button>
                    </div>

                    {!analysis && !analyzing && (
                        <div style={S.empty}>분석하기 버튼을 눌러주세요.</div>
                    )}

                    {analysis && (
                        <div>
                            <div style={S.scoreRow}>
                                <div style={S.scoreCircle}>{analysis.score}</div>
                                <div>
                                    <div style={S.summary}>{analysis.summary}</div>
                                    <span style={{ ...S.intensityBadge, background: intensityColor(analysis.intensity) }}>
                                        {analysis.intensity}
                                    </span>
                                </div>
                            </div>

                            {analysis.weatherImpact && (
                                <div style={S.weatherImpact}>🌤 {analysis.weatherImpact}</div>
                            )}

                            {analysis.highlights?.length > 0 && (
                                <div style={S.analysisSection}>
                                    <div style={S.analysisSectionTitle}>✅ 잘한 점</div>
                                    {analysis.highlights.map((h, i) => (
                                        <div key={i} style={S.analysisItem}>· {h}</div>
                                    ))}
                                </div>
                            )}

                            {analysis.suggestions?.length > 0 && (
                                <div style={S.analysisSection}>
                                    <div style={S.analysisSectionTitle}>💡 개선점</div>
                                    {analysis.suggestions.map((s, i) => (
                                        <div key={i} style={{ ...S.analysisItem, color: "#f87171" }}>· {s}</div>
                                    ))}
                                </div>
                            )}
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}

// ── 스타일 ────────────────────────────────────────────────────────
const S: Record<string, React.CSSProperties> = {
    page: { padding: "24px", maxWidth: 1400, margin: "0 auto", color: "#e2e8f0" },
    loading: { padding: 40, textAlign: "center", color: "#64748b" },
    header: { display: "flex", alignItems: "flex-start", gap: 16, marginBottom: 20 },
    backBtn: { padding: "8px 16px", background: "#1e293b", border: "1px solid #334155", borderRadius: 8, color: "#94a3b8", cursor: "pointer", fontSize: 13, flexShrink: 0 },
    title: { margin: 0, fontSize: 20, fontWeight: 700, color: "#f1f5f9" },
    gear: { marginTop: 4, fontSize: 13, color: "#64748b" },

    // 스탯 바
    statsBar: { display: "flex", flexWrap: "wrap", gap: 0, marginBottom: 20, background: "#1e293b", borderRadius: 12, border: "1px solid #334155", overflow: "hidden" },
    statItem: { flex: "1 1 10%", padding: "14px 16px", borderRight: "1px solid #334155", minWidth: 80 },
    statLabel: { fontSize: 11, color: "#64748b", marginBottom: 4 },
    statVal: { fontSize: 15, fontWeight: 700, color: "#f1f5f9" },

    // 지도
    mapContainer: { position: "relative", height: 400, borderRadius: 12, overflow: "hidden", marginBottom: 20, border: "1px solid #334155" },
    mapLegend: { position: "absolute", bottom: 10, left: 10, background: "rgba(15,23,42,0.85)", borderRadius: 8, padding: "6px 12px", fontSize: 12, color: "#e2e8f0" },

    // 레이아웃
    layout: { display: "grid", gridTemplateColumns: "1fr 360px", gap: 20 },
    card: { background: "#1e293b", borderRadius: 12, padding: 20, border: "1px solid #334155" },
    cardTitle: { margin: "0 0 16px", fontSize: 15, fontWeight: 600, color: "#f1f5f9" },

    // 테이블
    table: { width: "100%", borderCollapse: "collapse", fontSize: 13 },
    thead: { borderBottom: "1px solid #334155" },
    th: { padding: "8px 12px", color: "#64748b", fontWeight: 500, fontSize: 12, textAlign: "left", whiteSpace: "nowrap" },
    tr: { borderBottom: "1px solid #1e293b", transition: "background 0.15s" },
    td: { padding: "10px 12px", color: "#e2e8f0", whiteSpace: "nowrap" },
    prBadge: { fontSize: 10, padding: "1px 6px", background: "#7c3aed", borderRadius: 8, color: "#fff", fontWeight: 600 },
    empty: { color: "#475569", fontSize: 13, textAlign: "center", padding: "20px 0" },

    // AI 분석
    aiHeader: { display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 },
    analyzeBtn: { padding: "6px 14px", background: "linear-gradient(135deg,#2563eb,#6366f1)", border: "none", borderRadius: 8, color: "#fff", fontSize: 12, fontWeight: 600, cursor: "pointer" },
    scoreRow: { display: "flex", alignItems: "center", gap: 12, marginBottom: 14 },
    scoreCircle: { width: 52, height: 52, borderRadius: "50%", background: "linear-gradient(135deg,#2563eb,#6366f1)", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 18, fontWeight: 800, color: "#fff", flexShrink: 0 },
    summary: { fontSize: 14, fontWeight: 600, color: "#f1f5f9", marginBottom: 6 },
    intensityBadge: { fontSize: 11, padding: "2px 10px", borderRadius: 10, color: "#fff", fontWeight: 600 },
    weatherImpact: { fontSize: 12, color: "#93c5fd", background: "#0f172a", borderRadius: 8, padding: "8px 12px", marginBottom: 12 },
    analysisSection: { marginTop: 12 },
    analysisSectionTitle: { fontSize: 12, fontWeight: 700, color: "#94a3b8", marginBottom: 6 },
    analysisItem: { fontSize: 12, color: "#6ee7b7", padding: "2px 0" },
};
