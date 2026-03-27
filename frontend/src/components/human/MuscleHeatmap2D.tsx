// src/components/Human/MuscleHeatmap2D.tsx
// 2D SVG 근육 히트맵 - react-body-highlighter 기반
// 3D Canvas 대신 가볍고 안정적인 SVG 인체 모델
import { useMemo, useState } from "react";
import type { MuscleActivation } from "./MuscleHeatmapModel";
import Model from "react-body-highlighter";  // 이 줄 있는지 확인

// ============================================================
// MuscleActivation → react-body-highlighter data 매핑
// ============================================================

// react-body-highlighter의 muscle slug 목록:
// Front: chest, abs, obliques, front-deltoids, biceps, forearm, quadriceps, adductor
// Back:  trapezius, upper-back, lower-back, back-deltoids, triceps, forearm, hamstring, gluteal, calves, abductors

// MuscleActivation key → body-highlighter slug 매핑
const ACTIVATION_TO_SLUG: {
    key: keyof MuscleActivation;
    slug: string;
    side: "front" | "back" | "both";
}[] = [
    // 가슴
    { key: "upperChest", slug: "chest", side: "front" },
    { key: "midChest", slug: "chest", side: "front" },
    { key: "lowerChest", slug: "chest", side: "front" },
    { key: "innerChest", slug: "chest", side: "front" },
    // 등
    { key: "lats", slug: "upper-back", side: "back" },
    { key: "upperTraps", slug: "trapezius", side: "back" },
    { key: "midTraps", slug: "upper-back", side: "back" },
    { key: "lowerTraps", slug: "lower-back", side: "back" },
    { key: "rhomboids", slug: "upper-back", side: "back" },
    { key: "teresMajor", slug: "upper-back", side: "back" },
    // 어깨
    { key: "frontDelt", slug: "front-deltoids", side: "front" },
    { key: "sideDelt", slug: "front-deltoids", side: "front" },
    { key: "rearDelt", slug: "back-deltoids", side: "back" },
    { key: "rotatorCuff", slug: "back-deltoids", side: "back" },
    // 팔
    { key: "biceps", slug: "biceps", side: "front" },
    { key: "triceps", slug: "triceps", side: "back" },
    { key: "forearms", slug: "forearm", side: "both" },
    // 하체
    { key: "quads", slug: "quadriceps", side: "front" },
    { key: "hamstrings", slug: "hamstring", side: "back" },
    { key: "glutes", slug: "gluteal", side: "back" },
    { key: "gluteMed", slug: "abductors", side: "back" },
    // 코어
    { key: "upperAbs", slug: "abs", side: "front" },
    { key: "lowerAbs", slug: "abs", side: "front" },
    { key: "obliques", slug: "obliques", side: "front" },
    { key: "erectors", slug: "lower-back", side: "back" },
    // 종아리
    { key: "calves", slug: "calves", side: "back" },
];

// activation 값(0~1) → intensity 레벨(0~3)
function toIntensity(value: number): number {
    if (value <= 0.01) return 0;
    if (value < 0.3) return 1;
    if (value < 0.7) return 2;
    return 3;
}

interface MuscleHeatmap2DProps {
    activation: MuscleActivation;
    height?: number;
}

export default function MuscleHeatmap2D({
                                            activation,
                                            height = 350,
                                        }: MuscleHeatmap2DProps) {
    const [viewSide, setViewSide] = useState<"front" | "back">("front");

    // activation → react-body-highlighter data 변환
    const bodyData = useMemo(() => {
        // slug별 최대 activation 값 수집
        const slugMaxMap: Record<string, { value: number; side: "front" | "back" | "both" }> = {};

        ACTIVATION_TO_SLUG.forEach(({ key, slug, side }) => {
            const val = activation[key] || 0;
            if (val <= 0.01) return;

            if (!slugMaxMap[slug] || val > slugMaxMap[slug].value) {
                slugMaxMap[slug] = { value: val, side };
            }
        });

        // 현재 뷰(front/back)에 맞는 데이터만 필터
        return Object.entries(slugMaxMap)
            .filter(([, info]) => info.side === viewSide || info.side === "both")
            .map(([slug, info]) => ({
                name: slug,
                muscles: [slug] as any,
                frequency: toIntensity(info.value),
            }));
    }, [activation, viewSide]);

    // 활성 근육이 있는지
    const hasActivation = Object.values(activation).some((v) => v > 0.01);

    return (
        <div className="muscle-heatmap-2d">
            {/* 전면/후면 토글 */}
            <div className="heatmap-view-toggle">
                <button
                    className={`toggle-btn ${viewSide === "front" ? "active" : ""}`}
                    onClick={() => setViewSide("front")}
                >
                    전면
                </button>
                <button
                    className={`toggle-btn ${viewSide === "back" ? "active" : ""}`}
                    onClick={() => setViewSide("back")}
                >
                    후면
                </button>
            </div>

            {/* SVG 바디 모델 */}
            <div className="heatmap-body-wrapper">
                {hasActivation ? (
                    <Model
                        data={bodyData as any}
                        style={{width: "auto", height: `${height}px`, margin: "0 auto"}}
                        type={viewSide as any}
                    />
                ) : (
                    <div className="heatmap-empty">
                        <Model
                            data={[]}
                            style={{width: "auto", height: `${height}px`, margin: "0 auto", opacity: 0.4}}
                            type={viewSide as any}
                        />
                        <p className="heatmap-empty-text">근육 데이터 없음</p>
                    </div>
                )}
            </div>
        </div>
    );
}
