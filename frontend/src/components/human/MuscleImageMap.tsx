// src/components/Human/MuscleImageMap.tsx
// 운동 세션의 근육 매핑 데이터를 받아 해당 근육 이미지를 표시하는 컴포넌트
import { useMemo } from "react";

// ============================================================
// muscleGroupId → 이미지 파일 매핑
// ============================================================

interface MuscleImageInfo {
    file: string;   // /muscles/ 아래 파일명
    label: string;  // 한글 표시명
}

const MUSCLE_IMAGE_MAP: Record<number, MuscleImageInfo> = {
    // 가슴
    101: { file: "chest.jpg", label: "가슴" },
    102: { file: "chest.jpg", label: "가슴" },
    103: { file: "chest.jpg", label: "가슴" },
    104: { file: "chest.jpg", label: "가슴" },
    // 등
    201: { file: "lats.jpg", label: "광배근" },
    202: { file: "traps.jpg", label: "승모근" },
    203: { file: "traps_back.jpg", label: "승모근(후면)" },
    204: { file: "traps_back.jpg", label: "승모하부" },
    205: { file: "lats.jpg", label: "능형근" },
    206: { file: "lats.jpg", label: "대원근" },
    // 어깨
    301: { file: "shoulders.jpg", label: "전면삼각" },
    302: { file: "shoulders.jpg", label: "측면삼각" },
    303: { file: "shoulders.jpg", label: "후면삼각" },
    304: { file: "shoulders.jpg", label: "회전근개" },
    // 팔
    4: { file: "biceps.jpg", label: "이두" },
    5: { file: "triceps.jpg", label: "삼두" },
    6: { file: "forearms.jpg", label: "전완" },
    // 하체
    7: { file: "quads.jpg", label: "대퇴사두" },
    8: { file: "hamstrings.jpg", label: "햄스트링" },
    901: { file: "glutes.jpg", label: "대둔근" },
    902: { file: "glutes.jpg", label: "중둔근" },
    // 복근/코어
    1101: { file: "abdominals.jpg", label: "상복부" },
    1102: { file: "abdominals.jpg", label: "하복부" },
    1103: { file: "obliques.jpg", label: "복사근" },
    12: { file: "lower_back.jpg", label: "척추기립근" },
    // 종아리
    13: { file: "calves.jpg", label: "종아리" },
};

// ============================================================
// 타입
// ============================================================

interface MuscleMapping {
    muscleGroupId: number;
    muscleName: string;
    role: "PRIMARY" | "SECONDARY" | "SYNERGIST";
    activationLevel: number;
}

interface MuscleImageMapProps {
    /** 세션 내 모든 운동의 muscleMappings를 flat하게 전달 */
    muscleMappings: MuscleMapping[];
    /** 이미지 최대 높이 (기본 200px) */
    imageHeight?: number;
}

// ============================================================
// 컴포넌트
// ============================================================

export default function MuscleImageMap({
                                           muscleMappings,
                                           imageHeight = 200,
                                       }: MuscleImageMapProps) {
    // 중복 제거: 같은 이미지 파일은 한 번만, PRIMARY 우선 정렬
    const uniqueImages = useMemo(() => {
        const seen = new Map<string, { file: string; label: string; isPrimary: boolean }>();

        muscleMappings.forEach((mm) => {
            const info = MUSCLE_IMAGE_MAP[mm.muscleGroupId];
            if (!info) return;

            const existing = seen.get(info.file);
            if (!existing) {
                seen.set(info.file, {
                    file: info.file,
                    label: info.label,
                    isPrimary: mm.role === "PRIMARY",
                });
            } else if (mm.role === "PRIMARY" && !existing.isPrimary) {
                seen.set(info.file, { ...existing, isPrimary: true });
            }
        });

        // PRIMARY 먼저, 그 다음 SECONDARY
        return Array.from(seen.values()).sort((a, b) =>
            a.isPrimary === b.isPrimary ? 0 : a.isPrimary ? -1 : 1
        );
    }, [muscleMappings]);

    if (uniqueImages.length === 0) return null;

    return (
        <div className="muscle-image-map">
            <div className="muscle-image-scroll">
                {uniqueImages.map((img) => (
                    <div
                        key={img.file}
                        className={`muscle-image-card ${img.isPrimary ? "primary" : "secondary"}`}
                    >
                        <img
                            src={`/muscles/${img.file}`}
                            alt={img.label}
                            style={{ height: `${imageHeight}px` }}
                            loading="lazy"
                        />
                        <span className="muscle-image-label">{img.label}</span>
                    </div>
                ))}
            </div>
        </div>
    );
}