import {useEffect, useLayoutEffect, useMemo, useRef, useState} from "react";
import {Link} from "react-router-dom";
import * as mapboxgl from "mapbox-gl";
import "mapbox-gl/dist/mapbox-gl.css";
import {fetchMapActivities, type MapActivity} from "@/api/activity";
import {RIDE_TYPE_COLOR, RIDE_TYPE_LABEL} from "@/constants/rideType";
import {
    computeTileStats,
    decodeRoute,
    densify,
    matchesType,
    tileRectPolygon,
    TYPE_FILTERS,
    type LngLat,
    type TypeFilter,
} from "@/pages/ride/riding/ridingMapUtils";
import "@/styles/riding-map.css";

// ── 라이딩 지도 ─────────────────────────────────────────────────
// statshunters 처럼 전체 기록을 한 지도에: 경로 / 히트맵 / 타일 탐험, 연·월·타입 필터.

type ViewMode = "routes" | "heatmap" | "tiles";

const VIEW_MODES: { key: ViewMode; label: string }[] = [
    {key: "routes", label: "🛣️ 경로"},
    {key: "heatmap", label: "🔥 히트맵"},
    {key: "tiles", label: "🟥 타일"},
];

const DEFAULT_COLOR = "#ef4444";
const MONTH_LABELS = ["1월", "2월", "3월", "4월", "5월", "6월", "7월", "8월", "9월", "10월", "11월", "12월"];
// 지도 영역 아래 여백 (페이지 하단 패딩)
const LAYOUT_BOTTOM_SPACE = 24;
const LAYOUT_MIN_HEIGHT = 420;

interface Ride extends MapActivity {
    route: LngLat[];
    dense: LngLat[];   // 타일 판정용 (200m 간격)
    heat: LngLat[];    // 히트맵용 (400m 간격)
    year: number;
    month: number; // 1~12
}

const rideColor = (t?: string) => (t && t !== "GENERAL" ? RIDE_TYPE_COLOR[t] ?? DEFAULT_COLOR : DEFAULT_COLOR);
const km = (m?: number) => ((m ?? 0) / 1000);
const fmtKm = (m: number) => `${Math.round(km(m)).toLocaleString()} km`;
const fmtHours = (s: number) => `${Math.floor(s / 3600).toLocaleString()}시간`;
const fmtDate = (iso: string) => {
    const d = new Date(iso);
    return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, "0")}.${String(d.getDate()).padStart(2, "0")}`;
};

export default function RidingMapPage() {
    const [rides, setRides] = useState<Ride[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const [year, setYear] = useState<number | "ALL">("ALL");
    const [month, setMonth] = useState<number | "ALL">("ALL");
    const [typeFilter, setTypeFilter] = useState<TypeFilter>("ALL");
    const [mode, setMode] = useState<ViewMode>("routes");
    const [selectedId, setSelectedId] = useState<number | null>(null);

    const mapEl = useRef<HTMLDivElement>(null);
    const map = useRef<mapboxgl.Map | null>(null);
    const [mapReady, setMapReady] = useState(false);

    const bodyRef = useRef<HTMLDivElement>(null);
    const [bodyHeight, setBodyHeight] = useState<number | null>(null);

    // ── 데이터 ──
    useEffect(() => {
        fetchMapActivities()
            .then((list) => setRides(list.map((a) => {
                const route = decodeRoute(a.polyline);
                const d = new Date(a.startTime);
                return {...a, route, dense: densify(route, 200), heat: densify(route, 400), year: d.getFullYear(), month: d.getMonth() + 1};
            })))
            .catch((e) => setError(e.message))
            .finally(() => setLoading(false));
    }, []);

    const years = useMemo(() => [...new Set(rides.map((r) => r.year))].sort((a, b) => b - a), [rides]);

    // 타입만 적용한 목록 (연/월 막대그래프용)
    const typeRides = useMemo(() => rides.filter((r) => matchesType(r.rideType, typeFilter)), [rides, typeFilter]);

    const filtered = useMemo(() => typeRides.filter((r) =>
        (year === "ALL" || r.year === year) && (month === "ALL" || r.month === month),
    ), [typeRides, year, month]);

    // 연도 전체면 연도별, 특정 연도면 월별 거리
    const bars = useMemo(() => {
        if (year === "ALL") {
            return [...years].reverse().map((y) => {
                const rs = typeRides.filter((r) => r.year === y);
                return {key: y, label: `${y}`, count: rs.length, dist: rs.reduce((s, r) => s + (r.totalDistance ?? 0), 0)};
            });
        }
        return MONTH_LABELS.map((label, i) => {
            const rs = typeRides.filter((r) => r.year === year && r.month === i + 1);
            return {key: i + 1, label, count: rs.length, dist: rs.reduce((s, r) => s + (r.totalDistance ?? 0), 0)};
        });
    }, [typeRides, year, years]);
    const maxBar = Math.max(1, ...bars.map((b) => b.dist));

    const totals = useMemo(() => ({
        count: filtered.length,
        dist: filtered.reduce((s, r) => s + (r.totalDistance ?? 0), 0),
        ascent: filtered.reduce((s, r) => s + (r.totalAscent ?? 0), 0),
        time: filtered.reduce((s, r) => s + (r.movingTime ?? 0), 0),
    }), [filtered]);

    const tileStats = useMemo(() => computeTileStats(filtered.flatMap((r) => r.dense)), [filtered]);

    // ── 화면 높이: 지도+목록이 한 화면에 들어오게 ──
    useLayoutEffect(() => {
        const measure = () => {
            if (!bodyRef.current) return;
            const top = bodyRef.current.getBoundingClientRect().top;
            setBodyHeight(Math.max(LAYOUT_MIN_HEIGHT, window.innerHeight - top - LAYOUT_BOTTOM_SPACE));
        };
        measure();
        window.addEventListener("resize", measure);
        return () => window.removeEventListener("resize", measure);
    }, [loading]);

    // ── 지도 생성 ──
    useEffect(() => {
        if (loading || !mapEl.current || map.current) return;
        const m = new mapboxgl.Map({
            container: mapEl.current,
            style: "mapbox://styles/mapbox/light-v11",
            center: [127.8, 36.3],
            zoom: 6.3,
            accessToken: import.meta.env.VITE_MAPBOX_TOKEN,
        });
        m.addControl(new mapboxgl.NavigationControl({showCompass: false}), "top-right");
        map.current = m;

        m.on("load", () => {
            const empty = {type: "FeatureCollection", features: []} as GeoJSON.FeatureCollection;
            for (const id of ["rides", "heat", "tiles", "cluster", "square"]) m.addSource(id, {type: "geojson", data: empty});

            // 타일 (경로 아래)
            m.addLayer({id: "tiles-fill", type: "fill", source: "tiles", paint: {"fill-color": "#ef4444", "fill-opacity": 0.22}});
            m.addLayer({id: "cluster-fill", type: "fill", source: "cluster", paint: {"fill-color": "#2563eb", "fill-opacity": 0.35}});
            m.addLayer({id: "tiles-line", type: "line", source: "tiles", paint: {"line-color": "#ef4444", "line-opacity": 0.35, "line-width": 0.5}});
            m.addLayer({id: "square-line", type: "line", source: "square", paint: {"line-color": "#facc15", "line-width": 3}});

            // 히트맵
            m.addLayer({
                id: "heat", type: "heatmap", source: "heat",
                paint: {
                    "heatmap-weight": 0.6,
                    "heatmap-intensity": ["interpolate", ["linear"], ["zoom"], 5, 0.6, 12, 2],
                    "heatmap-radius": ["interpolate", ["linear"], ["zoom"], 5, 3, 10, 8, 14, 16],
                    "heatmap-opacity": 0.85,
                    "heatmap-color": [
                        "interpolate", ["linear"], ["heatmap-density"],
                        0, "rgba(0,0,0,0)", 0.15, "#4c1d95", 0.35, "#be185d", 0.6, "#f97316", 0.85, "#facc15", 1, "#fffbeb",
                    ],
                },
            });

            // 경로
            m.addLayer({
                id: "rides-line", type: "line", source: "rides",
                layout: {"line-join": "round", "line-cap": "round"},
                paint: {"line-color": ["get", "color"], "line-width": ["interpolate", ["linear"], ["zoom"], 5, 1.2, 12, 2.5], "line-opacity": 0.7},
            });
            m.addLayer({
                id: "rides-selected", type: "line", source: "rides",
                filter: ["==", ["get", "id"], -1],
                layout: {"line-join": "round", "line-cap": "round"},
                paint: {"line-color": "#0f172a", "line-width": 5, "line-opacity": 0.9},
            });

            m.on("click", "rides-line", (e) => {
                const id = e.features?.[0]?.properties?.id;
                if (id != null) setSelectedId(Number(id));
            });
            m.on("mouseenter", "rides-line", () => { m.getCanvas().style.cursor = "pointer"; });
            m.on("mouseleave", "rides-line", () => { m.getCanvas().style.cursor = ""; });

            setMapReady(true);
        });

        const ro = new ResizeObserver(() => m.resize());
        ro.observe(mapEl.current);
        return () => { ro.disconnect(); };
    }, [loading]);

    useEffect(() => () => { map.current?.remove(); map.current = null; }, []);

    // ── 필터가 바뀌면 경로 갱신 + 범위 맞춤 ──
    useEffect(() => {
        const m = map.current;
        if (!m || !mapReady) return;

        setSourceData(m, "rides", filtered.filter((r) => r.route.length > 1).map((r) => ({
            type: "Feature",
            properties: {id: r.id, color: rideColor(r.rideType)},
            geometry: {type: "LineString", coordinates: r.route},
        })));

        const pts = filtered.flatMap((r) => r.route);
        if (pts.length > 0) {
            const b = pts.reduce((acc, p) => acc.extend(p), new mapboxgl.LngLatBounds(pts[0], pts[0]));
            m.fitBounds(b, {padding: 40, duration: 600, maxZoom: 12});
        }
    }, [filtered, mapReady]);

    // 히트맵·타일 데이터는 무거워서(점 수만 개) 그 모드를 볼 때만 넣는다
    useEffect(() => {
        const m = map.current;
        if (!m || !mapReady) return;

        setSourceData(m, "heat", mode !== "heatmap" ? [] : filtered.flatMap((r) => r.heat).map((p) => ({
            type: "Feature", properties: {}, geometry: {type: "Point", coordinates: p},
        })));

        const tileFeature = (k: string): GeoJSON.Feature => {
            const [x, y] = k.split(",").map(Number);
            return {type: "Feature", properties: {}, geometry: {type: "Polygon", coordinates: tileRectPolygon(x, y, x, y)}};
        };
        const showTiles = mode === "tiles";
        setSourceData(m, "tiles", showTiles ? [...tileStats.visited].map(tileFeature) : []);
        setSourceData(m, "cluster", showTiles ? [...tileStats.maxCluster].map(tileFeature) : []);
        const sq = tileStats.maxSquare;
        setSourceData(m, "square", showTiles && sq ? [{
            type: "Feature", properties: {},
            geometry: {type: "Polygon", coordinates: tileRectPolygon(sq.x, sq.y, sq.x + sq.size - 1, sq.y + sq.size - 1)},
        }] : []);
    }, [filtered, tileStats, mode, mapReady]);

    // ── 보기 모드 ──
    useEffect(() => {
        const m = map.current;
        if (!m || !mapReady) return;
        const vis = (ids: string[], on: boolean) => ids.forEach((id) => m.setLayoutProperty(id, "visibility", on ? "visible" : "none"));
        vis(["rides-line"], mode !== "heatmap");
        vis(["heat"], mode === "heatmap");
        vis(["tiles-fill", "tiles-line", "cluster-fill", "square-line"], mode === "tiles");
        m.setPaintProperty("rides-line", "line-opacity", mode === "tiles" ? 0.45 : 0.7);
    }, [mode, mapReady]);

    // ── 선택 강조 ──
    useEffect(() => {
        const m = map.current;
        if (!m || !mapReady) return;
        m.setFilter("rides-selected", ["==", ["get", "id"], selectedId ?? -1]);
        const r = filtered.find((x) => x.id === selectedId);
        if (r && r.route.length > 1) {
            const b = r.route.reduce((acc, p) => acc.extend(p), new mapboxgl.LngLatBounds(r.route[0], r.route[0]));
            m.fitBounds(b, {padding: 60, duration: 600, maxZoom: 13});
        }
        if (selectedId != null) {
            document.getElementById(`ride-row-${selectedId}`)?.scrollIntoView({block: "nearest", behavior: "smooth"});
        }
    }, [selectedId, mapReady]);

    // 필터에서 빠진 기록은 선택 해제
    useEffect(() => {
        if (selectedId != null && !filtered.some((r) => r.id === selectedId)) setSelectedId(null);
    }, [filtered, selectedId]);

    if (loading) return <div className="riding-map-page loading">라이딩 지도 불러오는 중... (처음 한 번은 경로 정리로 몇 초 걸려요)</div>;
    if (error) return <div className="riding-map-page error">에러: {error}</div>;

    const changeYear = (v: string) => {
        setYear(v === "ALL" ? "ALL" : Number(v));
        setMonth("ALL");
    };

    return (
        <div className="riding-map-page">
            <div className="rm-header">
                <h2>🗺️ 라이딩 지도</h2>
                <div className="rm-segment">
                    {VIEW_MODES.map((v) => (
                        <button key={v.key} className={mode === v.key ? "active" : ""} onClick={() => setMode(v.key)}>
                            {v.label}
                        </button>
                    ))}
                </div>
            </div>

            <div className="rm-filters">
                <select className="rm-select" value={year} onChange={(e) => changeYear(e.target.value)}>
                    <option value="ALL">전체 기간</option>
                    {years.map((y) => <option key={y} value={y}>{y}년</option>)}
                </select>
                {TYPE_FILTERS.map((t) => (
                    <button
                        key={t.key}
                        className={`rm-chip${typeFilter === t.key ? " active" : ""}`}
                        onClick={() => setTypeFilter(t.key)}
                    >
                        {t.label}
                        <span className="rm-chip-count">{rides.filter((r) => matchesType(r.rideType, t.key)).length}</span>
                    </button>
                ))}
            </div>

            {/* 연도별 / 월별 거리 막대 — 누르면 그 기간만 */}
            <div className="rm-bars">
                <button
                    className={`rm-bar all${(year === "ALL" || month === "ALL") ? " active" : ""}`}
                    onClick={() => year === "ALL" ? undefined : setMonth("ALL")}
                    title={year === "ALL" ? "전체 기간" : `${year}년 전체`}
                >
                    <span className="rm-bar-label">{year === "ALL" ? "전체" : `${year}년`}</span>
                </button>
                {bars.map((b) => {
                    const active = year === "ALL" ? false : month === b.key;
                    return (
                        <button
                            key={b.key}
                            className={`rm-bar${active ? " active" : ""}${b.count === 0 ? " empty" : ""}`}
                            disabled={b.count === 0}
                            onClick={() => year === "ALL" ? changeYear(String(b.key)) : setMonth(active ? "ALL" : (b.key as number))}
                            title={`${b.label} · ${b.count}회 · ${fmtKm(b.dist)}`}
                        >
                            <span className="rm-bar-fill-wrap">
                                <span className="rm-bar-fill" style={{height: `${(b.dist / maxBar) * 100}%`}}/>
                            </span>
                            <span className="rm-bar-value">{b.count ? Math.round(km(b.dist)) : ""}</span>
                            <span className="rm-bar-label">{b.label}</span>
                        </button>
                    );
                })}
            </div>

            <div className="rm-stats">
                <Stat label="기록" value={`${totals.count}회`}/>
                <Stat label="거리" value={fmtKm(totals.dist)}/>
                <Stat label="획득고도" value={`${Math.round(totals.ascent).toLocaleString()} m`}/>
                <Stat label="이동시간" value={fmtHours(totals.time)}/>
                <Stat label="탐험 타일" value={tileStats.visited.size.toLocaleString()} accent="#ef4444"
                      hint="지도를 약 1.9km 칸(줌 14 타일)으로 나눴을 때 지나간 칸 수"/>
                <Stat label="최대 클러스터" value={tileStats.maxCluster.size.toLocaleString()} accent="#2563eb"
                      hint="상하좌우 칸을 모두 지나간 칸들이 이어진 가장 큰 묶음"/>
                <Stat label="최대 스퀘어" value={tileStats.maxSquare ? `${tileStats.maxSquare.size}×${tileStats.maxSquare.size}` : "-"}
                      accent="#ca8a04" hint="빈칸 없이 모두 지나간 가장 큰 정사각형"/>
            </div>

            <div className="rm-body" ref={bodyRef} style={bodyHeight ? {height: bodyHeight} : undefined}>
                <div className="rm-map-wrap">
                    <div ref={mapEl} className="rm-map"/>
                    <MapLegend mode={mode}/>
                </div>

                <div className="rm-list">
                    <div className="rm-list-head">
                        {year === "ALL" ? "전체 기간" : month === "ALL" ? `${year}년` : `${year}년 ${month}월`} · {filtered.length}개
                    </div>
                    {filtered.length === 0 && <div className="rm-list-empty">이 조건의 기록이 없어요.</div>}
                    {filtered.map((r) => (
                        <div
                            key={r.id}
                            id={`ride-row-${r.id}`}
                            className={`rm-row${selectedId === r.id ? " selected" : ""}`}
                            onClick={() => setSelectedId(selectedId === r.id ? null : r.id)}
                        >
                            <span className="rm-row-dot" style={{background: rideColor(r.rideType)}}/>
                            <div className="rm-row-main">
                                <div className="rm-row-top">
                                    <span className="rm-row-date">{fmtDate(r.startTime)}</span>
                                    {r.rideType && r.rideType !== "GENERAL" && (
                                        <span className="rm-row-type" style={{background: rideColor(r.rideType)}}>
                                            {RIDE_TYPE_LABEL[r.rideType] ?? r.rideType}
                                        </span>
                                    )}
                                </div>
                                <div className="rm-row-name">{r.name || "라이딩"}</div>
                            </div>
                            <div className="rm-row-side">
                                <span className="rm-row-km">{km(r.totalDistance).toFixed(1)} km</span>
                                <Link to={`/records/riding/${r.id}`} className="rm-row-link" onClick={(e) => e.stopPropagation()}>
                                    상세 →
                                </Link>
                            </div>
                        </div>
                    ))}
                </div>
            </div>
        </div>
    );
}

function setSourceData(m: mapboxgl.Map, id: string, features: GeoJSON.Feature[]) {
    (m.getSource(id) as mapboxgl.GeoJSONSource).setData({type: "FeatureCollection", features});
}

function Stat({label, value, accent, hint}: { label: string; value: string; accent?: string; hint?: string }) {
    return (
        <div className="rm-stat" title={hint}>
            <span className="rm-stat-label">
                {accent && <span className="rm-stat-dot" style={{background: accent}}/>}
                {label}
            </span>
            <span className="rm-stat-value">{value}</span>
        </div>
    );
}

function MapLegend({mode}: { mode: ViewMode }) {
    if (mode === "heatmap") {
        return <div className="rm-legend"><span className="rm-legend-heat"/>적음 → 많이 다닌 길</div>;
    }
    if (mode === "tiles") {
        return (
            <div className="rm-legend">
                <span><i style={{background: "rgba(239,68,68,0.35)"}}/>탐험</span>
                <span><i style={{background: "rgba(37,99,235,0.5)"}}/>클러스터</span>
                <span><i style={{border: "2px solid #facc15"}}/>스퀘어</span>
            </div>
        );
    }
    return (
        <div className="rm-legend">
            <span><i style={{background: DEFAULT_COLOR}}/>일반</span>
            {["BREVET", "PERMANENT", "FLECHE", "POPULAIRE", "TOURING"].map((t) => (
                <span key={t}><i style={{background: RIDE_TYPE_COLOR[t]}}/>{RIDE_TYPE_LABEL[t]}</span>
            ))}
        </div>
    );
}

