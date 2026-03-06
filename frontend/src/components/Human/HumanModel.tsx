import { useGLTF } from "@react-three/drei";
import type { BodyType } from "./human.types";

// src/components/Human/modelMap.ts

export const MODEL_MAP = {
    lean: "/models/body_lean.glb",
    normal: "/models/body_normal.glb",
    fit: "/models/body_fit.glb",
    athlete: "/models/body_athlete_ref.glb",
    overweight: "/models/body_overweight.glb",
    obese: "/models/body_obese.glb",
} as const;

export function HumanModel({ bodyType }: { bodyType: BodyType }) {
    const { scene } = useGLTF(MODEL_MAP[bodyType]);
1
    // 중심 맞추기
    scene.traverse(obj => {
        if (obj.type === "Mesh") {
            obj.castShadow = true;
            obj.receiveShadow = true;
        }
    });

    return (
        <primitive
            object={scene}
            scale={1.3}          // ⬅️ 1.7 → 1.45 로 줄임
            position={[0, 0, 0]}  // 고정
        />
    );

}

