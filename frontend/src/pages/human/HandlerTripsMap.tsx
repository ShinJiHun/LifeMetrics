// 핸들 경로 지도 — 여러 경로를 한 번에 그리고, 선택한 경로만 진하게.
import { useEffect, useRef } from "react";
import * as mapboxgl from "mapbox-gl";
import "mapbox-gl/dist/mapbox-gl.css";
import polyline from "@mapbox/polyline";

export interface MapTrip {
    id: number | string;
    polyline: string | null;
    origin?: [number, number] | null; // [lng, lat]
    dest?: [number, number] | null;
}

const LINE = "#2563eb";
const LINE_DIM = "#93c5fd";

function coordsOf(t: MapTrip): [number, number][] {
    if (t.polyline) return polyline.decode(t.polyline).map(([lat, lng]) => [lng, lat] as [number, number]);
    if (t.origin && t.dest) return [t.origin, t.dest];
    return [];
}

export default function HandlerTripsMap({
    trips,
    selectedId,
    onSelect,
    height = 420,
}: {
    trips: MapTrip[];
    selectedId?: number | string | null;
    onSelect?: (id: number | string) => void;
    height?: number;
}) {
    const box = useRef<HTMLDivElement>(null);
    const map = useRef<mapboxgl.Map | null>(null);
    const ready = useRef(false);
    const markers = useRef<mapboxgl.Marker[]>([]);
    const onSelectRef = useRef(onSelect);
    onSelectRef.current = onSelect;

    // 지도 1회 생성
    useEffect(() => {
        if (!box.current) return;
        const m = new mapboxgl.Map({
            container: box.current,
            style: "mapbox://styles/mapbox/light-v11",
            center: [126.978, 37.5665],
            zoom: 10,
            accessToken: import.meta.env.VITE_MAPBOX_TOKEN,
        });
        map.current = m;
        m.addControl(new mapboxgl.NavigationControl({ showCompass: false }), "top-right");
        m.on("load", () => {
            m.addSource("trips", { type: "geojson", data: { type: "FeatureCollection", features: [] } });
            m.addLayer({
                id: "trips-line",
                type: "line",
                source: "trips",
                layout: { "line-join": "round", "line-cap": "round" },
                paint: {
                    "line-color": ["case", ["get", "sel"], LINE, LINE_DIM],
                    "line-width": ["case", ["get", "sel"], 5, 3],
                    "line-opacity": ["case", ["get", "sel"], 1, 0.75],
                },
            });
            m.addLayer({
                id: "trips-hit",
                type: "line",
                source: "trips",
                paint: { "line-color": "#000", "line-width": 14, "line-opacity": 0 },
            });
            m.on("click", "trips-hit", (e) => {
                const id = e.features?.[0]?.properties?.id;
                if (id != null) onSelectRef.current?.(id);
            });
            m.on("mouseenter", "trips-hit", () => (m.getCanvas().style.cursor = "pointer"));
            m.on("mouseleave", "trips-hit", () => (m.getCanvas().style.cursor = ""));
            ready.current = true;
            m.fire("trips:update" as never);
        });
        const ro = new ResizeObserver(() => m.resize());
        ro.observe(box.current);
        return () => {
            ro.disconnect();
            m.remove();
            map.current = null;
            ready.current = false;
        };
    }, []);

    // 데이터·선택 반영
    useEffect(() => {
        const m = map.current;
        if (!m) return;
        const apply = () => {
            const src = m.getSource("trips") as mapboxgl.GeoJSONSource | undefined;
            if (!src) return;
            const lines = trips.map((t) => ({ t, coords: coordsOf(t) })).filter((x) => x.coords.length >= 2);
            // 선택한 경로가 위에 그려지도록 마지막에
            lines.sort((a, b) => Number(a.t.id === selectedId) - Number(b.t.id === selectedId));
            src.setData({
                type: "FeatureCollection",
                features: lines.map(({ t, coords }) => ({
                    type: "Feature",
                    properties: { id: t.id, sel: selectedId == null || t.id === selectedId },
                    geometry: { type: "LineString", coordinates: coords },
                })),
            });

            markers.current.forEach((mk) => mk.remove());
            markers.current = [];
            const focus = selectedId != null ? lines.filter((x) => x.t.id === selectedId) : lines;
            if (selectedId != null || trips.length === 1) {
                for (const { t, coords } of focus) {
                    markers.current.push(new mapboxgl.Marker({ color: "#16a34a", scale: 0.8 }).setLngLat(t.origin ?? coords[0]).addTo(m));
                    markers.current.push(
                        new mapboxgl.Marker({ color: "#dc2626", scale: 0.8 }).setLngLat(t.dest ?? coords[coords.length - 1]).addTo(m),
                    );
                }
            }
            const all = (focus.length ? focus : lines).flatMap((x) => x.coords);
            if (all.length) {
                const b = all.reduce((acc, c) => acc.extend(c), new mapboxgl.LngLatBounds(all[0], all[0]));
                m.fitBounds(b, { padding: 40, maxZoom: 15, duration: 400 });
            }
        };
        if (ready.current) apply();
        else m.once("trips:update" as never, apply);
    }, [trips, selectedId]);

    return <div ref={box} style={{ width: "100%", height, borderRadius: 8, overflow: "hidden", background: "#f3f4f6" }} />;
}
