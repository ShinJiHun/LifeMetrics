import type { BodyType } from "@/components/human/human.types.ts";

export interface BodyTypeDescription {
    title: string;        // UI 제목
    subtitle: string;     // 한 줄 요약
    features: string[];  // 특징 bullet
    caution?: string;    // 주의사항 (선택)
}

export const BODY_TYPE_DESCRIPTIONS: Record<BodyType, BodyTypeDescription> = {
    lean: {
        title: "마른 근육형",
        subtitle: "체지방은 낮고 근육량도 적은 체형",
        features: [
            "체중 대비 체지방률이 낮은 편",
            "골격근량이 평균 이하",
            "에너지 소모 대비 섭취가 부족한 경우가 많음",
        ],
        caution: "무리한 다이어트보다는 근육량 증가가 우선입니다.",
    },

    normal: {
        title: "표준 혼합형",
        subtitle: "지방과 근육이 비교적 균형된 체형",
        features: [
            "체지방과 골격근량이 평균 범위",
            "생활습관에 따라 체형 변화가 빠름",
            "관리 여부에 따라 지방/근육형으로 쉽게 이동",
        ],
    },

    fit: {
        title: "근육 우세형",
        subtitle: "근육량이 충분하고 체지방이 관리되는 체형",
        features: [
            "골격근량이 평균 이상",
            "체지방률이 비교적 안정적",
            "운동 반응성이 좋은 상태",
        ],
    },

    athlete: {
        title: "근육 포화형",
        subtitle: "근육량이 상한선에 가까운 체형",
        features: [
            "골격근량이 매우 높은 편",
            "기초대사량이 높음",
            "운동 강도에 따라 피로 누적 가능",
        ],
        caution: "회복과 휴식 관리가 중요합니다.",
    },

    overweight: {
        title: "지방 우세형",
        subtitle: "근육 대비 체지방이 많은 체형",
        features: [
            "체지방률이 평균 이상",
            "골격근량은 유지되나 지방 비율이 큼",
            "식단 영향도가 매우 큰 상태",
        ],
    },

    obese: {
        title: "지방 포화형",
        subtitle: "체지방 비율이 매우 높은 체형",
        features: [
            "체지방률이 높은 수준",
            "내장지방 관리가 필요",
            "운동보다는 식단 조절의 영향이 큼",
        ],
        caution: "무리한 운동보다는 단계적 감량이 중요합니다.",
    },
};
