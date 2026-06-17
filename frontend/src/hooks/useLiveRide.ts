import { useEffect, useRef, useState } from "react";

/** parseGPX 결과에서 이 훅이 쓰는 최소 형태 */
export interface GpxTrackLite {
    points: { lat: number; lon: number; ele: number }[];
    distances: number[]; // 누적 거리(m)
    totalDist: number;   // 전체 거리(m)
}

export interface CpPlan {
    name: string;
    distance: number;   // km (출발 기준 누적)
    deadline: string;   // "HH:MM" | "-"
}

export interface LiveRideState {
    position: { lat: number; lon: number; accuracy: number; ts: number } | null;
    offRouteM: number;        // 경로 이탈 거리(m)
    coveredKm: number;        // 진행 거리
    progressPct: number;      // 진행률 %
    elapsedMin: number;       // 경과(분)
    avgSpeed: number;         // 평균 속도 km/h
    remainingKm: number;      // 남은 거리
    etaFinish: string | null; // 완주 예상 시각 "HH:MM"
    nextCp: {
        name: string;
        distToCpKm: number;
        deadline: string;
        marginMin: number;     // 마감까지 여유(분), 음수면 지각 위험
        requiredSpeed: number; // 마감 맞추려면 필요한 평균 속도
        onTrack: boolean;      // 현재 평균으로 마감 가능한가
    } | null;
    error: string | null;
}

const INIT: LiveRideState = {
    position: null, offRouteM: 0, coveredKm: 0, progressPct: 0, elapsedMin: 0,
    avgSpeed: 0, remainingKm: 0, etaFinish: null, nextCp: null, error: null,
};

function haversine(aLat: number, aLon: number, bLat: number, bLon: number): number {
    const R = 6371000, toRad = (d: number) => (d * Math.PI) / 180;
    const dLat = toRad(bLat - aLat), dLon = toRad(bLon - aLon);
    const x =
        Math.sin(dLat / 2) ** 2 +
        Math.cos(toRad(aLat)) * Math.cos(toRad(bLat)) * Math.sin(dLon / 2) ** 2;
    return R * 2 * Math.atan2(Math.sqrt(x), Math.sqrt(1 - x));
}

/** 현재 좌표를 경로상 가장 가까운 점에 스냅 → 진행거리 + 이탈거리 */
function snapToRoute(track: GpxTrackLite, lat: number, lon: number) {
    let best = Infinity, idx = 0;
    for (let i = 0; i < track.points.length; i++) {
        const d = haversine(lat, lon, track.points[i].lat, track.points[i].lon);
        if (d < best) { best = d; idx = i; }
    }
    return { idx, distAlong: track.distances[idx], offRouteM: best };
}

function hhmm(d: Date): string {
    return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
}

/** "HH:MM" 마감을 출발일 기준 Date로. 자정 넘기면 다음날로 보정. */
function deadlineToDate(start: Date, deadline: string): Date | null {
    if (deadline === "-") return null;
    const [h, m] = deadline.split(":").map(Number);
    const d = new Date(start);
    d.setHours(h, m, 0, 0);
    if (d.getTime() < start.getTime()) d.setDate(d.getDate() + 1);
    return d;
}

/** 위치 + 현재시각 → 라이브 상태(순수 계산) */
function computeLiveState(
    track: GpxTrackLite, cps: CpPlan[], start: Date,
    lat: number, lon: number, accuracy: number, now: Date,
): LiveRideState {
    const { distAlong, offRouteM } = snapToRoute(track, lat, lon);
    const coveredKm = distAlong / 1000;
    const elapsedMin = (now.getTime() - start.getTime()) / 60000;
    const elapsedH = elapsedMin / 60;
    const avgSpeed = elapsedH > 0 ? coveredKm / elapsedH : 0;
    const remainingKm = (track.totalDist - distAlong) / 1000;
    const etaFinish = avgSpeed > 0
        ? hhmm(new Date(now.getTime() + (remainingKm / avgSpeed) * 3600000))
        : null;

    const next = cps.find(c => c.distance > coveredKm + 0.05);
    let nextCp: LiveRideState["nextCp"] = null;
    if (next) {
        const distToCpKm = Math.max(0, next.distance - coveredKm);
        const dl = deadlineToDate(start, next.deadline);
        const marginMin = dl ? (dl.getTime() - now.getTime()) / 60000 : Infinity;
        const requiredSpeed = dl && marginMin > 0 ? distToCpKm / (marginMin / 60) : 0;
        nextCp = {
            name: next.name, distToCpKm, deadline: next.deadline, marginMin, requiredSpeed,
            onTrack: requiredSpeed === 0 ? true : avgSpeed >= requiredSpeed,
        };
    }

    return {
        position: { lat, lon, accuracy, ts: now.getTime() },
        offRouteM, coveredKm,
        progressPct: Math.min(100, (distAlong / track.totalDist) * 100),
        elapsedMin, avgSpeed, remainingKm, etaFinish, nextCp, error: null,
    };
}

/**
 * 실시간 라이딩 상태.
 *   const live = useLiveRide(track, cps, started ? startDateTime : null);
 *
 * track / startDateTime이 준비되면 navigator.geolocation.watchPosition 구독.
 * "현재 시각"은 GPS fix 자체의 timestamp(now = new Date(p.timestamp))를 사용 →
 *   실제 GPS에서 더 정확하고, fixture/mock으로 가상 시계를 흘려보낼 때도 일관됨.
 * cps는 ref로 들고 있어 갱신돼도 재구독하지 않는다(메모이즈 불필요).
 */
export function useLiveRide(
    track: GpxTrackLite | null,
    cps: CpPlan[],
    startDateTime: Date | null,
) {
    const [state, setState] = useState<LiveRideState>(INIT);
    const watchRef = useRef<number | null>(null);
    const cpsRef = useRef<CpPlan[]>(cps);
    cpsRef.current = cps;

    useEffect(() => {
        if (!track || !startDateTime) return;
        if (!("geolocation" in navigator)) {
            setState(s => ({ ...s, error: "이 브라우저는 위치를 지원하지 않습니다." }));
            return;
        }

        const onPos = (p: GeolocationPosition) => {
            const { latitude, longitude, accuracy } = p.coords;
            if (accuracy > 80) return; // 정확도 낮은 노이즈 fix 무시
            setState(computeLiveState(
                track, cpsRef.current, startDateTime,
                latitude, longitude, accuracy, new Date(p.timestamp),
            ));
        };
        const onErr = (e: GeolocationPositionError) =>
            setState(s => ({ ...s, error: `위치 오류: ${e.message}` }));

        watchRef.current = navigator.geolocation.watchPosition(onPos, onErr, {
            enableHighAccuracy: true, maximumAge: 2000, timeout: 10000,
        });

        return () => {
            if (watchRef.current != null) navigator.geolocation.clearWatch(watchRef.current);
            watchRef.current = null;
        };
    }, [track, startDateTime]);

    return state;
}