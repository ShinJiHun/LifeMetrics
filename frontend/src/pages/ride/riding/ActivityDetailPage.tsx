import AdminOnly from "@/components/common/AdminOnly";
import {useState, useEffect, useRef} from "react";
import {useParams, useNavigate} from "react-router-dom";
import mapboxgl from "mapbox-gl";
import "mapbox-gl/dist/mapbox-gl.css";
import * as polylineLib from "@mapbox/polyline";
import ActivityChatPopover from "@/components/riding/ActivityChatPopover";

mapboxgl.accessToken = import.meta.env.VITE_MAPBOX_TOKEN || "";

// ── 타입 (백엔드 ActivitySummaryDto와 1:1 매칭) ──────────────────
interface ActivitySummary {
    id: number;
    filename?: string;
    name?: string;
    startTime: string;
    endTime: string;
    totalDistance: number;
    movingTime: number;
    elapsedTime: number;
    avgSpeed: number;
    maxSpeed: number;
    totalAscent: number;
    totalDescent: number;
    gearContext?: { bikeLabel?: string };
    startLat?: number;
    startLon?: number;
    endLat?: number;
    endLon?: number;
    polyline?: string;
    avgHeartRate?: number;
    maxHeartRate?: number;
    avgPower?: number;
    maxPower?: number;
    hasPower?: boolean;
    avgCadence?: number;
    maxCadence?: number;
    calories?: number;
    locationName?: string;
    relativeEffort?: number;
    weather?: WeatherInfo;
}

interface WeatherInfo {
    condition?: string;
    temperature?: number;
    humidity?: number;
    windSpeed?: number;
    windDirection?: string;
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
    maxPower?: number;
    startDistanceM?: number;
    endDistanceM?: number;
    prRank?: number;
}

interface AnalysisData {
    // 핵심
    summary: string;
    intensity: string;
    score: number;

    // 정량 분석 (신규)
    pacingGrade?: string;
    intensityFactor?: number;
    trainingStress?: number;

    // 코칭 인사이트 (신규)
    pacingAnalysis?: string;
    physiologyAnalysis?: string;
    cadenceAnalysis?: string;
    weatherImpact?: string;

    // 기존
    highlights: string[];
    suggestions: string[];

    // 처방 (신규)
    recoveryAdvice?: string;
    nextRideTip?: string;
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

const formatTimeShort = (sec: number) => {
    const h = Math.floor(sec / 3600);
    const m = Math.floor((sec % 3600) / 60);
    return h > 0 ? `${h}:${m.toString().padStart(2, "0")}` : `${m}분`;
};

const formatDate = (dateStr: string) => {
    const d = new Date(dateStr);
    const weekday = ["일", "월", "화", "수", "목", "금", "토"][d.getDay()];
    return `${d.getFullYear()}년 ${d.getMonth() + 1}월 ${d.getDate()}일 (${weekday}) ${d.getHours()}:${d.getMinutes().toString().padStart(2, "0")}`;
};

// 거리 기반 라이딩 종류 분류
const rideTypeLabel = (distanceM: number) => {
    const km = distanceM / 1000;
    if (km < 30) return "단거리 라이딩";
    if (km < 80) return "중거리 라이딩";
    if (km < 150) return "장거리 라이딩";
    if (km < 250) return "초장거리 라이딩";
    if (km < 350) return "🎖️ 브레베 200~300";
    if (km < 500) return "🎖️ 브레베 400";
    if (km < 700) return "🏅 브레베 600";
    if (km < 1100) return "🏆 브레베 1000";
    return "🏆 그랑 브레베 1200+";
};

// ── 지도 컴포넌트 (변경 없음) ─────────────────────────────────────
function ActivityMap({activity, segments, selectedSegId, onSelectSeg}: {
    activity: ActivitySummary;
    segments: SegmentEffort[];
    selectedSegId: number | null;
    onSelectSeg: (id: number | null) => void;
}) {
    const mapRef = useRef<mapboxgl.Map | null>(null);
    const containerRef = useRef<HTMLDivElement>(null);
    const loadedRef = useRef(false);

    useEffect(() => {
        if (!containerRef.current || !activity.polyline) return;

        const coords = polylineLib.decode(activity.polyline);
        if (!coords.length) return;

        const map = new mapboxgl.Map({
            container: containerRef.current,
            style: "mapbox://styles/mapbox/dark-v11",
            center: [coords[0][1], coords[0][0]],
            zoom: 9,
        });
        mapRef.current = map;
        loadedRef.current = false;

        map.on("load", () => {
            map.addSource("route", {
                type: "geojson",
                data: {
                    type: "Feature",
                    geometry: {type: "LineString", coordinates: coords.map(([lat, lon]) => [lon, lat])},
                    properties: {},
                },
            });
            map.addLayer({
                id: "route", type: "line", source: "route",
                paint: {"line-color": "#ef4444", "line-width": 3, "line-opacity": 0.8},
            });

            segments.forEach((seg) => {
                if (!seg.polyline) return;
                try {
                    const segCoords = polylineLib.decode(seg.polyline);
                    const sourceId = `seg-${seg.effortId}`;
                    map.addSource(sourceId, {
                        type: "geojson",
                        data: {
                            type: "Feature",
                            geometry: {type: "LineString", coordinates: segCoords.map(([lat, lon]) => [lon, lat])},
                            properties: {},
                        },
                    });
                    map.addLayer({
                        id: sourceId, type: "line", source: sourceId,
                        paint: {
                            "line-color": seg.prRank === 1 ? "#a855f7" : "#3b82f6",
                            "line-width": 4,
                            "line-opacity": 0.9,
                        },
                    });
                    map.on("click", sourceId, () => {
                        onSelectSeg(seg.effortId === selectedSegId ? null : seg.effortId);
                    });
                    map.on("mouseenter", sourceId, () => {
                        map.getCanvas().style.cursor = "pointer";
                    });
                    map.on("mouseleave", sourceId, () => {
                        map.getCanvas().style.cursor = "";
                    });
                } catch (e) {
                }
            });

            const lngs = coords.map(([, lon]) => lon);
            const lats = coords.map(([lat]) => lat);
            map.fitBounds(
                [[Math.min(...lngs), Math.min(...lats)], [Math.max(...lngs), Math.max(...lats)]],
                {padding: 60, maxZoom: 13, duration: 0}
            );

            loadedRef.current = true;
        });

        return () => {
            map.remove();
            loadedRef.current = false;
        };
    }, [activity.polyline, segments]);

    useEffect(() => {
        const map = mapRef.current;
        if (!map || !loadedRef.current) return;

        const applyHighlight = () => {
            segments.forEach((seg) => {
                const layerId = `seg-${seg.effortId}`;
                if (!map.getLayer(layerId)) return;
                const isSelected = seg.effortId === selectedSegId;
                map.setPaintProperty(layerId, "line-color",
                    isSelected ? "#f59e0b" : seg.prRank === 1 ? "#a855f7" : "#3b82f6"
                );
                map.setPaintProperty(layerId, "line-width", isSelected ? 7 : 4);
                map.setPaintProperty(layerId, "line-opacity", isSelected ? 1 : 0.7);
            });

            if (selectedSegId !== null) {
                const sel = segments.find(s => s.effortId === selectedSegId);
                if (sel?.polyline) {
                    const segCoords = polylineLib.decode(sel.polyline);
                    if (segCoords.length) {
                        const lngs = segCoords.map(([, lon]) => lon);
                        const lats = segCoords.map(([lat]) => lat);
                        map.fitBounds(
                            [[Math.min(...lngs), Math.min(...lats)], [Math.max(...lngs), Math.max(...lats)]],
                            {padding: 80, maxZoom: 15, duration: 600}
                        );
                    }
                }
            }
        };

        if (map.isStyleLoaded()) {
            applyHighlight();
        } else {
            map.once("idle", applyHighlight);
        }
    }, [selectedSegId, segments]);

    return <div ref={containerRef} style={{width: "100%", height: "100%"}}/>;
}

// ── 메인 ─────────────────────────────────────────────────────────
export default function ActivityDetailPage() {
    const {id} = useParams<{ id: string }>();
    const navigate = useNavigate();

    const [activity, setActivity] = useState<ActivitySummary | null>(null);
    const [segments, setSegments] = useState<SegmentEffort[]>([]);
    const [analysis, setAnalysis] = useState<AnalysisData | null>(null);
    const [analyzing, setAnalyzing] = useState(false);
    const [loading, setLoading] = useState(true);
    const [selectedSegId, setSelectedSegId] = useState<number | null>(null);
    const [editingName, setEditingName] = useState(false);
    const [nameInput, setNameInput] = useState("");

    useEffect(() => {
        if (!id) return;
        setLoading(true);

        fetch(`/api/activity/${id}/summary`)
            .then(r => r.json())
            .then(act => setActivity(act))
            .catch(() => {
            })
            .finally(() => setLoading(false));

        fetch(`/api/activity/${id}/segments`)
            .then(r => r.ok ? r.json() : [])
            .then(segs => setSegments(segs || []))
            .catch(() => setSegments([]));

        fetch(`/api/ai/analysis/activity/${id}?userId=1`)
            .then(r => r.ok ? r.json() : null)
            .then(ai => setAnalysis(ai))
            .catch(() => setAnalysis(null));
    }, [id]);

    const handleAnalyze = async () => {
        setAnalyzing(true);
        try {
            const res = await fetch(`/api/ai/analysis/activity/${id}?userId=1`, {method: "POST"});
            if (res.ok) setAnalysis(await res.json());
        } catch (e) {
            console.error(e);
        } finally {
            setAnalyzing(false);
        }
    };

    const handleSaveName = async () => {
        const newName = nameInput.trim();
        if (!newName) {
            setEditingName(false);
            return;
        }

        // 1) 일단 로컬 state 즉시 업데이트 (낙관적 UI)
        setActivity(prev => prev ? {...prev, name: newName} : prev);
        setEditingName(false);

        // 2) 서버 저장 — PATCH 시도, 403/405면 POST로 폴백
        const body = JSON.stringify({name: newName});
        const headers = {"Content-Type": "application/json"};

        try {
            let res = await fetch(`/api/activity/${id}`, {method: "PATCH", headers, body});

            // PATCH가 막히면 (CORS/Security) POST 우회 경로로 재시도
            if (res.status === 403 || res.status === 405) {
                console.warn(`[제목 저장] PATCH ${res.status} → POST 우회 시도`);
                res = await fetch(`/api/activity/${id}/update`, {method: "POST", headers, body});
            }

            if (!res.ok) {
                console.error(`[제목 저장 실패] HTTP ${res.status}`, await res.text());
                alert(`제목 저장 실패: HTTP ${res.status}\nNetwork 탭의 Response를 확인해주세요.`);
                return;
            }

            // 응답 동기화
            const ct = res.headers.get("content-type") || "";
            if (ct.includes("application/json")) {
                const updated = await res.json();
                if (updated && typeof updated === "object" && "id" in updated) {
                    setActivity(updated);
                }
            }
        } catch (e) {
            console.error("[제목 저장 네트워크 에러]", e);
            alert("제목 저장 중 네트워크 에러. 콘솔을 확인해주세요.");
        }
    };

    const intensityColor = (v: string) =>
        ({"낮음": "#22c55e", "보통": "#3b82f6", "높음": "#f59e0b", "매우높음": "#ef4444"}[v] ?? "#64748b");

    if (loading) return <div style={S.loading}>로딩 중...</div>;
    if (!activity) return <div style={S.loading}>활동을 찾을 수 없습니다.</div>;

    return (
        <div style={S.page}>
            {/* Webkit 브라우저용 스크롤바 숨김 (Chrome/Safari/Edge) */}
            <style>{`
                .lm-scroll-hidden::-webkit-scrollbar { display: none; }
            `}</style>

            <div style={S.backRow}>
                <button style={S.backBtn} onClick={() => navigate(-1)}>← 목록</button>
            </div>

            {/* ═══ 상단: 좌측(제목) / 우측(통계) 분할 ═══ */}
            <div style={S.topGrid}>
                {/* 좌측: 제목 영역 */}
                <div style={S.titleCol}>
                    <div style={S.titleHeader}>
                        <div style={S.titleIcon}>
                            <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#3b82f6"
                                 strokeWidth="2">
                                <circle cx="5.5" cy="17.5" r="3.5"/>
                                <circle cx="18.5" cy="17.5" r="3.5"/>
                                <path d="M15 6h4l-3 11M6 17l3.5-9 5 4"/>
                            </svg>
                        </div>
                        <div style={{flex: 1, minWidth: 0}}>
                            <p style={S.subTitle}>
                                {formatDate(activity.startTime)}
                            </p>
                            {editingName ? (
                                <div style={{display: "flex", gap: 6, marginTop: 4}}>
                                    <input
                                        type="text"
                                        value={nameInput}
                                        onChange={e => setNameInput(e.target.value)}
                                        onKeyDown={e => {
                                            if (e.key === "Enter") handleSaveName();
                                            if (e.key === "Escape") setEditingName(false);
                                        }}
                                        autoFocus
                                        style={S.titleInput}
                                    />
                                    <button style={S.smallBtn} onClick={handleSaveName}>저장</button>
                                </div>
                            ) : (
                                <h1 style={S.title}>{activity.name || "라이딩 기록"}</h1>
                            )}
                        </div>
                    </div>

                    {/* 메타 정보 라인 (위치/장비/거리 분류) */}
                    <div style={S.metaList}>
                        {activity.locationName && (
                            <div style={S.metaItem}>
                                <span style={S.metaIcon}>📍</span>
                                <span>{activity.locationName}</span>
                            </div>
                        )}
                        {activity.gearContext?.bikeLabel && (
                            <div style={S.metaItem}>
                                <span style={S.metaIcon}>🚴</span>
                                <span>{activity.gearContext.bikeLabel}</span>
                            </div>
                        )}
                        <div style={S.metaItem}>
                            <span style={S.metaIcon}>🏷️</span>
                            <span>{rideTypeLabel(activity.totalDistance)}</span>
                        </div>
                    </div>

                    {!editingName && (
                        <AdminOnly>
                            <button
                                style={S.editBtn}
                                onClick={() => {
                                    setNameInput(activity.name || "");
                                    setEditingName(true);
                                }}
                            >
                                ✏️ 제목 편집
                            </button>
                        </AdminOnly>
                    )}
                </div>

                {/* 우측: 통계 영역 */}
                <div style={S.statsCol}>
                    {/* 4 핵심 지표 */}
                    <div style={S.keyStatsRow}>
                        <div style={S.keyStat}>
                            <div style={S.keyStatVal}>
                                {formatDistance(activity.totalDistance)}
                                <span style={S.keyStatUnit}>km</span>
                            </div>
                            <div style={S.keyStatLabel}>거리</div>
                        </div>
                        <div style={S.keyStat}>
                            <div style={S.keyStatVal}>{formatTimeShort(activity.movingTime)}</div>
                            <div style={S.keyStatLabel}>이동시간</div>
                        </div>
                        <div style={S.keyStat}>
                            <div style={S.keyStatVal}>
                                {activity.totalAscent?.toFixed(0) ?? "-"}
                                <span style={S.keyStatUnit}>m</span>
                            </div>
                            <div style={S.keyStatLabel}>획득고도</div>
                        </div>
                        <div style={S.keyStat}>
                            <div style={{...S.keyStatVal, color: "#ef4444"}}>
                                {activity.relativeEffort ?? "-"}
                            </div>
                            <div style={S.keyStatLabel}>RE</div>
                        </div>
                    </div>

                    {/* Avg/Max 테이블 */}
                    <table style={S.avgMaxTable}>
                        <thead>
                        <tr>
                            <th style={{...S.avgMaxTh, width: "32%"}}></th>
                            <th style={{...S.avgMaxTh, textAlign: "right"}}>Avg</th>
                            <th style={{...S.avgMaxTh, textAlign: "right"}}>Max</th>
                        </tr>
                        </thead>
                        <tbody>
                        <tr>
                            <td style={S.avgMaxLabel}>속도</td>
                            <td style={S.avgMaxVal}>{activity.avgSpeed?.toFixed(1)} km/h</td>
                            <td style={S.avgMaxVal}>{activity.maxSpeed?.toFixed(1)} km/h</td>
                        </tr>
                        <tr>
                            <td style={S.avgMaxLabel}>심박</td>
                            <td style={S.avgMaxVal}>
                                {activity.avgHeartRate ? `${Math.round(activity.avgHeartRate)} bpm` : "-"}
                            </td>
                            <td style={S.avgMaxVal}>
                                {activity.maxHeartRate ? `${Math.round(activity.maxHeartRate)} bpm` : "-"}
                            </td>
                        </tr>
                        <tr>
                            <td style={S.avgMaxLabel}>케이던스</td>
                            <td style={S.avgMaxVal}>
                                {activity.avgCadence ? `${Math.round(activity.avgCadence)} rpm` : "-"}
                            </td>
                            <td style={S.avgMaxVal}>
                                {activity.maxCadence ? `${Math.round(activity.maxCadence)} rpm` : "-"}
                            </td>
                        </tr>
                        <tr>
                            <td style={S.avgMaxLabel}>파워</td>
                            <td style={S.avgMaxVal}>
                                {activity.avgPower ? `${Math.round(activity.avgPower)} W` : "-"}
                            </td>
                            <td style={S.avgMaxVal}>
                                {activity.maxPower ? `${Math.round(activity.maxPower)} W` : "-"}
                            </td>
                        </tr>
                        {activity.calories && (
                            <tr>
                                <td style={S.avgMaxLabel}>칼로리</td>
                                <td style={S.avgMaxVal} colSpan={2}>{activity.calories.toLocaleString()} kcal</td>
                            </tr>
                        )}
                        </tbody>
                    </table>

                    {/* 날씨 라인 */}
                    {activity.weather && (
                        <div style={S.weatherRow}>
                            {activity.weather.condition && (
                                <span>☁ {activity.weather.condition}
                                    {activity.weather.temperature !== undefined && ` ${activity.weather.temperature}°C`}
                                </span>
                            )}
                            {activity.weather.humidity !== undefined && (
                                <span>습도 {activity.weather.humidity}%</span>
                            )}
                            {activity.weather.windSpeed !== undefined && (
                                <span>바람 {activity.weather.windSpeed} km/h
                                    {activity.weather.windDirection && ` ${activity.weather.windDirection}`}
                                </span>
                            )}
                        </div>
                    )}
                </div>
            </div>

            {/* ═══ 지도 (풀폭) ═══ */}
            {activity.polyline && (
                <div style={S.mapContainer}>
                    <ActivityMap
                        activity={activity}
                        segments={segments}
                        selectedSegId={selectedSegId}
                        onSelectSeg={setSelectedSegId}
                    />
                    <div style={S.mapLegend}>
                        <span style={{color: "#ef4444"}}>━</span> 전체 경로 &nbsp;
                        <span style={{color: "#3b82f6"}}>━</span> 세그먼트 &nbsp;
                        <span style={{color: "#a855f7"}}>━</span> PR &nbsp;
                        <span style={{color: "#f59e0b"}}>━</span> 선택됨
                    </div>
                </div>
            )}

            {/* ═══ 하단: 좌측(세그먼트) / 우측(AI) 분할 ═══ */}
            <div style={S.bottomGrid}>
                {/* 세그먼트 테이블 */}
                <div style={S.card}>
                    <div className="lm-scroll-hidden" style={S.cardScrollBody}>
                        {segments.length === 0 ? (
                            <>
                                <div style={S.cardHeader}>
                                    <h3 style={{...S.cardTitle, color: "#fff"}}>🏁 세그먼트 (0개)</h3>
                                </div>
                                <div style={S.empty}>세그먼트 기록이 없습니다.</div>
                            </>
                        ) : (
                            <table style={S.segTable}>
                                <colgroup>
                                    <col style={{width: "auto"}}/>
                                    <col style={{width: 80}}/>
                                    <col style={{width: 90}}/>
                                    <col style={{width: 130}}/>
                                    <col style={{width: 80}}/>
                                    <col style={{width: 70}}/>
                                </colgroup>
                                <thead>
                                <tr style={S.thead}>
                                    <th style={{
                                        ...S.th, ...S.stickyTh,
                                        textAlign: "left",
                                        color: "#fff",
                                        fontSize: 14,
                                        fontWeight: 600
                                    }}>
                                        🏁 세그먼트 ({segments.length}개)
                                    </th>
                                    <th style={{...S.th, ...S.stickyTh, textAlign: "right", color: "#fff"}}>거리</th>
                                    <th style={{...S.th, ...S.stickyTh, textAlign: "right", color: "#fff"}}>시간</th>
                                    <th style={{
                                        ...S.th, ...S.stickyTh,
                                        textAlign: "right",
                                        color: "#fff"
                                    }}>파워(avg/max)
                                    </th>
                                    <th style={{...S.th, ...S.stickyTh, textAlign: "right", color: "#fff"}}>심박</th>
                                    <th style={{...S.th, ...S.stickyTh, textAlign: "center", color: "#fff"}}>순위</th>
                                </tr>
                                </thead>
                                <tbody>
                                {segments.map((seg) => {
                                    const isSelected = seg.effortId === selectedSegId;
                                    return (
                                        <tr
                                            key={seg.effortId}
                                            style={{
                                                ...S.tr,
                                                background: isSelected ? "rgba(245,158,11,0.12)" : "transparent",
                                                cursor: "pointer",
                                            }}
                                            onClick={() => setSelectedSegId(isSelected ? null : seg.effortId)}
                                        >
                                            <td style={S.tdName}>{seg.segmentName}</td>
                                            <td style={{...S.td, textAlign: "right"}}>
                                                {seg.distance != null ? `${(seg.distance / 1000).toFixed(2)} km` : "-"}
                                            </td>
                                            <td style={{...S.td, textAlign: "right"}}>
                                                {seg.elapsedTimeSec != null ? formatTime(seg.elapsedTimeSec) : "-"}
                                            </td>
                                            <td style={{...S.td, textAlign: "right"}}>
                                                {seg.avgPower != null
                                                    ? `${Math.round(seg.avgPower)}/${seg.maxPower != null ? Math.round(seg.maxPower) : "-"} W`
                                                    : "-"}
                                            </td>
                                            <td style={{...S.td, textAlign: "right"}}>
                                                {seg.avgHeartRate != null ? `${Math.round(seg.avgHeartRate)} bpm` : "-"}
                                            </td>
                                            <td style={{...S.td, textAlign: "center"}}>
                                                {seg.prRank === 1 ? "🏆 PR" : seg.prRank != null ? `${seg.prRank}위` : "-"}
                                            </td>
                                        </tr>
                                    );
                                })}
                                </tbody>
                            </table>
                        )}
                    </div>
                </div>

                {/* AI 분석 */}
                <div style={S.aiCard}>
                    <div style={S.cardHeader}>
                        <h3 style={S.cardTitle}>🤖 AI 분석</h3>
                        <button
                            style={{...S.analyzeBtn, opacity: analyzing ? 0.6 : 1}}
                            onClick={handleAnalyze}
                            disabled={analyzing}
                        >
                            <div className="lm-scroll-hidden" style={S.aiScrollBody}>
                                {analyzing ? "⏳ 분석 중..." : analysis ? "🔄 재분석" : "✨ 분석"}
                            </div>
                        </button>
                    </div>
                    <div className="lm-scroll-hidden" style={S.cardScrollBody}>
                        {!analysis && !analyzing && (
                            <div style={S.empty}>분석 버튼을 눌러주세요.</div>
                        )}

                        {analysis && (
                            <div>
                                {/* 점수 + 한줄 진단 + 강도 뱃지 */}
                                <div style={S.scoreRow}>
                                    <div style={S.scoreCircle}>{analysis.score}</div>
                                    <div style={{flex: 1, minWidth: 0}}>
                                        <div style={S.summary}>{analysis.summary}</div>
                                        <div style={{display: "flex", gap: 6, flexWrap: "wrap", marginTop: 4}}>
                                            <span style={{
                                                ...S.intensityBadge,
                                                background: intensityColor(analysis.intensity)
                                            }}>
                                                {analysis.intensity}
                                            </span>
                                            {analysis.pacingGrade && (
                                                <span style={{...S.intensityBadge, background: "#475569"}}>
                                                    페이싱: {analysis.pacingGrade}
                                                </span>
                                            )}
                                        </div>
                                    </div>
                                </div>

                                {/* 정량 지표 (IF/TSS) */}
                                {(analysis.intensityFactor != null || analysis.trainingStress != null) && (
                                    <div style={S.metricsRow}>
                                        {analysis.intensityFactor != null && (
                                            <div style={S.metricBox}>
                                                <div style={S.metricVal}>{analysis.intensityFactor.toFixed(2)}</div>
                                                <div style={S.metricLabel}>IF</div>
                                            </div>
                                        )}
                                        {analysis.trainingStress != null && (
                                            <div style={S.metricBox}>
                                                <div style={S.metricVal}>{analysis.trainingStress}</div>
                                                <div style={S.metricLabel}>TSS</div>
                                            </div>
                                        )}
                                    </div>
                                )}

                                {/* 코치 노트: 페이싱/생리/케이던스/날씨 */}
                                {(analysis.pacingAnalysis || analysis.physiologyAnalysis ||
                                    analysis.cadenceAnalysis || analysis.weatherImpact) && (
                                    <div style={S.coachNote}>
                                        {analysis.pacingAnalysis && (
                                            <div style={S.coachItem}>
                                                <span style={S.coachLabel}>📊 페이싱</span>
                                                <span style={S.coachText}>{analysis.pacingAnalysis}</span>
                                            </div>
                                        )}
                                        {analysis.physiologyAnalysis && (
                                            <div style={S.coachItem}>
                                                <span style={S.coachLabel}>❤️ 생리</span>
                                                <span style={S.coachText}>{analysis.physiologyAnalysis}</span>
                                            </div>
                                        )}
                                        {analysis.cadenceAnalysis && (
                                            <div style={S.coachItem}>
                                                <span style={S.coachLabel}>🦵 케이던스</span>
                                                <span style={S.coachText}>{analysis.cadenceAnalysis}</span>
                                            </div>
                                        )}
                                        {analysis.weatherImpact && (
                                            <div style={S.coachItem}>
                                                <span style={S.coachLabel}>🌤 날씨</span>
                                                <span style={S.coachText}>{analysis.weatherImpact}</span>
                                            </div>
                                        )}
                                    </div>
                                )}

                                {/* 잘한 점 */}
                                {analysis.highlights?.length > 0 && (
                                    <div style={S.analysisSection}>
                                        <div style={{...S.analysisSectionTitle, color: "#10b981"}}>
                                            ✅ 잘한 점
                                        </div>
                                        {analysis.highlights.map((h, i) => (
                                            <div key={i} style={{...S.analysisItem, color: "#6ee7b7"}}>
                                                · {h}
                                            </div>
                                        ))}
                                    </div>
                                )}

                                {/* 개선점 */}
                                {analysis.suggestions?.length > 0 && (
                                    <div style={S.analysisSection}>
                                        <div style={{...S.analysisSectionTitle, color: "#f59e0b"}}>
                                            💡 개선점
                                        </div>
                                        {analysis.suggestions.map((s, i) => (
                                            <div key={i} style={{...S.analysisItem, color: "#fcd34d"}}>
                                                · {s}
                                            </div>
                                        ))}
                                    </div>
                                )}

                                {/* 회복 처방 */}
                                {analysis.recoveryAdvice && (
                                    <div style={S.analysisSection}>
                                        <div style={{...S.analysisSectionTitle, color: "#a78bfa"}}>
                                            🛌 회복 권장
                                        </div>
                                        <div style={{...S.analysisItem, color: "#c4b5fd"}}>
                                            {analysis.recoveryAdvice}
                                        </div>
                                    </div>
                                )}

                                {/* 다음 라이딩 */}
                                {analysis.nextRideTip && (
                                    <div style={S.analysisSection}>
                                        <div style={{...S.analysisSectionTitle, color: "#60a5fa"}}>
                                            🎯 다음 라이딩
                                        </div>
                                        <div style={{...S.analysisItem, color: "#93c5fd"}}>
                                            {analysis.nextRideTip}
                                        </div>
                                    </div>
                                )}
                            </div>
                        )}
                    </div>
                </div>
            </div>

            {/* 우측 하단 채팅 FAB */}
            <ActivityChatPopover activityId={activity.id}/>
        </div>
    );
}

// ── 스타일 ────────────────────────────────────────────────────────
const S: Record<string, React.CSSProperties> = {
    page: {maxWidth: 1400, color: "#e2e8f0"},
    loading: {padding: 40, textAlign: "center", color: "#64748b"},
    backRow: {marginBottom: 14},
    backBtn: {
        padding: "6px 14px",
        background: "#1e293b",
        border: "1px solid #334155",
        borderRadius: 8,
        color: "#94a3b8",
        cursor: "pointer",
        fontSize: 13,
    },
    topGrid: {
        display: "grid",
        gridTemplateColumns: "1fr 1.8fr",   // ★ 좌측 좁히고 우측 넓힘
        gap: 0,
        marginBottom: 16,
        background: "#1e293b",
        borderRadius: 12,
        border: "1px solid #334155",
        overflow: "hidden",
    },
    titleCol: {
        padding: "20px 24px",
        borderRight: "1px solid #334155",
        display: "flex",
        flexDirection: "column",
        justifyContent: "space-between",
    },
    titleHeader: {display: "flex", gap: 12, alignItems: "flex-start"},
    titleIcon: {
        width: 48,                    // ★ 40 → 48
        height: 48,
        borderRadius: 10,
        background: "rgba(59,130,246,0.15)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        flexShrink: 0,
    },
    metaList: {
        marginTop: 16,
        display: "flex",
        flexDirection: "column",
        gap: 8,
    },
    metaItem: {
        display: "flex",
        alignItems: "center",
        gap: 8,
        fontSize: 12,
        color: "#94a3b8",
    },
    metaIcon: {
        fontSize: 13,
        opacity: 0.9,
    },
    subTitle: {margin: 0, fontSize: 11, color: "#64748b"},
    title: {
        margin: "4px 0 6px",
        fontSize: 19,
        fontWeight: 700,
        color: "#f1f5f9",
        lineHeight: 1.3,
        wordBreak: "keep-all",
    },
    titleInput: {
        flex: 1,
        padding: "6px 10px",
        background: "#0f172a",
        border: "1px solid #475569",
        borderRadius: 6,
        color: "#f1f5f9",
        fontSize: 15,
        fontWeight: 500,
    },
    smallBtn: {
        padding: "4px 12px",
        background: "#3b82f6",
        border: "none",
        borderRadius: 6,
        color: "#fff",
        fontSize: 12,
        fontWeight: 600,
        cursor: "pointer",
    },
    gearLine: {margin: "4px 0 0", fontSize: 12, color: "#94a3b8"},
    editBtn: {
        marginTop: 14,
        padding: "5px 12px",
        background: "transparent",
        border: "1px solid #334155",
        borderRadius: 6,
        color: "#94a3b8",
        fontSize: 11,
        cursor: "pointer",
        alignSelf: "flex-start",
    },
    statsCol: {padding: "20px 24px"},
    keyStatsRow: {
        display: "grid",
        gridTemplateColumns: "repeat(4, 1fr)",
        gap: 16,
        paddingBottom: 14,
        borderBottom: "1px solid #334155",
    },
    keyStat: {},
    keyStatVal: {
        fontSize: 24,
        fontWeight: 700,
        color: "#f1f5f9",
        lineHeight: 1,
    },
    keyStatUnit: {
        fontSize: 11,
        fontWeight: 400,
        color: "#94a3b8",
        marginLeft: 3,
    },
    keyStatLabel: {
        marginTop: 6,
        fontSize: 10,
        color: "#64748b",
        textTransform: "uppercase",
        letterSpacing: "0.5px",
    },
    avgMaxTable: {width: "100%", borderCollapse: "collapse", fontSize: 12, marginTop: 10},
    avgMaxTh: {
        padding: "6px 0",
        color: "#64748b",
        fontWeight: 500,
        fontSize: 11,
        textAlign: "left",
    },
    avgMaxLabel: {padding: "5px 0", color: "#94a3b8", fontSize: 12},
    avgMaxVal: {padding: "5px 0", color: "#e2e8f0", fontSize: 12, textAlign: "right"},
    weatherRow: {
        marginTop: 10,
        paddingTop: 10,
        borderTop: "1px solid #334155",
        display: "flex",
        gap: 14,
        flexWrap: "wrap",
        fontSize: 11,
        color: "#94a3b8",
    },
    mapContainer: {
        position: "relative",
        height: 360,
        borderRadius: 12,
        overflow: "hidden",
        marginBottom: 16,
        border: "1px solid #334155",
    },
    mapLegend: {
        position: "absolute",
        bottom: 10,
        left: 10,
        background: "rgba(15,23,42,0.85)",
        borderRadius: 8,
        padding: "6px 12px",
        fontSize: 12,
        color: "#e2e8f0",
    },
    bottomGrid: {
        display: "grid",
        gridTemplateColumns: "2fr 1fr",   // ★ 세그먼트 더 넓게, AI 좁게
        gap: 16,
        height: 600,
    },
    aiScrollBody: {
        paddingTop: 14,
        paddingLeft: 14,
        paddingRight: 14,
        paddingBottom: 6,
        overflow: "auto",
        maxHeight: 500,
    },
    card: {
        marginBottom: 8,
        paddingTop: 8,
        background: "#1e293b",
        borderRadius: 12,
        border: "1px solid #334155",
        display: "flex",
        flexDirection: "column",
        overflow: "hidden",
    },
    cardHeader: {
        padding: "14px 14px",         // ← 위아래 8px (버튼 위아래 여백)
        borderBottom: "1px solid #334155",
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        flexShrink: 0,
    },
    cardScrollBody: {
        paddingTop: 0,
        // 좌우 padding이 필요하면 paddingLeft/Right로 분리
        paddingLeft: 12,
        paddingRight: 12,
        paddingBottom: 12,
        overflow: "auto",
        maxHeight: 500,  // 기존 값 유지
    },
    cardTitle: {margin: 0, fontSize: 14, fontWeight: 600, color: "#f1f5f9"},

    segTable: {
        width: "100%",
        borderCollapse: "collapse",
        fontSize: 12,
        tableLayout: "fixed",          // ★ 고정 레이아웃으로 컬럼 너비 강제
    },
    thead: {borderBottom: "1px solid #334155"},
    th: {
        padding: "6px 10px",
        color: "#64748b",
        fontWeight: 500,
        fontSize: 11,
        textAlign: "left",
        whiteSpace: "nowrap",
        position: "sticky",
        top: 0,
        background: "#1e293b",
        zIndex: 1,
        height: 36,                  // ← 추가: cardHeader와 동일 높이
        boxSizing: "border-box",     // ← 추가
    },
    tr: {borderBottom: "1px solid #1e293b", transition: "background 0.15s"},
    td: {padding: "8px 10px", color: "#e2e8f0", whiteSpace: "nowrap"},
    tdName: {                          // ★ 세그먼트명 컬럼: 길면 줄바꿈
        padding: "8px 10px",
        color: "#e2e8f0",
        wordBreak: "keep-all",
        overflow: "hidden",
        textOverflow: "ellipsis",
        whiteSpace: "nowrap",
    },
    empty: {color: "#475569", fontSize: 13, textAlign: "center", padding: "20px 0"},

    // ── AI 사이드바 ──
    aiHeader: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        marginBottom: 14,
    },
    analyzeBtn: {
        padding: "2px 8px",        // ← 위아래 3px로 줄임
        background: "linear-gradient(135deg,#2563eb,#6366f1)",
        border: "none",
        borderRadius: 6,
        color: "#fff",
        fontSize: 11,
        fontWeight: 600,
        cursor: "pointer",
        whiteSpace: "nowrap",
        alignSelf: "center",
    },
    aiCard: {
        background: "#1e293b",
        borderRadius: 12,
        border: "1px solid #334155",
        backgroundImage: "linear-gradient(180deg, rgba(99,102,241,0.06) 0%, rgba(99,102,241,0) 60%)",
        display: "flex",
        flexDirection: "column",
        overflow: "hidden",
        // paddingTop: 10  ← 이 줄 완전히 삭제
    },
    scoreRow: {
        display: "flex",
        alignItems: "center",
        gap: 12,
        marginBottom: 14,
    },
    scoreCircle: {
        width: 48,
        height: 48,
        borderRadius: "50%",
        background: "linear-gradient(135deg,#2563eb,#6366f1)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        fontSize: 17,
        fontWeight: 800,
        color: "#fff",
        flexShrink: 0,
    },
    summary: {
        fontSize: 13,
        fontWeight: 600,
        color: "#f1f5f9",
        marginBottom: 5,
        lineHeight: 1.4,
    },
    intensityBadge: {
        fontSize: 10,
        padding: "2px 9px",
        borderRadius: 10,
        color: "#fff",
        fontWeight: 600,
    },
    weatherImpact: {
        fontSize: 12,
        color: "#93c5fd",
        background: "#0f172a",
        borderRadius: 8,
        padding: "8px 12px",
        marginBottom: 12,
        lineHeight: 1.5,
    },

    // ── 코치 노트 (라이덕/STRANK 스타일) ──
    metricsRow: {
        display: "grid",
        gridTemplateColumns: "1fr 1fr",
        gap: 8,
        marginBottom: 12,
        marginTop: 4,
    },
    metricBox: {
        background: "#0f172a",
        border: "1px solid #334155",
        borderRadius: 8,
        padding: "8px 10px",
        textAlign: "center",
    },
    metricVal: {
        fontSize: 14,
        fontWeight: 700,
        color: "#f1f5f9",
        lineHeight: 1,
    },
    metricLabel: {
        fontSize: 10,
        color: "#64748b",
        marginTop: 4,
        textTransform: "uppercase",
        letterSpacing: "0.5px",
    },
    coachNote: {
        marginTop: 4,
        marginBottom: 12,
        padding: "10px 12px",
        background: "rgba(99,102,241,0.06)",
        borderRadius: 8,
        borderLeft: "3px solid #6366f1",
    },
    coachItem: {
        display: "flex",
        gap: 8,
        marginBottom: 6,
        alignItems: "flex-start",
        fontSize: 11,
        lineHeight: 1.5,
    },
    coachLabel: {
        color: "#a5b4fc",
        fontWeight: 600,
        whiteSpace: "nowrap",
        minWidth: 50,
    },
    coachText: {
        color: "#cbd5e1",
        flex: 1,
    },
    analysisSection: {marginTop: 12},
    analysisSectionTitle: {
        fontSize: 11,
        fontWeight: 700,
        marginBottom: 5,
    },
    analysisItem: {
        fontSize: 12,
        padding: "2px 0",
        lineHeight: 1.5,
        whiteSpace: "normal",
    },
    stickyTh: {
        position: "sticky",
        top: 0,
        background: "#1e293b",
        zIndex: 2,
    },
};
