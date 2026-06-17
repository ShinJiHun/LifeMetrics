import { useEffect, useMemo, useRef, useState } from "react";
import mapboxgl from "mapbox-gl";
import "mapbox-gl/dist/mapbox-gl.css";
import { useLiveRide, type CpPlan } from "@/hooks/useLiveRide.ts";
import { installFixtureGeolocation, type FixtureController, type GeoFix } from "./fixtureGeolocation";

interface BrevetCourse {
    folderName: string; courseName: string; gpxFileName: string; distance: string; fileSizeKb: number;
}
interface GpxTrack {
    name: string;
    points: { lat: number; lon: number; ele: number }[];
    distances: number[];
    totalDist: number;
    gain: number;
    keyPoints: { lat: number; lon: number; ele: number; name: string; distFromStart: number }[];
}

// ── GPX 파서 (BrevePlanPage와 동일 로직 축약) ──
function parseGPX(xml: string): GpxTrack | null {
    const doc = new DOMParser().parseFromString(xml, "text/xml");
    const trkpts = Array.from(doc.querySelectorAll("trkpt"));
    const wpts = Array.from(doc.querySelectorAll("wpt"));
    const name = doc.querySelector("name")?.textContent?.trim() || "GPX 루트";
    const points = trkpts.map(pt => ({
        lat: parseFloat(pt.getAttribute("lat")!),
        lon: parseFloat(pt.getAttribute("lon")!),
        ele: parseFloat(pt.querySelector("ele")?.textContent || "0"),
    })).filter(p => !isNaN(p.lat));
    if (!points.length) return null;

    const R = 6371000;
    const distances = [0];
    for (let i = 1; i < points.length; i++) {
        const dLat = ((points[i].lat - points[i - 1].lat) * Math.PI) / 180;
        const dLon = ((points[i].lon - points[i - 1].lon) * Math.PI) / 180;
        const a = Math.sin(dLat / 2) ** 2 +
            Math.cos((points[i - 1].lat * Math.PI) / 180) * Math.cos((points[i].lat * Math.PI) / 180) * Math.sin(dLon / 2) ** 2;
        distances.push(distances[i - 1] + R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
    }
    const totalDist = distances[distances.length - 1];
    let gain = 0;
    for (let i = 1; i < points.length; i++) { const d = points[i].ele - points[i - 1].ele; if (d > 0) gain += d; }

    const waypoints = wpts.map(w => ({
        lat: parseFloat(w.getAttribute("lat")!),
        lon: parseFloat(w.getAttribute("lon")!),
        ele: parseFloat(w.querySelector("ele")?.textContent || "0"),
        name: w.querySelector("name")?.textContent?.trim() || "WPT",
    })).filter(w => !isNaN(w.lat));

    let keyPoints: GpxTrack["keyPoints"] = [];
    if (waypoints.length) {
        keyPoints = waypoints.map(w => {
            let minD = Infinity, idx = 0;
            points.forEach((p, i) => { const d = Math.hypot(p.lat - w.lat, p.lon - w.lon); if (d < minD) { minD = d; idx = i; } });
            return { ...w, distFromStart: distances[idx] };
        }).sort((a, b) => a.distFromStart - b.distFromStart);
    } else {
        const segs = Math.min(4, Math.max(2, Math.floor(totalDist / 20000)));
        for (let s = 1; s < segs; s++) {
            const target = (totalDist / segs) * s; let minD = Infinity, idx = 0;
            distances.forEach((d, i) => { if (Math.abs(d - target) < minD) { minD = Math.abs(d - target); idx = i; } });
            keyPoints.push({ lat: points[idx].lat, lon: points[idx].lon, ele: points[idx].ele, name: `CP${s}`, distFromStart: distances[idx] });
        }
    }
    const allKP = [
        { lat: points[0].lat, lon: points[0].lon, ele: points[0].ele, name: "START", distFromStart: 0 },
        ...keyPoints,
        { lat: points[points.length - 1].lat, lon: points[points.length - 1].lon, ele: points[points.length - 1].ele, name: "FINISH", distFromStart: totalDist },
    ];
    return { name, points, distances, totalDist, gain, keyPoints: allKP };
}

function calcDeadline(startTime: string, distKm: number, totalKm: number, limitH: number): string {
    const [sh, sm] = startTime.split(":").map(Number);
    const mins = sh * 60 + sm + Math.round((distKm / totalKm) * limitH * 60);
    return `${String(Math.floor(mins / 60) % 24).padStart(2, "0")}:${String(mins % 60).padStart(2, "0")}`;
}

function posAtDistance(track: GpxTrack, distM: number) {
    const d = track.distances, p = track.points;
    if (distM <= 0) return p[0];
    if (distM >= track.totalDist) return p[p.length - 1];
    let i = 1; while (i < d.length && d[i] < distM) i++;
    const t = (distM - d[i - 1]) / ((d[i] - d[i - 1]) || 1);
    const a = p[i - 1], b = p[i];
    return { lat: a.lat + (b.lat - a.lat) * t, lon: a.lon + (b.lon - a.lon) * t };
}

// 트랙에서 테스트용 fixture 자동 생성 (목표속도 기준 페이스)
function buildFixtures(track: GpxTrack, targetSpeed: number): GeoFix[] {
    const totalKm = track.totalDist / 1000;
    const fracs = [0, 0.15, 0.35, 0.55, 0.75, 0.9, 1.0];
    const fixes: GeoFix[] = fracs.map(f => {
        const pos = posAtDistance(track, f * track.totalDist);
        const km = f * totalKm;
        return { lat: pos.lat, lon: pos.lon, timeMin: Math.round((km / targetSpeed) * 60), label: `${Math.round(f * 100)}% · ${km.toFixed(0)}km` };
    });
    const mid = posAtDistance(track, track.totalDist * 0.5);
    fixes.splice(4, 0, { lat: mid.lat + 0.03, lon: mid.lon + 0.03, timeMin: Math.round((totalKm * 0.5 / targetSpeed) * 60) + 3, label: "⚠️ 경로이탈 테스트" });
    return fixes;
}

// ── 라이브 지도 (경로 + 현재위치 마커) ──
function LiveMap({ track, livePos }: { track: GpxTrack | null; livePos: { lat: number; lon: number } | null }) {
    const containerRef = useRef<HTMLDivElement>(null);
    const mapRef = useRef<mapboxgl.Map | null>(null);
    const markerRef = useRef<mapboxgl.Marker | null>(null);

    useEffect(() => {
        if (!containerRef.current) return;
        mapboxgl.accessToken = import.meta.env.VITE_MAPBOX_TOKEN as string;
        const map = new mapboxgl.Map({
            container: containerRef.current,
            style: "mapbox://styles/mapbox/outdoors-v12",
            center: track ? [track.keyPoints[0].lon, track.keyPoints[0].lat] : [127.5, 36.5],
            zoom: track ? 9 : 7,
        });
        mapRef.current = map;
        map.addControl(new mapboxgl.NavigationControl(), "top-right");

        const el = document.createElement("div");
        el.style.cssText = "width:16px;height:16px;border-radius:50%;background:#facc15;border:3px solid #fff;box-shadow:0 0 0 4px rgba(250,204,21,0.35),0 2px 8px rgba(0,0,0,0.5);";
        markerRef.current = new mapboxgl.Marker({ element: el, anchor: "center" }).setLngLat([127.5, 36.5]);

        map.on("load", () => {
            markerRef.current!.addTo(map);
            if (!track) return;
            map.addSource("route", { type: "geojson", data: { type: "Feature", properties: {}, geometry: { type: "LineString", coordinates: track.points.map(p => [p.lon, p.lat]) } } });
            map.addLayer({ id: "route", type: "line", source: "route", layout: { "line-join": "round", "line-cap": "round" }, paint: { "line-color": "#3b82f6", "line-width": 4, "line-opacity": 0.9 } });
            const lons = track.points.map(p => p.lon), lats = track.points.map(p => p.lat);
            map.fitBounds([[Math.min(...lons), Math.min(...lats)], [Math.max(...lons), Math.max(...lats)]], { padding: 50, duration: 800 });
            track.keyPoints.forEach((kp, i) => {
                const c = i === 0 ? "#22c55e" : i === track.keyPoints.length - 1 ? "#ef4444" : "#f59e0b";
                const m = document.createElement("div");
                m.style.cssText = `background:${c};color:#fff;font-size:10px;font-weight:700;padding:2px 6px;border-radius:4px;white-space:nowrap;`;
                m.textContent = kp.name;
                new mapboxgl.Marker({ element: m, anchor: "bottom" }).setLngLat([kp.lon, kp.lat]).addTo(map);
            });
        });
        return () => { markerRef.current?.remove(); map.remove(); mapRef.current = null; };
    }, [track]);

    useEffect(() => {
        if (livePos && markerRef.current) {
            markerRef.current.setLngLat([livePos.lon, livePos.lat]);
            mapRef.current?.easeTo({ center: [livePos.lon, livePos.lat], duration: 500 });
        }
    }, [livePos]);

    return <div ref={containerRef} style={{ width: "100%", height: "100%" }} />;
}

// ── 메인 ──
export default function LiveRidePage() {
    const [courses, setCourses] = useState<BrevetCourse[]>([]);
    const [selectedCourse, setSelectedCourse] = useState("");
    const [track, setTrack] = useState<GpxTrack | null>(null);
    const [gpxError, setGpxError] = useState("");
    const [timeLimit, setTimeLimit] = useState("13.5");
    const [targetSpeed, setTargetSpeed] = useState("20");
    const [started, setStarted] = useState(false);
    const [mode, setMode] = useState<"real" | "fixture">("fixture");
    const [startDateTime, setStartDateTime] = useState<Date | null>(null);
    const [startTimeStr, setStartTimeStr] = useState("06:00");
    const [fixtures, setFixtures] = useState<GeoFix[]>([]);
    const [fixIdx, setFixIdx] = useState(0);
    const ctrlRef = useRef<FixtureController | null>(null);

    useEffect(() => {
        fetch("/api/brevet/courses").then(r => r.json()).then((d: BrevetCourse[]) => setCourses(d)).catch(() => {});
    }, []);
    useEffect(() => () => { ctrlRef.current?.uninstall(); }, []);

    const handleCourseSelect = async (value: string) => {
        setSelectedCourse(value); if (!value) return;
        const [folderName, gpxFileName] = value.split("::");
        setGpxError("");
        try {
            const res = await fetch(`/api/brevet/courses/${encodeURIComponent(folderName)}/gpx?file=${encodeURIComponent(gpxFileName)}`);
            if (!res.ok) throw new Error();
            const parsed = parseGPX(await res.text());
            if (!parsed) { setGpxError("GPX 파싱 실패"); setTrack(null); } else setTrack(parsed);
        } catch { setGpxError("GPX 로드 실패"); setTrack(null); }
    };

    const handleGpxFile = (file: File) => {
        setSelectedCourse(""); setGpxError("");
        const reader = new FileReader();
        reader.onload = e => {
            const parsed = parseGPX(e.target!.result as string);
            if (!parsed) { setGpxError("GPX 파싱 실패"); setTrack(null); } else setTrack(parsed);
        };
        reader.readAsText(file);
    };

    const totalKm = track ? track.totalDist / 1000 : 0;

    const cps: CpPlan[] = useMemo(() => {
        if (!track) return [];
        return track.keyPoints.map((kp, i) => ({
            name: kp.name,
            distance: Math.round(kp.distFromStart / 1000),
            deadline: i === 0 ? "-" : calcDeadline(startTimeStr, kp.distFromStart / 1000, totalKm, Number(timeLimit)),
        }));
    }, [track, startTimeStr, timeLimit, totalKm]);

    const live = useLiveRide(track, cps, started ? startDateTime : null);

    const startRide = (m: "real" | "fixture") => {
        if (!track) return;
        const now = new Date();
        setStartTimeStr(`${String(now.getHours()).padStart(2, "0")}:${String(now.getMinutes()).padStart(2, "0")}`);
        setStartDateTime(now);
        setMode(m);
        if (m === "fixture") {
            const fx = buildFixtures(track, Number(targetSpeed));
            setFixtures(fx); setFixIdx(0);
            ctrlRef.current = installFixtureGeolocation(fx, now.getTime());
        }
        setStarted(true);
    };

    const stopRide = () => {
        ctrlRef.current?.uninstall(); ctrlRef.current = null;
        setStarted(false); setStartDateTime(null);
    };

    const stepFix = (dir: "next" | "prev") => {
        if (!ctrlRef.current) return;
        dir === "next" ? ctrlRef.current.next() : ctrlRef.current.prev();
        setFixIdx(ctrlRef.current.index());
    };

    const fmtMin = (m: number) => {
        if (!isFinite(m)) return "-";
        const s = m < 0 ? "-" : "";
        const a = Math.abs(Math.round(m));
        return `${s}${Math.floor(a / 60)}h ${a % 60}m`;
    };

    return (
        <div style={S.page}>
            <style>{`@import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:wght@400;500;600&family=Exo+2:wght@700;800&display=swap'); * { box-sizing: border-box; } input[type=number]{color-scheme:dark;} select option{background:#0f172a;color:#e2e8f0;}`}</style>

            <div style={S.header}>
                <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
                    <span style={{ fontSize: 32 }}>🛰️</span>
                    <div>
                        <h1 style={S.title}>라이브 라이딩</h1>
                        <p style={S.subtitle}>Live Ride · 현재 위치 기반 진행/페이스</p>
                    </div>
                </div>
                {started && <button style={S.backBtn} onClick={stopRide}>■ 종료</button>}
            </div>

            {!started ? (
                <div style={S.formCard}>
                    <h2 style={S.sectionTitle}>코스 / 설정</h2>
                    <div style={S.formGrid}>
                        <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                            <label style={S.label}>저장된 코스</label>
                            <select style={{ ...S.input, cursor: "pointer" }} value={selectedCourse} onChange={e => handleCourseSelect(e.target.value)}>
                                <option value="">-- NAS 코스 선택 --</option>
                                {["200K", "300K", "400K", "600K", "1000K", "1200K", "-"].map(dist => {
                                    const f = courses.filter(c => c.distance === dist);
                                    if (!f.length) return null;
                                    return <optgroup key={dist} label={`── ${dist} ──`}>
                                        {f.map(c => <option key={`${c.folderName}::${c.gpxFileName}`} value={`${c.folderName}::${c.gpxFileName}`}>{c.courseName}</option>)}
                                    </optgroup>;
                                })}
                            </select>
                        </div>
                        <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                            <label style={S.label}>GPX 직접 업로드</label>
                            <label style={S.fileLabel}>
                                <input type="file" accept=".gpx" style={{ display: "none" }} onChange={e => { const x = e.target.files?.[0]; if (x) handleGpxFile(x); }} />
                                {track ? "✅ 로드됨" : "📎 GPX 선택"}
                            </label>
                        </div>
                        <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                            <label style={S.label}>제한 시간 (h)</label>
                            <input style={S.input} type="number" value={timeLimit} onChange={e => setTimeLimit(e.target.value)} />
                        </div>
                        <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                            <label style={S.label}>목표/시험 속도 (km/h)</label>
                            <input style={S.input} type="number" value={targetSpeed} onChange={e => setTargetSpeed(e.target.value)} />
                        </div>
                    </div>

                    {gpxError && <div style={{ color: "#f87171", fontSize: 12, marginBottom: 10 }}>{gpxError}</div>}
                    {track && <div style={{ color: "#22c55e", fontSize: 12, marginBottom: 14 }}>✓ {track.name} · {totalKm.toFixed(1)}km · ↑{Math.round(track.gain)}m · CP {track.keyPoints.length}개</div>}

                    <div style={{ display: "flex", gap: 10 }}>
                        <button style={{ ...S.startBtn, opacity: track ? 1 : 0.4 }} disabled={!track} onClick={() => startRide("fixture")}>🧪 테스트 시작 (fixture)</button>
                        <button style={{ ...S.startBtn, background: "linear-gradient(135deg,#16a34a,#22c55e)", opacity: track ? 1 : 0.4 }} disabled={!track} onClick={() => startRide("real")}>🟢 실주행 시작 (GPS)</button>
                    </div>
                </div>
            ) : (
                <div>
                    {/* 라이브 대시보드 */}
                    <div style={S.dash}>
                        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
                            <span style={{ fontSize: 13, color: "#94a3b8" }}>
                                {mode === "fixture" ? "🧪 테스트(fixture)" : "🟢 실주행"} · {track?.name}
                            </span>
                            {live.offRouteM > 100 && <span style={{ fontSize: 12, fontWeight: 700, color: "#f87171" }}>⚠️ 경로이탈 {Math.round(live.offRouteM)}m</span>}
                        </div>

                        <div style={{ height: 10, background: "#0f172a", borderRadius: 6, overflow: "hidden", marginBottom: 14 }}>
                            <div style={{ width: `${live.progressPct}%`, height: "100%", background: "linear-gradient(90deg,#3b82f6,#6366f1)", transition: "width .4s" }} />
                        </div>

                        <div style={S.statGrid}>
                            <Stat label="진행" val={`${live.coveredKm.toFixed(1)} / ${totalKm.toFixed(0)}km`} sub={`${live.progressPct.toFixed(0)}%`} />
                            <Stat label="경과" val={fmtMin(live.elapsedMin)} />
                            <Stat label="평균속도" val={`${live.avgSpeed.toFixed(1)}km/h`} />
                            <Stat label="완주 예상" val={live.etaFinish ?? "-"} />
                        </div>

                        {live.nextCp && (
                            <div style={{ marginTop: 12, background: live.nextCp.onTrack ? "#0f2a1a" : "#2a0f12", border: `1px solid ${live.nextCp.onTrack ? "#22c55e55" : "#ef444455"}`, borderRadius: 10, padding: "12px 14px" }}>
                                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                                    <span style={{ fontSize: 13, fontWeight: 700, color: "#f1f5f9" }}>다음 {live.nextCp.name}</span>
                                    <span style={{ fontSize: 12, fontWeight: 700, color: live.nextCp.onTrack ? "#22c55e" : "#f87171" }}>
                                        {live.nextCp.onTrack ? "✓ 페이스 OK" : "▲ 속도 올려야"}
                                    </span>
                                </div>
                                <div style={{ display: "flex", gap: 16, marginTop: 8, fontSize: 12, color: "#cbd5e1" }}>
                                    <span>남은거리 <b>{live.nextCp.distToCpKm.toFixed(1)}km</b></span>
                                    <span>마감 <b>{live.nextCp.deadline}</b> (여유 {fmtMin(live.nextCp.marginMin)})</span>
                                    <span>필요 페이스 <b style={{ color: live.nextCp.onTrack ? "#34d399" : "#f87171" }}>{live.nextCp.requiredSpeed.toFixed(1)}km/h</b></span>
                                </div>
                            </div>
                        )}
                        {live.error && <div style={{ color: "#f87171", fontSize: 12, marginTop: 8 }}>{live.error}</div>}
                    </div>

                    {/* fixture 컨트롤 */}
                    {mode === "fixture" && (
                        <div style={S.fixBar}>
                            <button style={S.fixBtn} onClick={() => stepFix("prev")}>◀ 이전</button>
                            <span style={{ fontSize: 13, color: "#e2e8f0", minWidth: 200, textAlign: "center" }}>
                                {fixIdx + 1}/{fixtures.length} · {fixtures[fixIdx]?.label} · {fixtures[fixIdx]?.timeMin}분
                            </span>
                            <button style={S.fixBtn} onClick={() => stepFix("next")}>다음 ▶</button>
                            <button style={S.fixBtn} onClick={() => ctrlRef.current?.play(1500)}>⏩ 재생</button>
                            <button style={S.fixBtn} onClick={() => ctrlRef.current?.pause()}>⏸ 정지</button>
                        </div>
                    )}

                    {/* 지도 */}
                    <div style={{ height: 460, marginTop: 16, borderRadius: 12, overflow: "hidden", border: "1px solid #334155" }}>
                        <LiveMap track={track} livePos={live.position ? { lat: live.position.lat, lon: live.position.lon } : null} />
                    </div>
                </div>
            )}
        </div>
    );
}

function Stat({ label, val, sub }: { label: string; val: string; sub?: string }) {
    return (
        <div style={{ background: "#0f172a", borderRadius: 10, padding: "12px 14px" }}>
            <div style={{ fontSize: 11, color: "#64748b" }}>{label}</div>
            <div style={{ fontSize: 18, fontWeight: 700, color: "#f1f5f9", marginTop: 2 }}>{val}</div>
            {sub && <div style={{ fontSize: 11, color: "#3b82f6", marginTop: 1 }}>{sub}</div>}
        </div>
    );
}

const S: Record<string, React.CSSProperties> = {
    page: { padding: "24px 24px 48px", maxWidth: 1100, margin: "0 auto", fontFamily: "'IBM Plex Mono','Pretendard',monospace", color: "#e2e8f0", background: "#0f172a", minHeight: "100vh" },
    header: { display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 24 },
    title: { margin: 0, fontSize: 22, fontWeight: 800, color: "#f1f5f9", fontFamily: "'Exo 2',sans-serif" },
    subtitle: { margin: "2px 0 0", fontSize: 12, color: "#64748b" },
    backBtn: { padding: "8px 16px", background: "#1e293b", border: "1px solid #334155", borderRadius: 8, color: "#94a3b8", cursor: "pointer", fontSize: 13 },
    formCard: { background: "#1e293b", borderRadius: 16, padding: 28, border: "1px solid #334155" },
    sectionTitle: { margin: "0 0 18px", fontSize: 16, fontWeight: 600, color: "#f1f5f9" },
    formGrid: { display: "grid", gridTemplateColumns: "1fr 1fr", gap: 18, marginBottom: 18 },
    label: { fontSize: 13, color: "#94a3b8", fontWeight: 500 },
    input: { padding: "10px 14px", background: "#0f172a", border: "1px solid #334155", borderRadius: 8, color: "#e2e8f0", fontSize: 14, outline: "none", fontFamily: "inherit" },
    fileLabel: { padding: "10px 14px", background: "#0f172a", border: "1px dashed #475569", borderRadius: 8, fontSize: 13, cursor: "pointer", textAlign: "center", color: "#94a3b8", display: "block" },
    startBtn: { flex: 1, padding: "13px", background: "linear-gradient(135deg,#3b82f6,#6366f1)", border: "none", borderRadius: 10, color: "#fff", fontSize: 14, fontWeight: 700, cursor: "pointer" },
    dash: { background: "#1e293b", border: "1px solid #334155", borderRadius: 14, padding: 18 },
    statGrid: { display: "grid", gridTemplateColumns: "repeat(4,1fr)", gap: 10 },
    fixBar: { display: "flex", alignItems: "center", gap: 8, marginTop: 12, background: "#1e293b", border: "1px solid #334155", borderRadius: 10, padding: "10px 12px", flexWrap: "wrap" },
    fixBtn: { padding: "6px 12px", background: "#334155", border: "1px solid #475569", borderRadius: 8, color: "#e2e8f0", fontSize: 12, cursor: "pointer" },
};
