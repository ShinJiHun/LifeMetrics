import {useEffect, useRef, type MutableRefObject} from "react";
import type {Map as MapboxMap} from "mapbox-gl";

// 라이브 리플레이 지도 위 바람 파티클 (Windy 풍).
// 라이딩 당시 풍향·풍속 한 값으로 화면 전체에 같은 방향으로 흐르게 그린다(위치별 바람장 아님).
// 지도를 돌리면(bearing) 화면 기준 방향도 같이 돌린다. 지도 조작은 막지 않는다(pointer-events: none).

interface Props {
    /** 기상 풍향(도): 바람이 '불어오는' 방향. 0 = 북풍(남쪽으로 흐름). */
    windDeg: number;
    /** 풍속 m/s */
    windSpeed: number;
    /** 지도 회전 각도를 읽어 화면 방향을 맞춘다. */
    mapRef: MutableRefObject<MapboxMap | null>;
}

interface Particle {
    x: number;
    y: number;
    age: number;
    life: number;
    phase: number; // 옆으로 살짝 흔들리는 위상(자연스러움용)
}

const MAX_PARTICLES = 450;
const AREA_PER_PARTICLE = 3200; // px² 당 1개
const FADE_ALPHA = 0.1; // 프레임마다 지우는 양 — 작을수록 꼬리가 길다
const MAX_ALPHA = 0.5; // 경로·마커가 묻히지 않게

export default function WindParticles({windDeg, windSpeed, mapRef}: Props) {
    const canvasRef = useRef<HTMLCanvasElement>(null);

    useEffect(() => {
        const canvas = canvasRef.current;
        if (!canvas) return;
        const ctx = canvas.getContext("2d");
        if (!ctx) return;
        if (window.matchMedia?.("(prefers-reduced-motion: reduce)").matches) return;

        let width = 0;
        let height = 0;
        let particles: Particle[] = [];

        const spawn = (p?: Particle): Particle => {
            const n = p ?? ({} as Particle);
            n.x = Math.random() * width;
            n.y = Math.random() * height;
            n.age = 0;
            n.life = 1.5 + Math.random() * 2.5; // 초
            n.phase = Math.random() * Math.PI * 2;
            return n;
        };

        const resize = () => {
            const dpr = window.devicePixelRatio || 1;
            const rect = canvas.getBoundingClientRect();
            width = rect.width;
            height = rect.height;
            canvas.width = Math.round(width * dpr);
            canvas.height = Math.round(height * dpr);
            ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
            const count = Math.min(MAX_PARTICLES, Math.round((width * height) / AREA_PER_PARTICLE));
            particles = Array.from({length: count}, () => {
                const p = spawn();
                p.age = Math.random() * p.life; // 한꺼번에 사라지지 않게 나이를 흩어 둔다
                return p;
            });
        };
        resize();
        const ro = new ResizeObserver(resize);
        ro.observe(canvas);

        // 풍속 → 화면 속도(px/s). 무풍도 아주 천천히는 흐르게.
        const pxPerSec = Math.min(220, 18 + windSpeed * 14);
        const towardDeg = (windDeg + 180) % 360; // 바람이 흘러가는 방향

        let raf = 0;
        let last: number | null = null;
        const tick = (now: number) => {
            const dt = last == null ? 0 : Math.min(0.05, (now - last) / 1000);
            last = now;

            const bearing = mapRef.current?.getBearing() ?? 0;
            const rad = ((towardDeg - bearing) * Math.PI) / 180;
            const vx = Math.sin(rad) * pxPerSec;
            const vy = -Math.cos(rad) * pxPerSec; // 화면 y 는 아래가 +

            // 꼬리 페이드: 배경을 투명하게 유지하면서 이전 프레임을 조금씩 지운다
            ctx.globalCompositeOperation = "destination-out";
            ctx.fillStyle = `rgba(0,0,0,${FADE_ALPHA})`;
            ctx.fillRect(0, 0, width, height);
            ctx.globalCompositeOperation = "source-over";

            ctx.lineWidth = 1.1;
            ctx.lineCap = "round";
            for (const p of particles) {
                p.age += dt;
                if (p.age > p.life || p.x < -10 || p.x > width + 10 || p.y < -10 || p.y > height + 10) {
                    spawn(p);
                    continue;
                }
                // 진행 방향에 수직으로 아주 살짝 흔들림
                const wobble = Math.sin(now / 600 + p.phase) * 0.15;
                const nx = p.x + (vx - vy * wobble) * dt;
                const ny = p.y + (vy + vx * wobble) * dt;
                // 태어날 때·사라질 때 흐리게
                const t = p.age / p.life;
                const alpha = Math.min(1, t * 4, (1 - t) * 4) * MAX_ALPHA;
                ctx.strokeStyle = `rgba(186,230,253,${alpha.toFixed(3)})`;
                ctx.beginPath();
                ctx.moveTo(p.x, p.y);
                ctx.lineTo(nx, ny);
                ctx.stroke();
                p.x = nx;
                p.y = ny;
            }
            raf = requestAnimationFrame(tick);
        };
        raf = requestAnimationFrame(tick);

        return () => {
            cancelAnimationFrame(raf);
            ro.disconnect();
            ctx.clearRect(0, 0, width, height);
        };
    }, [windDeg, windSpeed, mapRef]);

    return (
        <canvas
            ref={canvasRef}
            style={{position: "absolute", inset: 0, width: "100%", height: "100%", pointerEvents: "none"}}
        />
    );
}
