import api from "@/lib/axios";

/** 건강검진 1건. 서버 HealthCheckupDto 와 1:1. 대부분 nullable. */
export interface HealthCheckup {
    id?: number;

    checkupDate: string | null; // "YYYY-MM-DD"
    checkupPlace: string | null;
    checkupOrg: string | null;
    checkupDoctor: string | null;
    overallJudgment: string | null;
    extraExams: string | null;
    suspectedDisease: string | null;
    existingDisease: string | null;
    lifestyleAdvice: string | null;
    etcAdvice: string | null;

    heightCm: number | null;
    weightKg: number | null;
    bmi: number | null;
    bmiGrade: string | null;
    waistCm: number | null;
    waistResult: string | null;
    visionLeft: number | null;
    visionRight: number | null;
    visionCorrected: boolean | null;
    hearingLeft: string | null;
    hearingRight: string | null;
    hearingResult: string | null;

    systolicBp: number | null;
    diastolicBp: number | null;
    bpResult: string | null;

    hemoglobin: number | null;
    anemiaResult: string | null;
    fastingBloodSugar: number | null;
    diabetesResult: string | null;
    totalCholesterol: number | null;
    hdlCholesterol: number | null;
    triglyceride: number | null;
    ldlCholesterol: number | null;
    lipidResult: string | null;
    serumCreatinine: number | null;
    egfr: number | null;
    kidneyResult: string | null;
    ast: number | null;
    alt: number | null;
    ggt: number | null;
    liverResult: string | null;

    urineProteinResult: string | null;
    chestXrayResult: string | null;

    pastHistory: string | null;
    medication: string | null;
    needSmokingCessation: boolean | null;
    needDrinkingReduction: boolean | null;
    needPhysicalActivity: boolean | null;
    needStrengthExercise: boolean | null;

    hepBResult: string | null;
    hepCResult: string | null;
    depressionResult: string | null;
    depressionScore: number | null;
    psychosisResult: string | null;
    cognitiveResult: string | null;
    boneDensityResult: string | null;
    urinationResult: string | null;

    rawText: string | null;
    sourceFile: string | null;
    createdAt?: string;
    updatedAt?: string;
}

export function emptyHealthCheckup(): HealthCheckup {
    return {
        checkupDate: null, checkupPlace: null, checkupOrg: null, checkupDoctor: null,
        overallJudgment: null, extraExams: null, suspectedDisease: null, existingDisease: null,
        lifestyleAdvice: null, etcAdvice: null,
        heightCm: null, weightKg: null, bmi: null, bmiGrade: null, waistCm: null, waistResult: null,
        visionLeft: null, visionRight: null, visionCorrected: null,
        hearingLeft: null, hearingRight: null, hearingResult: null,
        systolicBp: null, diastolicBp: null, bpResult: null,
        hemoglobin: null, anemiaResult: null, fastingBloodSugar: null, diabetesResult: null,
        totalCholesterol: null, hdlCholesterol: null, triglyceride: null, ldlCholesterol: null, lipidResult: null,
        serumCreatinine: null, egfr: null, kidneyResult: null,
        ast: null, alt: null, ggt: null, liverResult: null,
        urineProteinResult: null, chestXrayResult: null,
        pastHistory: null, medication: null,
        needSmokingCessation: null, needDrinkingReduction: null, needPhysicalActivity: null, needStrengthExercise: null,
        hepBResult: null, hepCResult: null,
        depressionResult: null, depressionScore: null,
        psychosisResult: null, cognitiveResult: null, boneDensityResult: null, urinationResult: null,
        rawText: null, sourceFile: null,
    };
}

export async function fetchHealthCheckups(userId = 1): Promise<HealthCheckup[]> {
    const res = await api.get<HealthCheckup[]>("/body/health-checkups", { params: { userId } });
    return res.data;
}

export async function fetchHealthCheckup(id: number): Promise<HealthCheckup> {
    const res = await api.get<HealthCheckup>(`/body/health-checkups/${id}`);
    return res.data;
}

/** PDF 업로드 → AI 추출 결과(저장 안 됨). 폼 prefill 용. */
export async function extractHealthCheckupPdf(file: File): Promise<HealthCheckup> {
    const form = new FormData();
    form.append("file", file);
    const res = await api.post<HealthCheckup>("/body/health-checkups/extract", form, {
        headers: { "Content-Type": "multipart/form-data" },
    });
    return res.data;
}

export async function createHealthCheckup(payload: HealthCheckup, userId = 1): Promise<HealthCheckup> {
    const res = await api.post<HealthCheckup>("/body/health-checkups", payload, { params: { userId } });
    return res.data;
}

export async function updateHealthCheckup(id: number, payload: HealthCheckup): Promise<HealthCheckup> {
    const res = await api.put<HealthCheckup>(`/body/health-checkups/${id}`, payload);
    return res.data;
}

export async function deleteHealthCheckup(id: number): Promise<void> {
    await api.delete(`/body/health-checkups/${id}`);
}
