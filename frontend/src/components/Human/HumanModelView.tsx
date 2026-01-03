// src/components/Human/HumanModelView.tsx
import { Canvas } from "@react-three/fiber";
import { OrbitControls } from "@react-three/drei";

import { HumanModel } from "./HumanModel";
import { resolveBodyType } from "./resolveBodyType";

import type { BodySummaryRecord } from "@/types/BodySummaryRecord";
import {BODY_TYPE_DESCRIPTIONS} from "@/components/BodyTypeDescription";

interface HumanModelViewProps {
    summary: BodySummaryRecord;
}

export default function HumanModelView({ summary }: HumanModelViewProps) {
    const bodyType = resolveBodyType(summary);
    const desc = BODY_TYPE_DESCRIPTIONS[bodyType];

    return (
        <div className="human-model-view">
            {/* 🔼 모델 영역 */}
            <div className="model-canvas-wrapper">
                <Canvas camera={{ position: [0, 1.6, 4.5], fov: 35 }}>
                    <ambientLight intensity={0.5} />
                    <directionalLight position={[3, 5, 5]} intensity={1} />

                    <HumanModel bodyType={bodyType} />

                    <OrbitControls
                        enablePan={false}
                        enableZoom={false}
                        minPolarAngle={Math.PI / 2}
                        maxPolarAngle={Math.PI / 2}
                        target={[0, 1.2, 0]}
                    />
                </Canvas>
            </div>

            {/* 🔽 설명 영역 */}
            <div className="body-type-desc">
                <h4>{desc.title}</h4>
                <p className="subtitle">{desc.subtitle}</p>

                <ul>
                    {desc.features.map((f, i) => (
                        <li key={i}>{f}</li>
                    ))}
                </ul>
            </div>
        </div>
    );
}
