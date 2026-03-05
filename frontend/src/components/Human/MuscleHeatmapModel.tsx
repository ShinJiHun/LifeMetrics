// src/components/Human/MuscleHeatmapModel.tsx
import { useRef, useEffect, useMemo } from "react";
import { useGLTF } from "@react-three/drei";
import { useFrame } from "@react-three/fiber";
import * as THREE from "three";

// ============================================================
// 타입 정의
// ============================================================

export interface MuscleActivation {
  upperChest: number;
  midChest: number;
  lowerChest: number;
  innerChest: number;
  lats: number;
  upperTraps: number;
  midTraps: number;
  lowerTraps: number;
  rhomboids: number;
  teresMajor: number;
  frontDelt: number;
  sideDelt: number;
  rearDelt: number;
  rotatorCuff: number;
  biceps: number;
  triceps: number;
  forearms: number;
  quads: number;
  hamstrings: number;
  glutes: number;
  gluteMed: number;
  upperAbs: number;
  lowerAbs: number;
  obliques: number;
  erectors: number;
  calves: number;
}

export const MUSCLE_ID_TO_KEY: Record<number, keyof MuscleActivation> = {
  101: "upperChest", 102: "midChest", 103: "lowerChest", 104: "innerChest",
  201: "lats", 202: "upperTraps", 203: "midTraps", 204: "lowerTraps",
  205: "rhomboids", 206: "teresMajor",
  301: "frontDelt", 302: "sideDelt", 303: "rearDelt", 304: "rotatorCuff",
  4: "biceps", 5: "triceps", 6: "forearms",
  7: "quads", 8: "hamstrings",
  901: "glutes", 902: "gluteMed",
  1101: "upperAbs", 1102: "lowerAbs", 1103: "obliques",
  12: "erectors",
};

export const DEFAULT_ACTIVATION: MuscleActivation = {
  upperChest: 0, midChest: 0, lowerChest: 0, innerChest: 0,
  lats: 0, upperTraps: 0, midTraps: 0, lowerTraps: 0, rhomboids: 0, teresMajor: 0,
  frontDelt: 0, sideDelt: 0, rearDelt: 0, rotatorCuff: 0,
  biceps: 0, triceps: 0, forearms: 0,
  quads: 0, hamstrings: 0, glutes: 0, gluteMed: 0,
  upperAbs: 0, lowerAbs: 0, obliques: 0, erectors: 0,
  calves: 0,
};

// ============================================================
// exercise_muscle_map 데이터 → MuscleActivation 변환
// ============================================================

interface MuscleMapRow {
  muscleGroupId: number;
  role: "PRIMARY" | "SECONDARY" | "SYNERGIST";
  activationLevel: number;
}

interface ExerciseLogForHeatmap {
  sets: { weight: number; reps: number }[];
  muscleMappings: MuscleMapRow[];
}

export function calcActivationFromLogs(logs: ExerciseLogForHeatmap[]): MuscleActivation {
  const raw: Record<string, number> = {};

  logs.forEach((log) => {
    const volume = log.sets.reduce((s, set) => s + Math.max(set.weight, 1) * set.reps, 0);
    log.muscleMappings.forEach((mm) => {
      const key = MUSCLE_ID_TO_KEY[mm.muscleGroupId];
      if (!key) return;
      raw[key] = (raw[key] || 0) + volume * (mm.activationLevel / 100);
    });
  });

  const maxVal = Math.max(...Object.values(raw), 1);
  const result = { ...DEFAULT_ACTIVATION };
  Object.entries(raw).forEach(([key, val]) => {
    (result as any)[key] = Math.min(val / maxVal, 1);
  });

  result.midChest = Math.max(result.midChest, result.innerChest * 0.7);
  result.midTraps = Math.max(result.midTraps, result.rhomboids * 0.8);
  result.lats = Math.max(result.lats, result.teresMajor * 0.6);
  result.sideDelt = Math.max(result.sideDelt, result.rotatorCuff * 0.5);

  return result;
}

// ============================================================
// GLSL Shaders
// ============================================================

const vertexShader = /* glsl */ `
varying vec3 vPos;
varying vec3 vWorldNormal;

void main() {
  vPos = (modelMatrix * vec4(position, 1.0)).xyz;
  vWorldNormal = normalize(normalMatrix * normal);
  gl_Position = projectionMatrix * viewMatrix * modelMatrix * vec4(position, 1.0);
}
`;

const fragmentShader = /* glsl */ `
precision highp float;

varying vec3 vPos;
varying vec3 vWorldNormal;

uniform float uUpperChest;
uniform float uMidChest;
uniform float uLowerChest;
uniform float uLats;
uniform float uUpperTraps;
uniform float uMidTraps;
uniform float uLowerTraps;
uniform float uFrontDelt;
uniform float uSideDelt;
uniform float uRearDelt;
uniform float uBiceps;
uniform float uTriceps;
uniform float uForearms;
uniform float uQuads;
uniform float uHamstrings;
uniform float uGlutes;
uniform float uGluteMed;
uniform float uUpperAbs;
uniform float uLowerAbs;
uniform float uObliques;
uniform float uErectors;
uniform float uCalves;

uniform float uBaseGray;
uniform float uHeatIntensity;
uniform float uTime;

const vec3 COL_CHEST    = vec3(0.95, 0.22, 0.22);
const vec3 COL_BACK     = vec3(0.24, 0.52, 1.00);
const vec3 COL_SHOULDER = vec3(0.96, 0.62, 0.07);
const vec3 COL_BICEPS   = vec3(0.55, 0.36, 0.98);
const vec3 COL_TRICEPS  = vec3(0.42, 0.26, 0.80);
const vec3 COL_FOREARM  = vec3(0.38, 0.22, 0.68);
const vec3 COL_ABS      = vec3(0.04, 0.78, 0.78);
const vec3 COL_ERECTOR  = vec3(0.04, 0.62, 0.72);
const vec3 COL_QUAD     = vec3(0.20, 0.78, 0.47);
const vec3 COL_HAM      = vec3(0.12, 0.62, 0.35);
const vec3 COL_GLUTE    = vec3(0.16, 0.70, 0.42);
const vec3 COL_CALF     = vec3(0.22, 0.58, 0.42);

float band(float v, float lo, float hi, float e) {
  return smoothstep(lo - e, lo + e, v) * (1.0 - smoothstep(hi - e, hi + e, v));
}
float gt(float v, float t, float e) { return smoothstep(t - e, t + e, v); }
float lt(float v, float t, float e) { return 1.0 - smoothstep(t - e, t + e, v); }

void main() {
  // 압축 후 좌표계: Y=높이(0~1.83), X=좌우(+-0.71), Z=앞뒤(+-0.21)
  float h  = vPos.z;
  float lr = abs(vPos.x);
  float fb = vPos.y;

  vec3 lightDir = normalize(vec3(0.3, 0.8, 0.5));
  float lighting = 0.35 + max(dot(vWorldNormal, lightDir), 0.0) * 0.65;
  vec3 baseColor = vec3(uBaseGray) * lighting;

  float totalW = 0.0;
  vec3  totalC = vec3(0.0);
  float w;

  // ── 가슴 (전면) ──
  float chestMask = gt(fb, -1.0, 2.5) * lt(lr, 22.0, 3.0);
  w = band(h, -40.0, -25.0, 3.0) * chestMask * uUpperChest;
  totalC += COL_CHEST * w; totalW += w;
  w = band(h, -52.0, -36.0, 3.0) * chestMask * uMidChest;
  totalC += COL_CHEST * 0.88 * w; totalW += w;
  w = band(h, -62.0, -47.0, 3.0) * chestMask * uLowerChest;
  totalC += COL_CHEST * 0.75 * w; totalW += w;

  // ── 어깨 ──
  float shMask = band(h, -50.0, -18.0, 3.0) * gt(lr, 16.0, 3.0);
  w = shMask * gt(fb, -1.0, 3.0) * uFrontDelt;
  totalC += COL_SHOULDER * w; totalW += w;
  w = shMask * band(fb, -5.0, 1.0, 3.0) * uSideDelt;
  totalC += COL_SHOULDER * 0.85 * w; totalW += w;
  w = shMask * lt(fb, -1.0, 3.0) * uRearDelt;
  totalC += COL_SHOULDER * 0.70 * w; totalW += w;

  // ── 등 ──
  float backM = lt(fb, 0.0, 3.0);
  w = band(h, -37.0, -18.0, 3.0) * backM * lt(lr, 16.0, 3.0) * uUpperTraps;
  totalC += COL_BACK * 1.1 * w; totalW += w;
  w = band(h, -52.0, -28.0, 3.0) * backM * lt(lr, 16.0, 3.0) * uMidTraps;
  totalC += COL_BACK * 0.9 * w; totalW += w;
  w = band(h, -64.0, -44.0, 3.0) * backM * lt(lr, 14.0, 3.0) * uLowerTraps;
  totalC += COL_BACK * 0.78 * w; totalW += w;
  w = band(h, -80.0, -33.0, 4.0) * backM * band(lr, 6.0, 26.0, 4.0) * uLats;
  totalC += COL_BACK * w; totalW += w;

  // ── 팔 ──
  float armM = gt(lr, 22.0, 3.0);
  w = band(h, -94.0, -40.0, 4.0) * armM * gt(fb, -3.0, 4.0) * uBiceps;
  totalC += COL_BICEPS * w; totalW += w;
  w = band(h, -94.0, -40.0, 4.0) * armM * lt(fb, 3.0, 4.0) * uTriceps;
  totalC += COL_TRICEPS * w; totalW += w;
  w = lt(h, -86.0, 5.0) * gt(lr, 25.0, 3.0) * uForearms;
  totalC += COL_FOREARM * w; totalW += w;

  // ── 복근/코어 ──
  float absM = gt(fb, -1.0, 3.0);
  w = band(h, -76.0, -53.0, 3.0) * absM * lt(lr, 14.0, 3.0) * uUpperAbs;
  totalC += COL_ABS * w; totalW += w;
  w = band(h, -92.0, -70.0, 3.0) * absM * lt(lr, 14.0, 3.0) * uLowerAbs;
  totalC += COL_ABS * 0.85 * w; totalW += w;
  w = band(h, -92.0, -53.0, 3.0) * absM * band(lr, 10.0, 24.0, 3.0) * uObliques;
  totalC += COL_ABS * 0.70 * w; totalW += w;
  w = band(h, -88.0, -36.0, 4.0) * backM * lt(lr, 9.0, 3.0) * uErectors;
  totalC += COL_ERECTOR * w; totalW += w;

  // ── 하체 ──
  w = band(h, -114.0, -80.0, 4.0) * lt(fb, 3.0, 4.0) * lt(lr, 22.0, 3.0) * uGlutes;
  totalC += COL_GLUTE * w; totalW += w;
  w = band(h, -104.0, -78.0, 3.0) * band(lr, 14.0, 26.0, 3.0) * uGluteMed;
  totalC += COL_GLUTE * 0.8 * w; totalW += w;
  w = band(h, -154.0, -96.0, 5.0) * gt(fb, -4.0, 4.0) * lt(lr, 22.0, 3.0) * uQuads;
  totalC += COL_QUAD * w; totalW += w;
  w = band(h, -154.0, -96.0, 5.0) * lt(fb, 4.0, 4.0) * lt(lr, 22.0, 3.0) * uHamstrings;
  totalC += COL_HAM * w; totalW += w;
  w = lt(h, -146.0, 5.0) * lt(lr, 18.0, 3.0) * uCalves;
  totalC += COL_CALF * w; totalW += w;

  // ── 합성 ──
  vec3 finalColor = baseColor;
  if (totalW > 0.01) {
    vec3 heat = totalC / totalW;
    float pulse = 1.0 + sin(uTime * 1.8) * 0.06 * totalW;
    float blend = clamp(totalW * uHeatIntensity * pulse, 0.0, 0.92);
    finalColor = mix(baseColor, heat * 1.25, blend);
    finalColor += heat * blend * 0.12;
  }
  finalColor = pow(finalColor, vec3(1.0 / 2.2));
  gl_FragColor = vec4(finalColor, 1.0);
}
`;

// ============================================================
// Uniform 키 목록
// ============================================================
const UNIFORM_KEYS: { uniform: string; key: keyof MuscleActivation }[] = [
  { uniform: "uUpperChest", key: "upperChest" },
  { uniform: "uMidChest", key: "midChest" },
  { uniform: "uLowerChest", key: "lowerChest" },
  { uniform: "uLats", key: "lats" },
  { uniform: "uUpperTraps", key: "upperTraps" },
  { uniform: "uMidTraps", key: "midTraps" },
  { uniform: "uLowerTraps", key: "lowerTraps" },
  { uniform: "uFrontDelt", key: "frontDelt" },
  { uniform: "uSideDelt", key: "sideDelt" },
  { uniform: "uRearDelt", key: "rearDelt" },
  { uniform: "uBiceps", key: "biceps" },
  { uniform: "uTriceps", key: "triceps" },
  { uniform: "uForearms", key: "forearms" },
  { uniform: "uQuads", key: "quads" },
  { uniform: "uHamstrings", key: "hamstrings" },
  { uniform: "uGlutes", key: "glutes" },
  { uniform: "uGluteMed", key: "gluteMed" },
  { uniform: "uUpperAbs", key: "upperAbs" },
  { uniform: "uLowerAbs", key: "lowerAbs" },
  { uniform: "uObliques", key: "obliques" },
  { uniform: "uErectors", key: "erectors" },
  { uniform: "uCalves", key: "calves" },
];

// ============================================================
// React Component
// ============================================================

interface MuscleHeatmapModelProps {
  modelPath: string;
  activation?: Partial<MuscleActivation>;
  baseGray?: number;
  heatIntensity?: number;
  animate?: boolean;
  scale?: number;
  position?: [number, number, number];
}

export function MuscleHeatmapModel({
                                     modelPath,
                                     activation = {},
                                     baseGray = 0.50,
                                     heatIntensity = 0.85,
                                     animate = true,
                                     scale = 0.1,
                                     position = [0, 0, 0],
                                   }: MuscleHeatmapModelProps) {
  const { scene } = useGLTF(modelPath);
  const shaderRef = useRef<THREE.ShaderMaterial | null>(null);

  const act = useMemo(() => ({ ...DEFAULT_ACTIVATION, ...activation }), [activation]);

  type ShaderUniforms = Record<string, THREE.IUniform<number>>;

  const shaderMaterial = useMemo(() => {
    const uniforms: ShaderUniforms = {
      uBaseGray: { value: baseGray },
      uHeatIntensity: { value: heatIntensity },
      uTime: { value: 0 },
    };
    UNIFORM_KEYS.forEach(({ uniform }) => {
      uniforms[uniform] = { value: 0 };
    });
    const mat = new THREE.ShaderMaterial({ vertexShader, fragmentShader, uniforms });
    shaderRef.current = mat;
    return mat;
  }, []);

  useEffect(() => {
    const mat = shaderRef.current;
    if (!mat) return;
    const u = mat.uniforms as ShaderUniforms;
    UNIFORM_KEYS.forEach(({ uniform, key }) => {
      u[uniform].value = act[key];
    });
    u.uBaseGray.value = baseGray;
    u.uHeatIntensity.value = heatIntensity;
  }, [act, baseGray, heatIntensity]);

  useEffect(() => {
    scene.traverse((obj: any) => {
      if (!obj.isMesh) return;
      const name = (obj.name || "").toLowerCase();

      if (name.includes("body") && obj.geometry?.attributes?.position?.count > 10000) {
        // 디버그: 좌표 범위 출력
        const pos = obj.geometry.attributes.position;
        let minX = Infinity, maxX = -Infinity;
        let minY = Infinity, maxY = -Infinity;
        let minZ = Infinity, maxZ = -Infinity;
        for (let i = 0; i < pos.count; i++) {
          const x = pos.getX(i), y = pos.getY(i), z = pos.getZ(i);
          if (x < minX) minX = x; if (x > maxX) maxX = x;
          if (y < minY) minY = y; if (y > maxY) maxY = y;
          if (z < minZ) minZ = z; if (z > maxZ) maxZ = z;
        }
        console.log("BODY BOUNDS:", { minX, maxX, minY, maxY, minZ, maxZ });

        obj.material = shaderMaterial;
      }

      obj.castShadow = true;
      obj.receiveShadow = true;
    });
  }, [scene, shaderMaterial]);

  useFrame((_, delta) => {
    const mat = shaderRef.current;
    if (animate && mat !== null) {
      (mat.uniforms as ShaderUniforms).uTime.value += delta;
    }
  });

  return <primitive object={scene} scale={scale} position={position} />;
}

// ============================================================
// 디버그 프리셋
// ============================================================

export const DEBUG_ALL_ACTIVE: MuscleActivation = {
  upperChest: 1, midChest: 1, lowerChest: 1, innerChest: 1,
  lats: 1, upperTraps: 1, midTraps: 1, lowerTraps: 1, rhomboids: 1, teresMajor: 1,
  frontDelt: 1, sideDelt: 1, rearDelt: 1, rotatorCuff: 1,
  biceps: 1, triceps: 1, forearms: 1,
  quads: 1, hamstrings: 1, glutes: 1, gluteMed: 1,
  upperAbs: 1, lowerAbs: 1, obliques: 1, erectors: 1,
  calves: 1,
};

export const DEBUG_CHEST_DAY: Partial<MuscleActivation> = {
  upperChest: 0.9, midChest: 0.7, lowerChest: 0.5, triceps: 0.4, frontDelt: 0.3,
};

export const DEBUG_BACK_DAY: Partial<MuscleActivation> = {
  lats: 0.9, midTraps: 0.7, upperTraps: 0.5, lowerTraps: 0.4,
  biceps: 0.5, rearDelt: 0.3, erectors: 0.4,
};

export const DEBUG_LEG_DAY: Partial<MuscleActivation> = {
  quads: 0.9, hamstrings: 0.7, glutes: 0.8, gluteMed: 0.4, calves: 0.3,
};

export default MuscleHeatmapModel;