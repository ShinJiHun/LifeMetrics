import { useState, useCallback, useRef, useEffect } from "react";
import mapboxgl from "mapbox-gl";
import "mapbox-gl/dist/mapbox-gl.css";

// ─── 타입 ───────────────────────────────────────────────
interface BrevetCourse {
  folderName: string;
  courseName: string;
  gpxFileName: string;
  distance: string;
  fileSizeKb: number;
}

interface CheckPoint {
  id: string;
  name: string;
  distance: number;
  elevation: number;
  targetArrival: string;
  deadline: string;
  lat: number;
  lon: number;
  weather?: WeatherData | null;
}

interface WeatherData {
  icon: string;
  temp: number;
  wind: number;
  windDir: number;
  precip: number;
  desc: string;
  hourly?: HourlyWeather[];
}

interface HourlyWeather {
  time: string;
  temp: number;
  wind: number;
  windDir: number;
  icon: string;
  precip: number;
}

interface GpxTrack {
  name: string;
  points: { lat: number; lon: number; ele: number }[];
  distances: number[];
  totalDist: number;
  gain: number;
  loss: number;
  minEle: number;
  maxEle: number;
  keyPoints: {
    lat: number;
    lon: number;
    ele: number;
    name: string;
    distFromStart: number;
  }[];
}

// ─── GPX Parser ─────────────────────────────────────────
function parseGPX(xml: string): GpxTrack | null {
  const doc = new DOMParser().parseFromString(xml, "text/xml");
  const trkpts = Array.from(doc.querySelectorAll("trkpt"));
  const wpts = Array.from(doc.querySelectorAll("wpt"));
  const name = doc.querySelector("name")?.textContent?.trim() || "GPX 루트";

  const points = trkpts
    .map((pt) => ({
      lat: parseFloat(pt.getAttribute("lat")!),
      lon: parseFloat(pt.getAttribute("lon")!),
      ele: parseFloat(pt.querySelector("ele")?.textContent || "0"),
    }))
    .filter((p) => !isNaN(p.lat));

  if (!points.length) return null;

  const R = 6371000;
  const distances = [0];
  for (let i = 1; i < points.length; i++) {
    const dLat = ((points[i].lat - points[i - 1].lat) * Math.PI) / 180;
    const dLon = ((points[i].lon - points[i - 1].lon) * Math.PI) / 180;
    const a =
      Math.sin(dLat / 2) ** 2 +
      Math.cos((points[i - 1].lat * Math.PI) / 180) *
        Math.cos((points[i].lat * Math.PI) / 180) *
        Math.sin(dLon / 2) ** 2;
    distances.push(distances[i - 1] + R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
  }
  const totalDist = distances[distances.length - 1];

  let gain = 0, loss = 0;
  for (let i = 1; i < points.length; i++) {
    const d = points[i].ele - points[i - 1].ele;
    if (d > 0) gain += d;
    else loss += Math.abs(d);
  }

  const eles = points.map((p) => p.ele);

  const waypoints = wpts
    .map((w) => ({
      lat: parseFloat(w.getAttribute("lat")!),
      lon: parseFloat(w.getAttribute("lon")!),
      ele: parseFloat(w.querySelector("ele")?.textContent || "0"),
      name: w.querySelector("name")?.textContent?.trim() || "WPT",
    }))
    .filter((w) => !isNaN(w.lat));

  let keyPoints: GpxTrack["keyPoints"] = [];
  if (waypoints.length > 0) {
    keyPoints = waypoints
      .map((w) => {
        let minD = Infinity, idx = 0;
        points.forEach((p, i) => {
          const d = Math.hypot(p.lat - w.lat, p.lon - w.lon);
          if (d < minD) { minD = d; idx = i; }
        });
        return { ...w, distFromStart: distances[idx] };
      })
      .sort((a, b) => a.distFromStart - b.distFromStart);
  } else {
    const segs = Math.min(4, Math.max(2, Math.floor(totalDist / 20000)));
    for (let s = 1; s < segs; s++) {
      const target = (totalDist / segs) * s;
      let minD = Infinity, idx = 0;
      distances.forEach((d, i) => {
        if (Math.abs(d - target) < minD) { minD = Math.abs(d - target); idx = i; }
      });
      keyPoints.push({
        lat: points[idx].lat,
        lon: points[idx].lon,
        ele: points[idx].ele,
        name: `CP${s}`,
        distFromStart: distances[idx],
      });
    }
  }

  const allKP = [
    { lat: points[0].lat, lon: points[0].lon, ele: points[0].ele, name: "START", distFromStart: 0 },
    ...keyPoints,
    {
      lat: points[points.length - 1].lat,
      lon: points[points.length - 1].lon,
      ele: points[points.length - 1].ele,
      name: "FINISH",
      distFromStart: totalDist,
    },
  ];

  return { name, points, distances, totalDist, gain, loss, minEle: Math.min(...eles), maxEle: Math.max(...eles), keyPoints: allKP };
}

// ─── 날씨 API ────────────────────────────────────────────
const WMO: Record<number, [string, string]> = {
  0: ["☀️", "맑음"], 1: ["🌤️", "대체로맑음"], 2: ["⛅", "구름조금"], 3: ["☁️", "흐림"],
  45: ["🌫️", "안개"], 48: ["🌫️", "안개"],
  51: ["🌦️", "이슬비"], 53: ["🌦️", "이슬비"], 55: ["🌦️", "이슬비"],
  61: ["🌧️", "비"], 63: ["🌧️", "비"], 65: ["🌧️", "강한비"],
  71: ["❄️", "눈"], 73: ["❄️", "눈"], 75: ["❄️", "폭설"],
  80: ["🌦️", "소나기"], 81: ["⛈️", "강한소나기"], 95: ["🌩️", "뇌우"],
};
function wmo(code: number) { return WMO[code] ?? ["❓", "불명"]; }
function windDirStr(deg: number) {
  const d = ["N", "NE", "E", "SE", "S", "SW", "W", "NW"];
  return d[Math.round(((deg + 22.5) % 360) / 45) % 8];
}

/** 두 좌표 간 진행 방향(bearing) 계산 (0°=북, 시계방향) */
function calcBearing(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const toRad = (d: number) => (d * Math.PI) / 180;
  const dLon = toRad(lon2 - lon1);
  const y = Math.sin(dLon) * Math.cos(toRad(lat2));
  const x =
    Math.cos(toRad(lat1)) * Math.sin(toRad(lat2)) -
    Math.sin(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.cos(dLon);
  return ((Math.atan2(y, x) * 180) / Math.PI + 360) % 360;
}

/** 진행방향과 풍향으로 역풍/순풍 분석 */
function windEffect(bearing: number, windDir: number, windSpeed: number): {
  label: string;
  emoji: string;
  color: string;
  desc: string;
} {
  if (windSpeed < 5) return { label: "무풍", emoji: "😌", color: "#64748b", desc: "바람 영향 거의 없음" };
  // 풍향은 바람이 불어오는 방향 → 진행방향과 반대면 역풍
  const diff = ((windDir - bearing + 540) % 360) - 180; // -180 ~ +180
  const absDiff = Math.abs(diff);

  if (absDiff <= 45)   return { label: "역풍", emoji: "💨", color: "#f87171", desc: `정면에서 ${windSpeed}km/h 맞바람 — 페이스 유지 주의` };
  if (absDiff <= 90)   return { label: "측역풍", emoji: "↙️", color: "#fb923c", desc: `측면 역풍 ${windSpeed}km/h — 다소 힘든 구간` };
  if (absDiff <= 135)  return { label: "측순풍", emoji: "↗️", color: "#a3e635", desc: `측면 순풍 ${windSpeed}km/h — 크게 영향 없음` };
  return               { label: "순풍", emoji: "🚀", color: "#34d399", desc: `등 뒤 ${windSpeed}km/h 순풍 — 속도 이득 예상` };
}

async function fetchWeatherForPoint(lat: number, lon: number, date: string): Promise<WeatherData | null> {
  try {
    const today = new Date();
    const target = new Date(date);
    const diffDays = Math.ceil((target.getTime() - today.getTime()) / (1000 * 60 * 60 * 24));

    let url: string;
    if (diffDays > 16) {
      const maxDate = new Date(today);
      maxDate.setDate(today.getDate() + 15);
      const maxDateStr = maxDate.toISOString().slice(0, 10);
      url = `https://api.open-meteo.com/v1/forecast?latitude=${lat.toFixed(4)}&longitude=${lon.toFixed(4)}&hourly=temperature_2m,precipitation_probability,windspeed_10m,winddirection_10m,weathercode&start_date=${maxDateStr}&end_date=${maxDateStr}&timezone=Asia%2FSeoul`;
    } else if (diffDays < -1) {
      url = `https://archive-api.open-meteo.com/v1/archive?latitude=${lat.toFixed(4)}&longitude=${lon.toFixed(4)}&hourly=temperature_2m,precipitation_probability,windspeed_10m,winddirection_10m,weathercode&start_date=${date}&end_date=${date}&timezone=Asia%2FSeoul`;
    } else {
      url = `https://api.open-meteo.com/v1/forecast?latitude=${lat.toFixed(4)}&longitude=${lon.toFixed(4)}&hourly=temperature_2m,precipitation_probability,windspeed_10m,winddirection_10m,weathercode&start_date=${date}&end_date=${date}&timezone=Asia%2FSeoul`;
    }

    const res = await fetch(url);
    if (!res.ok) return null;
    const json = await res.json();
    const h = json.hourly;
    if (!h) return null;
    const idx = 9;
    const [icon, desc] = wmo(h.weathercode[idx]);
    const hourly: HourlyWeather[] = [6, 9, 12, 15, 18, 21].map((hr) => {
      const [ic] = wmo(h.weathercode[hr]);
      return {
        time: `${String(hr).padStart(2, "0")}:00`,
        temp: h.temperature_2m[hr],
        wind: h.windspeed_10m[hr],
        windDir: h.winddirection_10m[hr],
        icon: ic,
        precip: h.precipitation_probability?.[hr] ?? 0,
      };
    });
    return {
      icon, desc,
      temp: h.temperature_2m[idx],
      wind: h.windspeed_10m[idx],
      windDir: h.winddirection_10m[idx],
      precip: h.precipitation_probability?.[idx] ?? 0,
      hourly,
    };
  } catch { return null; }
}

// ─── 유틸 ────────────────────────────────────────────────
function calcArrival(startTime: string, distKm: number, speedKmh: number): string {
  const [sh, sm] = startTime.split(":").map(Number);
  const mins = sh * 60 + sm + Math.round((distKm / speedKmh) * 60);
  return `${String(Math.floor(mins / 60) % 24).padStart(2, "0")}:${String(mins % 60).padStart(2, "0")}`;
}
function calcDeadline(startTime: string, distKm: number, totalDist: number, timeLimitH: number): string {
  const [sh, sm] = startTime.split(":").map(Number);
  const mins = sh * 60 + sm + Math.round((distKm / totalDist) * timeLimitH * 60);
  return `${String(Math.floor(mins / 60) % 24).padStart(2, "0")}:${String(mins % 60).padStart(2, "0")}`;
}
function marginColor(deadline: string, arrival: string) {
  if (deadline === "-") return "#22c55e";
  const [dh, dm] = deadline.split(":").map(Number);
  const [ah, am] = arrival.split(":").map(Number);
  const m = dh * 60 + dm - (ah * 60 + am);
  return m >= 180 ? "#22c55e" : m >= 60 ? "#f59e0b" : "#ef4444";
}
function marginText(deadline: string, arrival: string) {
  if (deadline === "-") return "-";
  const [dh, dm] = deadline.split(":").map(Number);
  const [ah, am] = arrival.split(":").map(Number);
  const m = dh * 60 + dm - (ah * 60 + am);
  return `여유 ${Math.floor(m / 60)}h ${m % 60}m`;
}

interface HoveredPoint { lat: number; lon: number; ele: number; dist: number; }

// ─── Mapbox 3D 지형 지도 ─────────────────────────────────
function MapboxTerrain({
  track,
  mode,
  hoveredPoint,
}: {
  track: GpxTrack | null;
  mode: "2d" | "3d";
  hoveredPoint: HoveredPoint | null;
}) {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<mapboxgl.Map | null>(null);
  const markersRef = useRef<mapboxgl.Marker[]>([]);
  const cursorMarkerRef = useRef<mapboxgl.Marker | null>(null);
  const chartMarkerRef = useRef<mapboxgl.Marker | null>(null);
  const tooltipRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const el = chartMarkerRef.current?.getElement() as HTMLElement | undefined;
    if (!el) return;
    if (hoveredPoint) {
      chartMarkerRef.current!.setLngLat([hoveredPoint.lon, hoveredPoint.lat]);
      el.style.display = "block";
    } else {
      el.style.display = "none";
    }
  }, [hoveredPoint]);

  useEffect(() => {
    if (!containerRef.current) return;
    const token = import.meta.env.VITE_MAPBOX_TOKEN as string;
    mapboxgl.accessToken = token;

    const map = new mapboxgl.Map({
      container: containerRef.current,
      style: "mapbox://styles/mapbox/outdoors-v12",
      center: track ? [track.keyPoints[0].lon, track.keyPoints[0].lat] : [127.5, 36.5],
      zoom: track ? 10 : 7,
      pitch: mode === "3d" ? 62 : 0,
      bearing: mode === "3d" ? -20 : 0,
      antialias: true,
    });
    mapRef.current = map;

    map.addControl(new mapboxgl.NavigationControl(), "top-right");
    map.addControl(new mapboxgl.ScaleControl({ unit: "metric" }), "bottom-left");

    const cursorEl = document.createElement("div");
    cursorEl.style.cssText = `
      width: 14px; height: 14px; border-radius: 50%;
      background: rgba(250, 204, 21, 0.95);
      border: 2px solid #fff;
      box-shadow: 0 0 0 3px rgba(250,204,21,0.3), 0 2px 8px rgba(0,0,0,0.5);
      pointer-events: none;
    `;
    const cursorMarker = new mapboxgl.Marker({ element: cursorEl, anchor: "center" })
      .setLngLat([127.5, 36.5]);
    cursorMarkerRef.current = cursorMarker;

    const chartEl = document.createElement("div");
    chartEl.style.cssText = `
      width: 16px; height: 16px; border-radius: 50%;
      background: rgba(96, 165, 250, 0.95);
      border: 2px solid #fff;
      box-shadow: 0 0 0 4px rgba(96,165,250,0.3), 0 2px 10px rgba(0,0,0,0.6);
      pointer-events: none; display: none;
    `;
    const chartMarker = new mapboxgl.Marker({ element: chartEl, anchor: "center" })
      .setLngLat([127.5, 36.5]);
    chartMarkerRef.current = chartMarker;

    const tooltip = document.createElement("div");
    tooltip.style.cssText = `
      position: absolute; display: none; z-index: 10;
      background: rgba(15,23,42,0.92); color: #e2e8f0;
      font-family: 'IBM Plex Mono', monospace; font-size: 11px;
      padding: 5px 10px; border-radius: 6px;
      border: 1px solid #334155; pointer-events: none;
      white-space: nowrap; backdrop-filter: blur(4px);
      box-shadow: 0 4px 12px rgba(0,0,0,0.4);
    `;
    containerRef.current.style.position = "relative";
    containerRef.current.appendChild(tooltip);
    tooltipRef.current = tooltip;

    map.on("load", () => {
      map.addSource("mapbox-dem", {
        type: "raster-dem",
        url: "mapbox://mapbox.mapbox-terrain-dem-v1",
        tileSize: 512,
        maxzoom: 14,
      });

      if (mode === "3d") {
        map.setTerrain({ source: "mapbox-dem", exaggeration: 1.8 });
        map.setFog({
          color: "rgb(186, 210, 235)",
          "high-color": "rgb(36, 92, 223)",
          "horizon-blend": 0.02,
          "space-color": "rgb(11, 11, 25)",
          "star-intensity": 0.6,
        });
      }

      cursorMarker.addTo(map);
      chartMarker.addTo(map);

      if (!track) return;

      const coords = track.points.map((p) => [p.lon, p.lat]);
      map.addSource("route", {
        type: "geojson",
        data: {
          type: "Feature",
          properties: {},
          geometry: { type: "LineString", coordinates: coords },
        },
      });

      map.addLayer({
        id: "route-outline",
        type: "line",
        source: "route",
        layout: { "line-join": "round", "line-cap": "round" },
        paint: { "line-color": "#000", "line-width": 7, "line-opacity": 0.3 },
      });

      map.addLayer({
        id: "route-line",
        type: "line",
        source: "route",
        layout: { "line-join": "round", "line-cap": "round" },
        paint: { "line-color": "#3b82f6", "line-width": 4, "line-opacity": 0.95 },
      });

      const lons = track.points.map((p) => p.lon);
      const lats = track.points.map((p) => p.lat);
      map.fitBounds(
        [[Math.min(...lons), Math.min(...lats)], [Math.max(...lons), Math.max(...lats)]],
        { padding: 60, pitch: mode === "3d" ? 62 : 0, duration: 1500 }
      );

      markersRef.current.forEach((m) => m.remove());
      markersRef.current = [];
      track.keyPoints.forEach((kp, i) => {
        const color = i === 0 ? "#22c55e" : i === track.keyPoints.length - 1 ? "#ef4444" : "#f59e0b";
        const el = document.createElement("div");
        el.style.cssText = `
          background:${color}; color:#fff; font-size:10px; font-weight:700;
          padding:3px 8px; border-radius:5px; white-space:nowrap;
          box-shadow:0 2px 8px rgba(0,0,0,0.5);
          border:1.5px solid rgba(255,255,255,0.4); cursor:pointer;
        `;
        el.textContent = kp.name;
        const marker = new mapboxgl.Marker({ element: el, anchor: "bottom" })
          .setLngLat([kp.lon, kp.lat])
          .setPopup(
            new mapboxgl.Popup({ offset: 25 }).setHTML(`
              <div style="font-family:monospace;font-size:12px;color:#1e293b;padding:4px">
                <b>${kp.name}</b><br/>
                거리: ${(kp.distFromStart / 1000).toFixed(1)}km<br/>
                고도: ${Math.round(kp.ele)}m
              </div>
            `)
          )
          .addTo(map);
        markersRef.current.push(marker);
      });
    });

    map.on("mousemove", (e) => {
      const { lng, lat } = e.lngLat;
      cursorMarker.setLngLat([lng, lat]);

      if (!tooltipRef.current || !containerRef.current) return;

      const elevation = typeof (map as any).queryTerrainElevation === "function"
        ? (map as any).queryTerrainElevation([lng, lat], { exaggerated: false })
        : null;

      let routeInfo = "";
      if (track) {
        let minD = Infinity, closestDist = 0, closestEle = 0;
        track.points.forEach((p, i) => {
          const d = Math.hypot(p.lat - lat, p.lon - lng);
          if (d < minD) { minD = d; closestDist = track.distances[i]; closestEle = p.ele; }
        });
        if (minD < 0.04) {
          routeInfo = `  📍${(closestDist / 1000).toFixed(1)}km  ${Math.round(closestEle)}m`;
        }
      }

      const elevStr = elevation != null ? `  ⛰${Math.round(elevation)}m` : "";
      tooltipRef.current.textContent = `${lat.toFixed(5)}, ${lng.toFixed(5)}${elevStr}${routeInfo}`;
      tooltipRef.current.style.display = "block";

      const rect = containerRef.current.getBoundingClientRect();
      const mx = e.originalEvent.clientX - rect.left;
      const my = e.originalEvent.clientY - rect.top;
      const ttW = 300;
      tooltipRef.current.style.left = `${mx + 16 + ttW > rect.width ? mx - ttW - 8 : mx + 16}px`;
      tooltipRef.current.style.top = `${my - 14}px`;
    });

    map.on("mouseleave", () => {
      if (tooltipRef.current) tooltipRef.current.style.display = "none";
    });

    return () => {
      markersRef.current.forEach((m) => m.remove());
      cursorMarkerRef.current?.remove();
      chartMarkerRef.current?.remove();
      tooltipRef.current?.remove();
      map.remove();
      mapRef.current = null;
    };
  }, [track, mode]);

  return <div ref={containerRef} style={{ width: "100%", height: "100%" }} />;
}

// ─── 고도 차트 ───────────────────────────────────────────
function ElevChart({
  track,
  onHover,
  hoveredPoint,
}: {
  track: GpxTrack;
  onHover: (pt: HoveredPoint | null) => void;
  hoveredPoint: HoveredPoint | null;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const svgRef = useRef<SVGSVGElement>(null);
  const [w, setW] = useState(600);
  const H = 130, pad = { t: 8, r: 14, b: 28, l: 44 };
  const W = w - pad.l - pad.r, CH = H - pad.t - pad.b;

  useEffect(() => {
    const obs = new ResizeObserver((e) => setW(e[0].contentRect.width));
    if (ref.current) obs.observe(ref.current);
    return () => obs.disconnect();
  }, []);

  const { points, distances, totalDist, minEle, maxEle, keyPoints } = track;
  const er = (maxEle - minEle) || 1;
  const xs = (d: number) => (d / totalDist) * W;
  const ys = (e: number) => CH - ((e - minEle) / er) * CH;
  const pathD = points
    .map((p, i) => `${i === 0 ? "M" : "L"}${xs(distances[i]).toFixed(1)},${ys(p.ele).toFixed(1)}`)
    .join(" ");

  const handleMouseMove = (e: React.MouseEvent<SVGSVGElement>) => {
    if (!svgRef.current) return;
    const rect = svgRef.current.getBoundingClientRect();
    const rawX = ((e.clientX - rect.left) / rect.width) * w - pad.l;
    const ratio = Math.max(0, Math.min(1, rawX / W));
    const targetDist = ratio * totalDist;
    let minD = Infinity, idx = 0;
    distances.forEach((d, i) => {
      if (Math.abs(d - targetDist) < minD) { minD = Math.abs(d - targetDist); idx = i; }
    });
    onHover({ lat: points[idx].lat, lon: points[idx].lon, ele: points[idx].ele, dist: distances[idx] });
  };

  return (
    <div ref={ref} style={{ width: "100%", height: H }}>
      <svg
        ref={svgRef}
        width="100%" height={H}
        viewBox={`0 0 ${w} ${H}`}
        preserveAspectRatio="none"
        style={{ cursor: "crosshair" }}
        onMouseMove={handleMouseMove}
        onMouseLeave={() => onHover(null)}
      >
        <defs>
          <linearGradient id="ecg" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#3b82f6" stopOpacity="0.4" />
            <stop offset="100%" stopColor="#1e3a5f" stopOpacity="0.05" />
          </linearGradient>
        </defs>
        <g transform={`translate(${pad.l},${pad.t})`}>
          {[0, 0.5, 1].map((t) => {
            const e = minEle + er * t;
            return (
              <g key={t}>
                <line x1={0} y1={ys(e)} x2={W} y2={ys(e)} stroke="#1e3a5f" strokeWidth="1" />
                <text x={-6} y={ys(e) + 4} fill="#475569" fontSize="10" textAnchor="end">
                  {Math.round(e)}m
                </text>
              </g>
            );
          })}
          <path d={`${pathD} L${W},${CH} L0,${CH} Z`} fill="url(#ecg)" />
          <path d={pathD} fill="none" stroke="#3b82f6" strokeWidth="2" strokeLinejoin="round" />
          {keyPoints.map((kp, i) => {
            const x = xs(kp.distFromStart), y = ys(kp.ele);
            const c = i === 0 ? "#22c55e" : i === keyPoints.length - 1 ? "#ef4444" : "#f59e0b";
            return (
              <g key={i}>
                <line x1={x} y1={0} x2={x} y2={CH} stroke={c} strokeWidth="1" strokeDasharray="3,3" opacity="0.6" />
                <circle cx={x} cy={y} r={4} fill={c} stroke="#1e293b" strokeWidth="1.5" />
                <text x={x} y={CH + 18} fill={c} fontSize="9" textAnchor="middle">{kp.name}</text>
              </g>
            );
          })}
          {hoveredPoint && (() => {
            const x = xs(hoveredPoint.dist);
            const y = ys(hoveredPoint.ele);
            const labelX = x + 6 + 90 > W ? x - 96 : x + 6;
            return (
              <g>
                <line x1={x} y1={0} x2={x} y2={CH} stroke="#facc15" strokeWidth="1" strokeDasharray="4,3" opacity="0.85" />
                <circle cx={x} cy={y} r={5} fill="#facc15" stroke="#fff" strokeWidth="2"
                  style={{ filter: "drop-shadow(0 0 5px rgba(250,204,21,0.8))" }} />
                <rect x={labelX} y={y - 24} width={90} height={20} rx={4}
                  fill="rgba(15,23,42,0.9)" stroke="#facc15" strokeWidth="1" strokeOpacity="0.6" />
                <text x={labelX + 45} y={y - 10} fill="#facc15" fontSize="10" textAnchor="middle" fontFamily="monospace">
                  {(hoveredPoint.dist / 1000).toFixed(1)}km · {Math.round(hoveredPoint.ele)}m
                </text>
              </g>
            );
          })()}
        </g>
      </svg>
    </div>
  );
}

// ─── 날씨 패널 ───────────────────────────────────────────
function WeatherPanel({ cp, prev }: { cp: CheckPoint; prev?: CheckPoint }) {
  const w = cp.weather;
  const segDist = prev ? cp.distance - prev.distance : 0;

  // 구간 진행 방향 + 풍향 분석
  const windAnalysis = (() => {
    if (!w || !prev) return null;
    const bearing = calcBearing(prev.lat, prev.lon, cp.lat, cp.lon);
    return windEffect(bearing, w.windDir, w.wind);
  })();

  return (
    <div style={{ background: "linear-gradient(135deg,#0f1f3d,#0a1628)", border: "1px solid #1e3a5f", borderRadius: 10, padding: "14px 16px", marginBottom: 10 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          {prev && (
            <>
              <span style={{ background: "#1e3a5f", color: "#93c5fd", fontSize: 10, fontWeight: 700, padding: "2px 7px", borderRadius: 4 }}>
                {prev.name.split(" ")[0]}
              </span>
              <span style={{ color: "#334155", fontSize: 11 }}>→</span>
            </>
          )}
          <span style={{ background: "#1d4ed8", color: "#fff", fontSize: 10, fontWeight: 700, padding: "2px 7px", borderRadius: 4 }}>
            {cp.name.split(" ")[0]}
          </span>
        </div>
        {segDist > 0 && <span style={{ fontSize: 11, color: "#475569" }}>{segDist}km</span>}
      </div>
      {!w ? (
        <div style={{ color: "#334155", fontSize: 12, padding: "6px 0" }}>날씨 로딩 중...</div>
      ) : (
        <>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "6px 14px", marginBottom: 8 }}>
            <WCell icon={w.icon} label="날씨" val={w.desc} />
            <WCell icon="🌡️" label="기온" val={`${w.temp}°C`} />
            <WCell icon="💧" label="강수확률" val={`${w.precip}%`} accent={w.precip > 50 ? "#f87171" : undefined} />
            <WCell icon="💨" label="풍속" val={`${w.wind}km/h`} accent={w.wind > 30 ? "#fbbf24" : undefined} />
            <WCell icon="🧭" label="풍향" val={windDirStr(w.windDir)} full />
          </div>
          {windAnalysis && (
            <div style={{
              display: "flex", alignItems: "center", gap: 8,
              background: `${windAnalysis.color}18`,
              border: `1px solid ${windAnalysis.color}44`,
              borderRadius: 8, padding: "8px 12px", marginBottom: 8,
            }}>
              <span style={{ fontSize: 18 }}>{windAnalysis.emoji}</span>
              <div>
                <span style={{ fontSize: 12, fontWeight: 700, color: windAnalysis.color }}>
                  {windAnalysis.label}
                </span>
                <span style={{ fontSize: 11, color: "#94a3b8", marginLeft: 8 }}>
                  {windAnalysis.desc}
                </span>
              </div>
            </div>
          )}
          {w.hourly && (
            <div style={{ borderTop: "1px solid #1e3a5f", paddingTop: 8, marginTop: 4 }}>
              <div style={{ fontSize: 10, color: "#334155", marginBottom: 6 }}>시간대별 예보</div>
              <div style={{ display: "flex", gap: 4 }}>
                {w.hourly.map((h) => (
                  <div key={h.time} style={{ flex: 1, background: "#060d1a", borderRadius: 6, padding: "6px 4px", textAlign: "center" }}>
                    <div style={{ fontSize: 10, color: "#475569" }}>{h.time}</div>
                    <div style={{ fontSize: 16, lineHeight: 1.4 }}>{h.icon}</div>
                    <div style={{ fontSize: 11, fontWeight: 600, color: "#93c5fd" }}>{h.temp}°</div>
                    <div style={{ fontSize: 10, color: h.precip > 50 ? "#f87171" : "#475569" }}>{h.precip}%</div>
                    <div style={{ fontSize: 11, color: h.wind > 30 ? "#fbbf24" : "#64748b" }}>{h.wind}㎞</div>
                    <div style={{ fontSize: 14, transform: `rotate(${h.windDir}deg)`, display: "inline-block", lineHeight: 1 }}>↑</div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}

function WCell({ icon, label, val, accent, full }: { icon: string; label: string; val: string; accent?: string; full?: boolean }) {
  return (
    <div style={{ gridColumn: full ? "1/-1" : undefined }}>
      <div style={{ fontSize: 10, color: "#475569" }}>{icon} {label}</div>
      <div style={{ fontSize: 13, fontWeight: 600, color: accent || "#93c5fd", marginTop: 1 }}>{val}</div>
    </div>
  );
}

function FormField({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
      <label style={{ fontSize: 13, color: "#94a3b8", fontWeight: 500 }}>{label}</label>
      {children}
    </div>
  );
}

// ─── 메인 ────────────────────────────────────────────────
export default function BrevePlanPage() {
  const [step, setStep] = useState<"form" | "result">("form");
  const [expandedCp, setExpandedCp] = useState<string | null>(null);
  const [mapMode, setMapMode] = useState<"2d" | "3d">("2d");
  const [track, setTrack] = useState<GpxTrack | null>(null);
  const [cps, setCps] = useState<CheckPoint[]>([]);
  const [gpxError, setGpxError] = useState("");
  const [hoveredPoint, setHoveredPoint] = useState<HoveredPoint | null>(null);

  const [eventName, setEventName] = useState("BRM 200 수원-천안");
  const [eventDate, setEventDate] = useState("2026-05-10");
  const [startTime, setStartTime] = useState("06:00");
  const [timeLimit, setTimeLimit] = useState("13.5");
  const [targetSpeed, setTargetSpeed] = useState("15");
  const [gpxFile, setGpxFile] = useState<File | null>(null);

  // ── NAS 코스 선택 state ──────────────────────────────
  const [courses, setCourses] = useState<BrevetCourse[]>([]);
  const [selectedCourse, setSelectedCourse] = useState<string>("");
  const [courseLoading, setCourseLoading] = useState(false);

  // Claude 분석
  const [brevetAnalysis, setBrevetAnalysis] = useState<{
    summary: string;
    overallCondition: string;
    paceStrategy: string;
    warnings: string[];
    tips: string[];
    conclusion: string;
    score: number;
  } | null>(null);
  const [analysisLoading, setAnalysisLoading] = useState(false);

  // 코스 목록 최초 로드
  useEffect(() => {
    fetch("/api/brevet/courses")
      .then((r) => r.json())
      .then((data: BrevetCourse[]) => setCourses(data))
      .catch(() => console.warn("브레베 코스 목록 로드 실패"));
  }, []);

  // 코스 선택 시 GPX 자동 로드
  const handleCourseSelect = async (value: string) => {
    setSelectedCourse(value);
    if (!value) return;

    const [folderName, gpxFileName] = value.split("::");

    // 대회명 자동 채우기 - 선택된 코스의 courseName 사용
    const selected = courses.find(
      (c) => c.folderName === folderName && c.gpxFileName === gpxFileName
    );
    if (selected) setEventName(selected.courseName);
    setCourseLoading(true);
    setGpxError("");
    setGpxFile(null);

    try {
      const res = await fetch(
        `/api/brevet/courses/${encodeURIComponent(folderName)}/gpx?file=${encodeURIComponent(gpxFileName)}`
      );
      if (!res.ok) throw new Error("GPX 로드 실패");
      const xml = await res.text();
      const parsed = parseGPX(xml);
      if (!parsed) {
        setGpxError("GPX 파싱 실패");
        setTrack(null);
      } else {
        setTrack(parsed);
      }
    } catch {
      setGpxError("GPX 로드 실패");
      setTrack(null);
    } finally {
      setCourseLoading(false);
    }
  };

  const handleGpxFile = useCallback((file: File) => {
    setGpxFile(file);
    setSelectedCourse(""); // 파일 업로드 시 코스 선택 초기화
    setGpxError("");
    const reader = new FileReader();
    reader.onload = (e) => {
      const parsed = parseGPX(e.target!.result as string);
      if (!parsed) { setGpxError("GPX 파싱 실패"); setTrack(null); }
      else setTrack(parsed);
    };
    reader.readAsText(file);
  }, []);

  const handleBrevetAnalyze = async () => {
    if (cps.length === 0) return;
    setAnalysisLoading(true);
    setBrevetAnalysis(null);
    try {
      const payload = {
        userId: 1,
        eventName,
        eventDate,
        startTime,
        timeLimit: Number(timeLimit),
        targetSpeed: Number(targetSpeed),
        totalDistance: totalDistKm,
        totalAscent: totalElev,
        cps: cps.map((cp, i) => ({
          name: cp.name,
          distance: cp.distance,
          elevation: cp.elevation,
          targetArrival: cp.targetArrival,
          deadline: cp.deadline,
          lat: cp.lat,
          lon: cp.lon,
          temp: cp.weather?.temp ?? 0,
          wind: cp.weather?.wind ?? 0,
          windDir: cp.weather?.windDir ?? 0,
          precip: cp.weather?.precip ?? 0,
          weatherDesc: cp.weather?.desc ?? "",
          windEffect: i > 0 && cp.weather
            ? windEffect(calcBearing(cps[i-1].lat, cps[i-1].lon, cp.lat, cp.lon), cp.weather.windDir, cp.weather.wind).label
            : "정보없음",
        })),
      };
      const res = await fetch("/api/ai/analysis/brevet", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });
      if (res.ok) {
        const data = await res.json();
        setBrevetAnalysis(data);
      }
    } catch (e) {
      console.error("브레베 분석 실패", e);
    } finally {
      setAnalysisLoading(false);
    }
  };

  const handleAnalyze = async () => {
    const speed = Number(targetSpeed);
    const limit = Number(timeLimit);
    let baseCps: CheckPoint[];

    if (track) {
      const totalDistKm = track.totalDist / 1000;
      baseCps = track.keyPoints.map((kp, i) => {
        const distKm = Math.round(kp.distFromStart / 1000);
        const arrival = i === 0 ? startTime : calcArrival(startTime, distKm, speed);
        const deadline =
          i === 0 ? "-"
          : i === track.keyPoints.length - 1
            ? calcDeadline(startTime, totalDistKm, totalDistKm, limit)
            : calcDeadline(startTime, distKm, totalDistKm, limit);
        return {
          id: `kp-${i}`,
          name: kp.name,
          distance: distKm,
          elevation: Math.round(kp.ele),
          targetArrival: arrival,
          deadline,
          lat: kp.lat,
          lon: kp.lon,
          weather: undefined,
        };
      });
    } else {
      baseCps = [
        { id: "start", name: "출발 (수원)", distance: 0, elevation: 0, targetArrival: "06:00", deadline: "-", lat: 37.27, lon: 127.01, weather: undefined },
        { id: "cp1", name: "CP1 (천안)", distance: 88, elevation: 620, targetArrival: "12:00", deadline: "14:00", lat: 36.81, lon: 127.11, weather: undefined },
        { id: "finish", name: "완주 (수원)", distance: 200, elevation: 1240, targetArrival: "19:30", deadline: "22:00", lat: 37.27, lon: 127.01, weather: undefined },
      ];
    }

    setCps(baseCps);
    setStep("result");

    const weatherResults = await Promise.all(
      baseCps.map((cp) => fetchWeatherForPoint(cp.lat, cp.lon, eventDate))
    );
    setCps((prev) => prev.map((cp, i) => ({ ...cp, weather: weatherResults[i] })));
  };

  const totalDistKm = track ? Math.round(track.totalDist / 1000) : cps.length ? cps[cps.length - 1].distance : 0;
  const totalElev = track ? Math.round(track.gain) : 0;

  return (
    <div style={S.page}>
      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:wght@400;500;600&family=Exo+2:wght@700;800&display=swap');
        * { box-sizing: border-box; }
        input[type=date], input[type=time] { color-scheme: dark; }
        .mapboxgl-popup-content { border-radius: 8px !important; padding: 8px 12px !important; }
        ::-webkit-scrollbar { width: 5px; }
        ::-webkit-scrollbar-thumb { background: #1e3a5f; border-radius: 3px; }
        select option { background: #0f172a; color: #e2e8f0; }
        select optgroup { background: #0f172a; color: #64748b; }
      `}</style>

      {/* 헤더 */}
      <div style={S.header}>
        <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
          <span style={{ fontSize: 32 }}>🏅</span>
          <div>
            <h1 style={S.title}>랜도너스 계획</h1>
            <p style={S.subtitle}>Brevet Route Planner</p>
          </div>
        </div>
        {step === "result" && (
          <button style={S.backBtn} onClick={() => setStep("form")}>← 다시 입력</button>
        )}
      </div>

      {step === "form" ? (
        <div style={S.formCard}>
          <h2 style={S.sectionTitle}>대회 정보 입력</h2>
          <div style={S.formGrid}>
            <FormField label="대회명">
              <input style={S.input} value={eventName} onChange={(e) => setEventName(e.target.value)} />
            </FormField>
            <FormField label="날짜">
              <input style={S.input} type="date" value={eventDate} onChange={(e) => setEventDate(e.target.value)} />
            </FormField>
            <FormField label="출발 시간">
              <input style={S.input} type="time" value={startTime} onChange={(e) => setStartTime(e.target.value)} />
            </FormField>
            <FormField label="제한 시간 (시간)">
              <input style={S.input} type="number" value={timeLimit} onChange={(e) => setTimeLimit(e.target.value)} />
            </FormField>
            <FormField label="목표 평균 속도 (km/h)">
              <input style={S.input} type="number" value={targetSpeed} onChange={(e) => setTargetSpeed(e.target.value)} />
            </FormField>

            {/* ── NAS 코스 선택 (새로 추가) ── */}
            <FormField label="저장된 코스 선택">
              <select
                style={{ ...S.input, cursor: "pointer" }}
                value={selectedCourse}
                onChange={(e) => handleCourseSelect(e.target.value)}
              >
                <option value="">-- NAS 코스 선택 --</option>
                {["200K", "300K", "400K", "600K", "1000K", "1200K", "-"].map((dist) => {
                  const filtered = courses.filter((c) => c.distance === dist);
                  if (!filtered.length) return null;
                  return (
                    <optgroup key={dist} label={`── ${dist} ──`}>
                      {filtered.map((c) => (
                        <option
                          key={`${c.folderName}::${c.gpxFileName}`}
                          value={`${c.folderName}::${c.gpxFileName}`}
                        >
                          {c.courseName} ({c.fileSizeKb}KB)
                        </option>
                      ))}
                    </optgroup>
                  );
                })}
              </select>
              {courseLoading && (
                <span style={{ fontSize: 11, color: "#93c5fd", marginTop: 4 }}>⏳ GPX 로딩 중...</span>
              )}
              {selectedCourse && track && !courseLoading && (
                <span style={{ fontSize: 11, color: "#22c55e", marginTop: 4 }}>
                  ✓ {(track.totalDist / 1000).toFixed(1)}km · ↑{Math.round(track.gain)}m
                </span>
              )}
            </FormField>

            {/* ── 구분선 ── */}
            <div style={{ gridColumn: "1/-1", display: "flex", alignItems: "center", gap: 12, color: "#334155", fontSize: 12 }}>
              <div style={{ flex: 1, height: 1, background: "#1e3a5f" }} />
              또는 직접 업로드
              <div style={{ flex: 1, height: 1, background: "#1e3a5f" }} />
            </div>

            {/* ── GPX 파일 업로드 ── */}
            <FormField label="GPX 파일">
              <label
                style={{ ...S.fileLabel, borderColor: track && gpxFile ? "#22c55e" : "#475569", color: track && gpxFile ? "#22c55e" : "#94a3b8" }}
                onDragOver={(e) => e.preventDefault()}
                onDrop={(e) => { e.preventDefault(); const f = e.dataTransfer.files[0]; if (f) handleGpxFile(f); }}
              >
                <input type="file" accept=".gpx" style={{ display: "none" }} onChange={(e) => { const f = e.target.files?.[0]; if (f) handleGpxFile(f); }} />
                {track && gpxFile ? `✅ ${gpxFile?.name}` : "📎 GPX 파일 선택 또는 드래그"}
              </label>
              {gpxError && <span style={{ fontSize: 11, color: "#f87171", marginTop: 4 }}>{gpxError}</span>}
              {track && gpxFile && (
                <span style={{ fontSize: 11, color: "#22c55e", marginTop: 4 }}>
                  ✓ {(track.totalDist / 1000).toFixed(1)}km · ↑{Math.round(track.gain)}m · {track.keyPoints.length}개 포인트
                </span>
              )}
            </FormField>
          </div>
          <button style={S.analyzeBtn} onClick={handleAnalyze}>🗺️ 구간 분석 시작</button>
        </div>
      ) : (
        <div>
          {/* 요약 */}
          <div style={S.summaryRow}>
            {[
              { icon: "🚴", val: `${totalDistKm} km`, label: "총 거리" },
              { icon: "⛰️", val: `${totalElev.toLocaleString()} m`, label: "총 획득고도" },
              { icon: "⏱️", val: `${timeLimit}시간`, label: "제한 시간" },
              { icon: "📅", val: eventDate, label: `${startTime} 출발` },
            ].map((c) => (
              <div key={c.label} style={S.summaryCard}>
                <span style={{ fontSize: 26 }}>{c.icon}</span>
                <div>
                  <div style={S.summaryValue}>{c.val}</div>
                  <div style={S.summaryLabel}>{c.label}</div>
                </div>
              </div>
            ))}
          </div>

          {/* 지도 영역 */}
          <div style={{ background: "#1e293b", border: "1px solid #334155", borderRadius: 12, marginBottom: 20, overflow: "hidden" }}>
            <div style={{ display: "flex", borderBottom: "1px solid #334155" }}>
              {([["2d", "🗺️ 2D 지도"], ["3d", "🏔️ 3D 지형"]] as const).map(([mode, label]) => (
                <button key={mode} onClick={() => setMapMode(mode)}
                  style={{
                    flex: 1, padding: "10px 0", border: "none", cursor: "pointer",
                    fontFamily: "inherit", fontSize: 12, fontWeight: 600, letterSpacing: "0.04em",
                    background: mapMode === mode ? "#1d4ed8" : "transparent",
                    color: mapMode === mode ? "#fff" : "#475569",
                    borderBottom: mapMode === mode ? "2px solid #3b82f6" : "2px solid transparent",
                  }}>{label}</button>
              ))}
            </div>

            <div style={{ height: 420 }}>
              <MapboxTerrain track={track} mode={mapMode} hoveredPoint={hoveredPoint} />
            </div>

            {track && (
              <div style={{ borderTop: "1px solid #1e293b", background: "#0f172a", padding: "8px 8px 4px" }}>
                <div style={{ fontSize: 10, color: "#334155", paddingLeft: 8, marginBottom: 2 }}>고도 프로파일</div>
                <ElevChart track={track} onHover={setHoveredPoint} hoveredPoint={hoveredPoint} />
              </div>
            )}
          </div>

          {/* 2단 레이아웃 */}
          <div style={{ display: "grid", gridTemplateColumns: "1fr 320px", gap: 16, alignItems: "start" }}>
            <div>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
                <h2 style={S.sectionTitle}>구간별 계획표</h2>
                <span style={{ color: "#64748b", fontSize: 13 }}>목표 속도 {targetSpeed} km/h</span>
              </div>
              {cps.map((cp, idx) => {
                const segDist = idx === 0 ? 0 : cp.distance - cps[idx - 1].distance;
                const isExp = expandedCp === cp.id;
                const dotC = idx === 0 ? "#22c55e" : idx === cps.length - 1 ? "#ef4444" : "#3b82f6";
                const mc = marginColor(cp.deadline, cp.targetArrival);
                const w = cp.weather;
                return (
                  <div key={cp.id} style={{ display: "flex", alignItems: "flex-start" }}>
                    <div style={{ display: "flex", flexDirection: "column", alignItems: "center", width: 32, paddingTop: 18, flexShrink: 0 }}>
                      <div style={{ width: 12, height: 12, borderRadius: "50%", background: dotC }} />
                      {idx < cps.length - 1 && <div style={{ width: 2, flex: 1, background: "#334155", minHeight: 40, marginTop: 4 }} />}
                    </div>
                    <div style={{ flex: 1, background: "#1e293b", border: "1px solid #334155", borderRadius: 12, marginBottom: 8, overflow: "hidden" }}>
                      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "14px 18px", cursor: "pointer" }}
                        onClick={() => setExpandedCp(isExp ? null : cp.id)}>
                        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                          <span style={{ padding: "3px 8px", borderRadius: 6, fontSize: 11, fontWeight: 700, color: "white", background: dotC }}>
                            {idx === 0 ? "출발" : idx === cps.length - 1 ? "완주" : `CP${idx}`}
                          </span>
                          <div>
                            <div style={{ fontSize: 15, fontWeight: 600, color: "#f1f5f9" }}>{cp.name}</div>
                            <div style={{ fontSize: 12, color: "#64748b", marginTop: 2 }}>
                              {cp.distance} km 지점
                              {segDist > 0 && <span style={{ color: "#3b82f6" }}> · +{segDist} km</span>}
                            </div>
                          </div>
                        </div>
                        <div style={{ display: "flex", alignItems: "center", gap: 14 }}>
                          {w && (
                            <div style={{ display: "flex", alignItems: "center", gap: 5, background: "#0f172a", padding: "5px 10px", borderRadius: 8, fontSize: 13 }}>
                              <span>{w.icon}</span>
                              <span style={{ fontWeight: 600 }}>{w.temp}°C</span>
                              <span style={{ color: "#64748b", fontSize: 11 }}>💨{w.wind}</span>
                            </div>
                          )}
                          <div style={{ display: "flex", gap: 8 }}>
                            <div style={{ textAlign: "center" }}>
                              <div style={{ fontSize: 10, color: "#64748b" }}>목표</div>
                              <div style={{ fontSize: 14, fontWeight: 700, color: "#e2e8f0" }}>{cp.targetArrival}</div>
                            </div>
                            <div style={{ textAlign: "center" }}>
                              <div style={{ fontSize: 10, color: "#64748b" }}>마감</div>
                              <div style={{ fontSize: 14, fontWeight: 700, color: mc }}>{cp.deadline}</div>
                            </div>
                          </div>
                          <span style={{ color: "#64748b", fontSize: 12 }}>{isExp ? "▲" : "▼"}</span>
                        </div>
                      </div>
                      {isExp && (
                        <div style={{ padding: "14px 18px 18px", borderTop: "1px solid #334155" }}>
                          {segDist > 0 && (
                            <div style={{ display: "flex", gap: 10, marginBottom: 14 }}>
                              {[
                                { label: "구간 거리", val: `${segDist} km` },
                                { label: "예상 소요", val: `${Math.round(segDist / Number(targetSpeed) * 60)}분` },
                                { label: "마감 여유", val: marginText(cp.deadline, cp.targetArrival), color: mc },
                                { label: "누적 고도", val: `${cp.elevation}m` },
                              ].map((item) => (
                                <div key={item.label} style={{ flex: 1, background: "#0f172a", borderRadius: 8, padding: "10px 12px" }}>
                                  <div style={{ fontSize: 11, color: "#64748b" }}>{item.label}</div>
                                  <div style={{ fontSize: 15, fontWeight: 700, color: item.color || "#e2e8f0", marginTop: 2 }}>{item.val}</div>
                                </div>
                              ))}
                            </div>
                          )}
                          {w?.hourly && (
                            <div>
                              <div style={{ fontSize: 11, color: "#64748b", marginBottom: 8 }}>시간대별 날씨</div>
                              <div style={{ display: "flex", gap: 6 }}>
                                {w.hourly.map((h) => (
                                  <div key={h.time} style={{ flex: 1, background: "#0f172a", borderRadius: 8, padding: "8px 4px", textAlign: "center" }}>
                                    <div style={{ fontSize: 10, color: "#475569" }}>{h.time}</div>
                                    <div style={{ fontSize: 18 }}>{h.icon}</div>
                                    <div style={{ fontSize: 12, fontWeight: 600, color: "#93c5fd" }}>{h.temp}°</div>
                                    <div style={{ fontSize: 10, color: h.precip > 50 ? "#f87171" : "#475569" }}>{h.precip}%</div>
                                    <div style={{ fontSize: 11, color: h.wind > 30 ? "#fbbf24" : "#64748b" }}>{h.wind}㎞</div>
                                    <div style={{ fontSize: 14, transform: `rotate(${h.windDir}deg)`, display: "inline-block", lineHeight: 1 }}>↑</div>
                                  </div>
                                ))}
                              </div>
                            </div>
                          )}
                        </div>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>

            {/* 날씨 패널 */}
            <div style={{ position: "sticky", top: 16 }}>
              <h2 style={{ ...S.sectionTitle, marginBottom: 12 }}>☁️ 구간 날씨 요약</h2>
              <div style={{ fontSize: 11, color: "#334155", marginBottom: 12 }}>{eventDate} · 09:00 기준</div>
              {cps.map((cp, i) => (
                <WeatherPanel key={cp.id} cp={cp} prev={i > 0 ? cps[i - 1] : undefined} />
              ))}

              {/* Claude 분석 */}
              <div style={{ marginTop: 16, background: "linear-gradient(135deg,#0f1f3d,#0a1628)", border: "1px solid #1e3a5f", borderRadius: 12, padding: 16 }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
                  <h2 style={{ ...S.sectionTitle, fontSize: 14 }}>🤖 Claude AI 분석</h2>
                  <button
                    onClick={handleBrevetAnalyze}
                    disabled={analysisLoading}
                    style={{ padding: "6px 14px", background: analysisLoading ? "#334155" : "linear-gradient(135deg,#2563eb,#6366f1)", border: "none", borderRadius: 8, color: "#fff", fontSize: 12, fontWeight: 600, cursor: analysisLoading ? "not-allowed" : "pointer" }}
                  >
                    {analysisLoading ? "⏳ 분석 중..." : brevetAnalysis ? "🔄 재분석" : "✨ 분석하기"}
                  </button>
                </div>

                {!brevetAnalysis && !analysisLoading && (
                  <div style={{ color: "#334155", fontSize: 12, textAlign: "center", padding: "16px 0" }}>
                    날씨 로드 후 분석하기를 눌러주세요
                  </div>
                )}

                {analysisLoading && (
                  <div style={{ color: "#475569", fontSize: 12, textAlign: "center", padding: "16px 0" }}>
                    Claude가 코스와 날씨를 분석 중...
                  </div>
                )}

                {brevetAnalysis && (
                  <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
                    {/* 점수 + 요약 */}
                    <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                      <div style={{ width: 48, height: 48, borderRadius: "50%", background: "linear-gradient(135deg,#2563eb,#6366f1)", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 16, fontWeight: 800, color: "#fff", flexShrink: 0 }}>
                        {brevetAnalysis.score}
                      </div>
                      <div>
                        <div style={{ fontSize: 13, fontWeight: 600, color: "#f1f5f9" }}>{brevetAnalysis.summary}</div>
                        <span style={{ fontSize: 11, padding: "2px 8px", borderRadius: 10, background: brevetAnalysis.overallCondition === "좋음" ? "#15803d" : brevetAnalysis.overallCondition === "어려움" ? "#dc2626" : "#d97706", color: "#fff", fontWeight: 600 }}>
                          {brevetAnalysis.overallCondition}
                        </span>
                      </div>
                    </div>

                    {/* 페이스 전략 */}
                    <div style={{ background: "#0f172a", borderRadius: 8, padding: "8px 12px", borderLeft: "3px solid #3b82f6" }}>
                      <div style={{ fontSize: 11, color: "#64748b", marginBottom: 4 }}>📍 페이스 전략</div>
                      <div style={{ fontSize: 12, color: "#93c5fd" }}>{brevetAnalysis.paceStrategy}</div>
                    </div>

                    {/* 주의사항 */}
                    {brevetAnalysis.warnings?.length > 0 && (
                      <div>
                        <div style={{ fontSize: 11, fontWeight: 700, color: "#f87171", marginBottom: 4 }}>⚠️ 주의사항</div>
                        {brevetAnalysis.warnings.map((w, i) => (
                          <div key={i} style={{ fontSize: 11, color: "#fca5a5", padding: "2px 0" }}>· {w}</div>
                        ))}
                      </div>
                    )}

                    {/* 구간별 팁 */}
                    {brevetAnalysis.tips?.length > 0 && (
                      <div>
                        <div style={{ fontSize: 11, fontWeight: 700, color: "#34d399", marginBottom: 4 }}>💡 구간별 팁</div>
                        {brevetAnalysis.tips.map((t, i) => (
                          <div key={i} style={{ fontSize: 11, color: "#6ee7b7", padding: "2px 0" }}>· {t}</div>
                        ))}
                      </div>
                    )}

                    {/* 종합 조언 */}
                    <div style={{ background: "#0f172a", borderRadius: 8, padding: "8px 12px", borderLeft: "3px solid #34d399" }}>
                      <div style={{ fontSize: 11, color: "#64748b", marginBottom: 4 }}>🏁 종합 조언</div>
                      <div style={{ fontSize: 12, color: "#e2e8f0" }}>{brevetAnalysis.conclusion}</div>
                    </div>
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

const S: Record<string, React.CSSProperties> = {
  page: { padding: "24px 24px 48px", maxWidth: 1200, fontFamily: "'IBM Plex Mono','Pretendard',monospace", color: "#e2e8f0", background: "#0f172a", minHeight: "100vh" },
  header: { display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 28 },
  title: { margin: 0, fontSize: 22, fontWeight: 800, color: "#f1f5f9", fontFamily: "'Exo 2',sans-serif" },
  subtitle: { margin: "2px 0 0", fontSize: 12, color: "#64748b" },
  backBtn: { padding: "8px 16px", background: "#1e293b", border: "1px solid #334155", borderRadius: 8, color: "#94a3b8", cursor: "pointer", fontSize: 13 },
  formCard: { background: "#1e293b", borderRadius: 16, padding: 32, border: "1px solid #334155" },
  sectionTitle: { margin: 0, fontSize: 16, fontWeight: 600, color: "#f1f5f9" },
  formGrid: { display: "grid", gridTemplateColumns: "1fr 1fr", gap: 20, marginBottom: 28, marginTop: 20 },
  input: { padding: "10px 14px", background: "#0f172a", border: "1px solid #334155", borderRadius: 8, color: "#e2e8f0", fontSize: 14, outline: "none", fontFamily: "inherit" },
  fileLabel: { padding: "10px 14px", background: "#0f172a", border: "1px dashed", borderRadius: 8, fontSize: 13, cursor: "pointer", textAlign: "center" as const, display: "block" },
  analyzeBtn: { width: "100%", padding: "14px", background: "linear-gradient(135deg,#3b82f6,#6366f1)", border: "none", borderRadius: 10, color: "white", fontSize: 15, fontWeight: 700, cursor: "pointer" },
  summaryRow: { display: "grid", gridTemplateColumns: "repeat(4,1fr)", gap: 12, marginBottom: 20 },
  summaryCard: { background: "#1e293b", border: "1px solid #334155", borderRadius: 12, padding: "16px 18px", display: "flex", alignItems: "center", gap: 12 },
  summaryValue: { fontSize: 17, fontWeight: 700, color: "#f1f5f9" },
  summaryLabel: { fontSize: 12, color: "#64748b", marginTop: 2 },
};
