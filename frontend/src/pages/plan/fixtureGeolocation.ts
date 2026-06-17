/**
 * fixtureGeolocation.ts  (개발/테스트 전용)
 * "위치 + 경과시간"을 명시한 fix 목록을 한 점씩 navigator.geolocation 으로 흘려보낸다.
 * next/prev/goto/play 로 값을 바꿔가며 실제 동작(진행률·평균속도·마감여유·필요페이스)을 검증.
 *
 * useLiveRide 는 실주행 모드 그대로(now = new Date(pos.timestamp) 사용 권장).
 */

export interface GeoFix {
    lat: number;
    lon: number;
    timeMin: number;   // 출발 기준 경과(분)
    label?: string;    // UI 표시용(선택)
}

export interface FixtureController {
    next(): void;
    prev(): void;
    goto(i: number): void;
    play(stepMs?: number): void;
    pause(): void;
    index(): number;
    count(): number;
    current(): GeoFix | null;
    uninstall(): void;
}

type PosCb = (pos: GeolocationPosition) => void;
let original: Geolocation | null = null;

function makePosition(lat: number, lon: number, accuracy: number, timestamp: number): GeolocationPosition {
    return {
        coords: {
            latitude: lat, longitude: lon, accuracy,
            altitude: null, altitudeAccuracy: null, heading: null, speed: null,
        } as GeolocationCoordinates,
        timestamp,
    } as GeolocationPosition;
}

export function installFixtureGeolocation(fixes: GeoFix[], startMs: number, accuracy = 5): FixtureController {
    if (original == null) original = navigator.geolocation;

    const watchers = new Map<number, PosCb>();
    let seq = 1;
    let idx = 0;
    let playTimer: ReturnType<typeof setInterval> | null = null;

    const emit = () => {
        const f = fixes[idx];
        if (!f) return;
        const pos = makePosition(f.lat, f.lon, accuracy, startMs + f.timeMin * 60000);
        watchers.forEach(cb => cb(pos));
    };

    const mock: Partial<Geolocation> = {
        getCurrentPosition: (s: PositionCallback) => {
            const f = fixes[idx];
            if (f) s(makePosition(f.lat, f.lon, accuracy, startMs + f.timeMin * 60000));
        },
        watchPosition: (s: PositionCallback) => {
            const id = seq++;
            watchers.set(id, s as PosCb);
            Promise.resolve().then(emit); // 구독 즉시 현재 fix 1회
            return id;
        },
        clearWatch: (id: number) => { watchers.delete(id); },
    };
    Object.defineProperty(navigator, "geolocation", { value: mock, configurable: true });

    const ctrl: FixtureController = {
        next() { idx = Math.min(fixes.length - 1, idx + 1); emit(); },
        prev() { idx = Math.max(0, idx - 1); emit(); },
        goto(i) { idx = Math.max(0, Math.min(fixes.length - 1, i)); emit(); },
        play(stepMs = 1500) {
            ctrl.pause();
            playTimer = setInterval(() => {
                if (idx >= fixes.length - 1) { ctrl.pause(); return; }
                idx++; emit();
            }, stepMs);
        },
        pause() { if (playTimer) { clearInterval(playTimer); playTimer = null; } },
        index: () => idx,
        count: () => fixes.length,
        current: () => fixes[idx] ?? null,
        uninstall() {
            ctrl.pause();
            if (original) {
                Object.defineProperty(navigator, "geolocation", { value: original, configurable: true });
                original = null;
            }
        },
    };
    return ctrl;
}
