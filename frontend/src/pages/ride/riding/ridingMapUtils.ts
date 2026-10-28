// ── 라이딩 지도: 경로 가공 · 타일 탐험 계산 ─────────────────────────
// statshunters 처럼 지도를 줌 14 타일(한국 기준 한 변 약 1.9km)로 나눠 지나간 칸을 센다.

import polyline from "@mapbox/polyline";

export const TILE_ZOOM = 14;
const TILE_N = 2 ** TILE_ZOOM;

/** [lng, lat] 목록 (mapbox 순서) */
export type LngLat = [number, number];

export function decodeRoute(encoded: string): LngLat[] {
    if (!encoded) return [];
    return polyline.decode(encoded).map(([lat, lng]) => [lng, lat] as LngLat);
}

const toRad = (d: number) => (d * Math.PI) / 180;

/** 두 점 사이 거리(m), 짧은 거리용 근사 */
function distM(a: LngLat, b: LngLat): number {
    const x = toRad(b[0] - a[0]) * Math.cos(toRad((a[1] + b[1]) / 2));
    const y = toRad(b[1] - a[1]);
    return Math.sqrt(x * x + y * y) * 6371000;
}

/**
 * 단순화된 경로는 직선 구간에 점이 거의 없으므로, stepM 간격으로 점을 채워 넣는다.
 * 히트맵 밀도와 타일 판정이 직선 구간에서 끊기지 않게 하려는 것.
 */
export function densify(route: LngLat[], stepM: number): LngLat[] {
    if (route.length < 2) return route;
    const out: LngLat[] = [route[0]];
    for (let i = 1; i < route.length; i++) {
        const a = route[i - 1];
        const b = route[i];
        const n = Math.floor(distM(a, b) / stepM);
        for (let k = 1; k <= n; k++) {
            const t = k / (n + 1);
            out.push([a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t]);
        }
        out.push(b);
    }
    return out;
}

export function lngLatToTile([lng, lat]: LngLat): [number, number] {
    const x = Math.floor(((lng + 180) / 360) * TILE_N);
    const r = toRad(lat);
    const y = Math.floor(((1 - Math.log(Math.tan(r) + 1 / Math.cos(r)) / Math.PI) / 2) * TILE_N);
    return [x, y];
}

/** 타일 좌상단 [lng, lat] */
function tileCorner(x: number, y: number): LngLat {
    const lng = (x / TILE_N) * 360 - 180;
    const lat = (Math.atan(Math.sinh(Math.PI * (1 - (2 * y) / TILE_N))) * 180) / Math.PI;
    return [lng, lat];
}

/** 타일 x0..x1, y0..y1 범위(포함)를 덮는 사각형 폴리곤 좌표 */
export function tileRectPolygon(x0: number, y0: number, x1: number, y1: number): LngLat[][] {
    const nw = tileCorner(x0, y0);
    const se = tileCorner(x1 + 1, y1 + 1);
    return [[nw, [se[0], nw[1]], se, [nw[0], se[1]], nw]];
}

export const tileKey = (x: number, y: number) => `${x},${y}`;

export interface TileStats {
    visited: Set<string>;
    /** 상하좌우가 모두 방문된 타일 중 가장 큰 연결 묶음 */
    maxCluster: Set<string>;
    /** 빈틈없이 방문한 가장 큰 정사각형 */
    maxSquare: { x: number; y: number; size: number } | null;
}

export function computeTileStats(points: LngLat[]): TileStats {
    const visited = new Set<string>();
    let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
    for (const p of points) {
        const [x, y] = lngLatToTile(p);
        visited.add(tileKey(x, y));
        if (x < minX) minX = x;
        if (y < minY) minY = y;
        if (x > maxX) maxX = x;
        if (y > maxY) maxY = y;
    }
    if (visited.size === 0) return {visited, maxCluster: new Set(), maxSquare: null};

    const has = (x: number, y: number) => visited.has(tileKey(x, y));

    // 클러스터: 4방향 이웃이 모두 방문된 타일끼리 연결된 묶음
    const inner = new Set<string>();
    for (const k of visited) {
        const [x, y] = k.split(",").map(Number);
        if (has(x + 1, y) && has(x - 1, y) && has(x, y + 1) && has(x, y - 1)) inner.add(k);
    }
    let maxCluster = new Set<string>();
    const seen = new Set<string>();
    for (const start of inner) {
        if (seen.has(start)) continue;
        const group = new Set<string>([start]);
        const stack = [start];
        seen.add(start);
        while (stack.length) {
            const [x, y] = stack.pop()!.split(",").map(Number);
            for (const [nx, ny] of [[x + 1, y], [x - 1, y], [x, y + 1], [x, y - 1]]) {
                const nk = tileKey(nx, ny);
                if (inner.has(nk) && !seen.has(nk)) {
                    seen.add(nk);
                    group.add(nk);
                    stack.push(nk);
                }
            }
        }
        if (group.size > maxCluster.size) maxCluster = group;
    }

    // 최대 정사각형: 방문 타일 범위에서 DP (dp = 그 칸을 오른쪽 아래 끝으로 하는 정사각형 한 변)
    const w = maxX - minX + 1;
    const h = maxY - minY + 1;
    let maxSquare: TileStats["maxSquare"] = null;
    if (w * h <= 4_000_000) {
        let prev = new Int32Array(w + 1);
        for (let j = 0; j < h; j++) {
            const cur = new Int32Array(w + 1);
            for (let i = 0; i < w; i++) {
                if (!has(minX + i, minY + j)) continue;
                const v = Math.min(prev[i], prev[i + 1], cur[i]) + 1;
                cur[i + 1] = v;
                if (!maxSquare || v > maxSquare.size) {
                    maxSquare = {x: minX + i - v + 1, y: minY + j - v + 1, size: v};
                }
            }
            prev = cur;
        }
    }

    return {visited, maxCluster, maxSquare};
}

// ── 타입 묶음 ──
export const RANDO_TYPES = ["BREVET", "PERMANENT", "FLECHE", "POPULAIRE"];

export type TypeFilter = "ALL" | "RANDO" | "TOURING" | "GENERAL";

export const TYPE_FILTERS: { key: TypeFilter; label: string }[] = [
    {key: "ALL", label: "전체"},
    {key: "RANDO", label: "랜도 (브레베·퍼머넌트·플레쉬·포퓨레어)"},
    {key: "TOURING", label: "투어링"},
    {key: "GENERAL", label: "일반"},
];

export function matchesType(rideType: string | undefined, f: TypeFilter): boolean {
    if (f === "ALL") return true;
    if (f === "RANDO") return !!rideType && RANDO_TYPES.includes(rideType);
    if (f === "TOURING") return rideType === "TOURING";
    return !rideType || rideType === "GENERAL";
}
