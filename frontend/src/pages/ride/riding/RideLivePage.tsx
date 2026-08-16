import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {useParams, useNavigate} from "react-router-dom";
import mapboxgl from "mapbox-gl";
import "mapbox-gl/dist/mapbox-gl.css";

mapboxgl.accessToken = import.meta.env.VITE_MAPBOX_TOKEN || "";

// ── 타입 (백엔드 ActivityDetailDto/ActivityPointDto/WeatherDto와 1:1 매칭) ──
interface ActivityPointRaw {
    seq: number;
    pointTime: string;
    lat: number;
    lon: number;
    altitude?: number;
    distance?: number;
    speed?: number;
    heartRate?: number;
    power?: number;
    cadence?: number;
}

interface WeatherInfo {
    temperature?: number;
    windSpeed?: number;
    windDeg?: number;
}

interface ActivityDetail {
    id: number;
    name?: string;
    startTime: string;
    totalDistance: number;
    weather?: WeatherInfo;
    points: ActivityPointRaw[];
}

interface TimedPoint extends ActivityPointRaw {
    elapsed: number; // 첫 포인트 대비 경과초
}

// 재생 속도: "N분/초" — 실제 1초가 지날 때 라이딩 기록상 N분이 흘러가도록.
const SPEED_OPTIONS_MIN_PER_SEC = [1, 2, 5, 10];

function formatClock(sec: number) {
    const h = Math.floor(sec / 3600);
    const m = Math.floor((sec % 3600) / 60);
    const s = Math.floor(sec % 60);
    return h > 0
        ? `${h}:${m.toString().padStart(2, "0")}:${s.toString().padStart(2, "0")}`
        : `${m}:${s.toString().padStart(2, "0")}`;
}

const compassOf = (deg: number) => {
    const dirs = ["N", "NE", "E", "SE", "S", "SW", "W", "NW"];
    return dirs[Math.round(deg / 45) % 8];
};

export default function RideLivePage() {
    const {id} = useParams<{ id: string }>();
    const navigate = useNavigate();

    const [activity, setActivity] = useState<ActivityDetail | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(false);

    const [playing, setPlaying] = useState(false);
    const [speedMult, setSpeedMult] = useState(1); // 분/초
    const [elapsedSec, setElapsedSec] = useState(0);
    const [currentIdx, setCurrentIdx] = useState(0);

    const mapContainer = useRef<HTMLDivElement>(null);
    const mapRef = useRef<mapboxgl.Map | null>(null);
    const markerRef = useRef<mapboxgl.Marker | null>(null);
    const mapLoadedRef = useRef(false);

    const rafRef = useRef<number | null>(null);
    const lastFrameRef = useRef<number | null>(null);
    const elapsedRef = useRef(0);
    const playingRef = useRef(false);
    const speedRef = useRef(speedMult);

    useEffect(() => {
        playingRef.current = playing;
    }, [playing]);
    useEffect(() => {
        speedRef.current = speedMult;
    }, [speedMult]);

    useEffect(() => {
        if (!id) return;
        setLoading(true);
        setError(false);
        fetch(`/api/activity/${id}`)
            .then((r) => {
                if (!r.ok) throw new Error("failed");
                return r.json();
            })
            .then((data) => setActivity(data))
            .catch(() => setError(true))
            .finally(() => setLoading(false));
    }, [id]);

    const timedPoints: TimedPoint[] = useMemo(() => {
        const points = activity?.points ?? [];
        if (points.length === 0) return [];
        const t0 = new Date(points[0].pointTime).getTime();
        return points.map((p) => ({
            ...p,
            elapsed: (new Date(p.pointTime).getTime() - t0) / 1000,
        }));
    }, [activity]);

    const totalDuration = timedPoints.length > 0 ? timedPoints[timedPoints.length - 1].elapsed : 0;

    const coords = useMemo<[number, number][]>(
        () => timedPoints.map((p) => [p.lon, p.lat]),
        [timedPoints]
    );

    // 지도 초기화 (경로 데이터가 준비되면 1회)
    useEffect(() => {
        if (!mapContainer.current || coords.length === 0 || mapRef.current) return;

        const map = new mapboxgl.Map({
            container: mapContainer.current,
            style: "mapbox://styles/mapbox/dark-v11",
            center: coords[0],
            zoom: 12,
        });
        mapRef.current = map;

        map.on("load", () => {
            map.addSource("route", {
                type: "geojson",
                data: {type: "Feature", properties: {}, geometry: {type: "LineString", coordinates: coords}},
            });
            map.addLayer({
                id: "route",
                type: "line",
                source: "route",
                layout: {"line-join": "round", "line-cap": "round"},
                paint: {"line-color": "#64748b", "line-width": 3, "line-opacity": 0.55},
            });

            map.addSource("route-progress", {
                type: "geojson",
                data: {type: "Feature", properties: {}, geometry: {type: "LineString", coordinates: [coords[0]]}},
            });
            map.addLayer({
                id: "route-progress",
                type: "line",
                source: "route-progress",
                layout: {"line-join": "round", "line-cap": "round"},
                paint: {"line-color": "#ef4444", "line-width": 4},
            });

            const el = document.createElement("div");
            el.style.width = "16px";
            el.style.height = "16px";
            el.style.borderRadius = "50%";
            el.style.background = "#ef4444";
            el.style.border = "3px solid #fff";
            el.style.boxShadow = "0 0 0 5px rgba(239,68,68,0.35)";
            markerRef.current = new mapboxgl.Marker({element: el}).setLngLat(coords[0]).addTo(map);

            const bounds = coords.reduce(
                (b, c) => b.extend(c),
                new mapboxgl.LngLatBounds(coords[0], coords[0])
            );
            map.fitBounds(bounds, {padding: 60, duration: 0});

            mapLoadedRef.current = true;
        });

        return () => {
            map.remove();
            mapRef.current = null;
            mapLoadedRef.current = false;
        };
    }, [coords]);

    // 경과초 → 포인트 인덱스 (이분탐색)
    const findIndexAt = useCallback(
        (t: number) => {
            if (timedPoints.length === 0) return 0;
            let lo = 0,
                hi = timedPoints.length - 1;
            while (lo < hi) {
                const mid = (lo + hi + 1) >> 1;
                if (timedPoints[mid].elapsed <= t) lo = mid;
                else hi = mid - 1;
            }
            return lo;
        },
        [timedPoints]
    );

    const applyFrame = useCallback(
        (t: number) => {
            if (timedPoints.length === 0) return;
            const idx = findIndexAt(t);
            setCurrentIdx(idx);

            const p0 = timedPoints[idx];
            const p1 = timedPoints[idx + 1];
            let lng = p0.lon,
                lat = p0.lat;
            if (p1 && p1.elapsed > p0.elapsed) {
                const ratio = Math.min(1, Math.max(0, (t - p0.elapsed) / (p1.elapsed - p0.elapsed)));
                lng = p0.lon + (p1.lon - p0.lon) * ratio;
                lat = p0.lat + (p1.lat - p0.lat) * ratio;
            }

            markerRef.current?.setLngLat([lng, lat]);

            const map = mapRef.current;
            if (map && mapLoadedRef.current) {
                const src = map.getSource("route-progress") as mapboxgl.GeoJSONSource | undefined;
                if (src) {
                    const progressCoords = coords.slice(0, idx + 1).concat([[lng, lat]]);
                    src.setData({
                        type: "Feature",
                        properties: {},
                        geometry: {type: "LineString", coordinates: progressCoords},
                    });
                }
                map.easeTo({center: [lng, lat], duration: 200});
            }
        },
        [timedPoints, coords, findIndexAt]
    );

    // 재생 루프
    useEffect(() => {
        function tick(now: number) {
            if (playingRef.current && lastFrameRef.current != null) {
                const dt = (now - lastFrameRef.current) / 1000;
                let next = elapsedRef.current + dt * speedRef.current * 60; // 분/초 → 초/초
                if (next >= totalDuration) {
                    next = totalDuration;
                    playingRef.current = false;
                    setPlaying(false);
                }
                elapsedRef.current = next;
                setElapsedSec(next);
                applyFrame(next);
            }
            lastFrameRef.current = now;
            rafRef.current = requestAnimationFrame(tick);
        }
        rafRef.current = requestAnimationFrame(tick);
        return () => {
            if (rafRef.current) cancelAnimationFrame(rafRef.current);
        };
    }, [applyFrame, totalDuration]);

    // 데이터/지도가 준비되면 시작 위치로 초기 프레임 렌더
    useEffect(() => {
        if (timedPoints.length > 0) applyFrame(elapsedRef.current);
    }, [timedPoints, applyFrame]);

    const handleScrub = (t: number) => {
        elapsedRef.current = t;
        setElapsedSec(t);
        applyFrame(t);
    };

    const handleRestart = () => {
        setPlaying(false);
        handleScrub(0);
    };

    const current = timedPoints[currentIdx];
    const weather = activity?.weather;
    const hasWind = weather?.windDeg != null && weather?.windSpeed != null;

    if (loading) {
        return <div style={S.centerMsg}>불러오는 중...</div>;
    }
    if (error || !activity || timedPoints.length === 0) {
        return (
            <div style={S.centerMsg}>
                <div>경로 데이터를 불러올 수 없습니다.</div>
                <button style={S.backBtn} onClick={() => navigate(`/records/riding/${id}`)}>
                    상세 페이지로 돌아가기
                </button>
            </div>
        );
    }

    return (
        <div style={S.page}>
            <div style={S.topBar}>
                <button style={S.backBtn} onClick={() => navigate(`/records/riding/${id}`)}>
                    ← 상세로
                </button>
                <div style={S.title}>{activity.name || "라이딩 라이브 리플레이"}</div>
            </div>

            <div style={S.mapWrap}>
                <div ref={mapContainer} style={{width: "100%", height: "100%"}}/>

                {hasWind && (
                    <div style={S.windCard}>
                        <div
                            style={{
                                ...S.windArrow,
                                transform: `rotate(${(weather!.windDeg! + 180) % 360}deg)`,
                            }}
                        >
                            ↑
                        </div>
                        <div>
                            <div style={S.windSpeed}>{weather!.windSpeed!.toFixed(1)} m/s</div>
                            <div style={S.windMeta}>
                                {compassOf(weather!.windDeg!)}풍
                                {weather!.temperature != null ? ` · ${weather!.temperature.toFixed(1)}°C` : ""}
                            </div>
                        </div>
                    </div>
                )}

                <div style={S.statsCard}>
                    <div style={S.statRow}>
                        <StatItem label="경과" value={formatClock(elapsedSec)}/>
                        <StatItem label="거리" value={`${((current?.distance ?? 0) / 1000).toFixed(1)} km`}/>
                        <StatItem label="속도" value={`${(current?.speed ?? 0).toFixed(1)} km/h`}/>
                    </div>
                    <div style={S.statRow}>
                        <StatItem label="심박" value={current?.heartRate ? `${Math.round(current.heartRate)} bpm` : "-"}/>
                        <StatItem label="고도" value={current?.altitude != null ? `${Math.round(current.altitude)} m` : "-"}/>
                        <StatItem label="파워" value={current?.power ? `${Math.round(current.power)} W` : "-"}/>
                    </div>
                </div>
            </div>

            <div style={S.controls}>
                <button style={S.iconBtn} onClick={handleRestart} title="처음으로">
                    ⏮
                </button>
                <button style={S.playBtn} onClick={() => setPlaying((p) => !p)}>
                    {playing ? "⏸ 일시정지" : "▶ 재생"}
                </button>
                <input
                    type="range"
                    min={0}
                    max={totalDuration}
                    step={1}
                    value={elapsedSec}
                    onChange={(e) => handleScrub(Number(e.target.value))}
                    style={S.scrub}
                />
                <span style={S.clock}>
                    {formatClock(elapsedSec)} / {formatClock(totalDuration)}
                </span>
                <div style={S.speedGroup}>
                    {SPEED_OPTIONS_MIN_PER_SEC.map((s) => (
                        <button
                            key={s}
                            onClick={() => setSpeedMult(s)}
                            style={{...S.speedBtn, ...(speedMult === s ? S.speedBtnActive : {})}}
                        >
                            {s}분/초
                        </button>
                    ))}
                </div>
            </div>
        </div>
    );
}

function StatItem({label, value}: { label: string; value: string }) {
    return (
        <div style={S.statItem}>
            <div style={S.statValue}>{value}</div>
            <div style={S.statLabel}>{label}</div>
        </div>
    );
}

const S: Record<string, React.CSSProperties> = {
    page: {display: "flex", flexDirection: "column", height: "100%", background: "#0f172a", color: "#e2e8f0"},
    centerMsg: {
        display: "flex",
        flexDirection: "column",
        gap: 12,
        alignItems: "center",
        justifyContent: "center",
        height: "100%",
        color: "#94a3b8",
    },
    topBar: {display: "flex", alignItems: "center", gap: 12, padding: "10px 16px", borderBottom: "1px solid #1e293b"},
    title: {fontSize: 14, fontWeight: 600, color: "#f1f5f9"},
    backBtn: {
        padding: "5px 10px",
        fontSize: 12,
        background: "#1e293b",
        border: "1px solid #334155",
        borderRadius: 6,
        color: "#cbd5e1",
        cursor: "pointer",
    },
    mapWrap: {position: "relative", flex: 1, minHeight: 0},
    windCard: {
        position: "absolute",
        top: 14,
        right: 14,
        display: "flex",
        alignItems: "center",
        gap: 10,
        padding: "10px 14px",
        background: "rgba(15,23,42,0.85)",
        border: "1px solid #334155",
        borderRadius: 10,
        backdropFilter: "blur(4px)",
    },
    windArrow: {fontSize: 22, color: "#38bdf8", transition: "transform 0.3s"},
    windSpeed: {fontSize: 14, fontWeight: 700, color: "#f1f5f9"},
    windMeta: {fontSize: 11, color: "#94a3b8"},
    statsCard: {
        position: "absolute",
        left: 14,
        bottom: 14,
        display: "flex",
        flexDirection: "column",
        gap: 6,
        padding: "10px 14px",
        background: "rgba(15,23,42,0.85)",
        border: "1px solid #334155",
        borderRadius: 10,
        backdropFilter: "blur(4px)",
    },
    statRow: {display: "flex", gap: 18},
    statItem: {minWidth: 60},
    statValue: {fontSize: 15, fontWeight: 700, color: "#f1f5f9"},
    statLabel: {fontSize: 10, color: "#94a3b8", marginTop: 2},
    controls: {
        display: "flex",
        alignItems: "center",
        gap: 10,
        padding: "10px 16px",
        borderTop: "1px solid #1e293b",
        background: "#0b1220",
    },
    iconBtn: {
        padding: "6px 10px",
        background: "#1e293b",
        border: "1px solid #334155",
        borderRadius: 6,
        color: "#cbd5e1",
        cursor: "pointer",
    },
    playBtn: {
        padding: "6px 14px",
        background: "#3a1e1e",
        border: "1px solid #ef4444",
        borderRadius: 6,
        color: "#fca5a5",
        cursor: "pointer",
        fontSize: 13,
        fontWeight: 600,
    },
    scrub: {flex: 1},
    clock: {fontSize: 12, color: "#94a3b8", minWidth: 90, textAlign: "center"},
    speedGroup: {display: "flex", gap: 4},
    speedBtn: {
        padding: "4px 8px",
        fontSize: 11,
        background: "#1e293b",
        border: "1px solid #334155",
        borderRadius: 6,
        color: "#94a3b8",
        cursor: "pointer",
    },
    speedBtnActive: {background: "#1e3a5f", border: "1px solid #2563eb", color: "#93c5fd"},
};
