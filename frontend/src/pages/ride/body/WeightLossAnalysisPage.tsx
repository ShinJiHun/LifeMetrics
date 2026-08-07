import { useEffect, useState } from "react";
import { fetchWeightLossAnalysis, fetchWeightLossNarrative } from "@/api/body";
import type { WeightLossAnalysis } from "@/types/WeightLossAnalysis";

import "@/styles/global.css";
import "@/styles/weight-loss.css";

/** 측정이 오래되면 체성분이 달라졌을 수 있어 화면에서 경고한다. */
const STALE_DAYS = 30;

export default function WeightLossAnalysisPage() {
    const [data, setData] = useState<WeightLossAnalysis | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [narrative, setNarrative] = useState<string | null>(null);
    const [aiState, setAiState] = useState<"idle" | "loading" | "error">("idle");
    const [aiError, setAiError] = useState<string | null>(null);

    useEffect(() => {
        fetchWeightLossAnalysis()
            .then(setData)
            .catch((e: Error) => setError(e.message));
    }, []);

    const runAi = async () => {
        setAiState("loading");
        setAiError(null);
        try {
            setNarrative(await fetchWeightLossNarrative());
            setAiState("idle");
        } catch (e) {
            setAiError((e as Error).message);
            setAiState("error");
        }
    };

    if (error) return <div className="wl-page"><div className="wl-state">{error}</div></div>;
    if (!data) return <div className="wl-page"><div className="wl-state">불러오는 중…</div></div>;

    const primary = data.scenarios.find((s) => s.primary) ?? data.scenarios[0];
    const stale = data.recordAgeDays > STALE_DAYS;

    return (
        <div className="wl-page">
            <h1>감량 분석</h1>
            <div className="wl-subtitle">
                기초대사량만큼 먹는다는 가정에서 체지방 1kg 감량에 필요한 라이딩 양
            </div>

            {stale && (
                <div className="wl-notes wl-warn">
                    최신 인바디 측정이 {data.recordAgeDays}일 전({data.recordDate})입니다.
                    그동안 체성분이 달라졌다면 아래 수치도 함께 어긋납니다.
                </div>
            )}

            <div className="wl-headline">
                <div className="wl-headline-label">
                    체지방 1kg 감량에 필요한 라이딩 — {primary.label}
                </div>
                <div className="wl-headline-value">
                    <strong>{primary.hoursPerKg}</strong>
                    <span className="wl-card-unit">시간</span>
                    <span className="wl-headline-sep">/</span>
                    <strong>{primary.kmPerKg.toLocaleString()}</strong>
                    <span className="wl-card-unit">km</span>
                </div>
                <div className="wl-headline-note">
                    시간당 {primary.kcalPerHour} kcal 소비, 평균 {data.avgSpeedKmh} km/h 기준.
                    체지방 1kg = 7,700 kcal 로 계산했습니다.
                </div>
            </div>

            <div className="wl-section-title">체성분 — {data.recordDate} 인바디</div>
            <div className="wl-grid">
                <Card title="체중" value={data.weight} unit="kg" />
                <Card title="체지방량" value={data.bodyFatMass} unit="kg" />
                <Card title="체지방률" value={data.bodyFatPercentage} unit="%" />
                <Card title="제지방량" value={data.fatFreeMass} unit="kg" />
                <Card
                    title={data.bmrEstimated ? "기초대사량 (계산)" : "기초대사량"}
                    value={data.basalMetabolicRate}
                    unit="kcal"
                />
            </div>

            <div className="wl-section-title">
                라이딩 소비 — 파워미터 기록 {data.rideCount}회
            </div>
            <div className="wl-grid">
                <Card title="집계 시간" value={data.rideHours} unit="h" />
                <Card title="집계 거리" value={data.rideKm} unit="km" />
                <Card title="평균 속도" value={data.avgSpeedKmh} unit="km/h" />
                <Card title="시간당 일" value={data.kjPerHour} unit="kJ/h" />
            </div>

            <div className="wl-section-title">소비 추정 방식별 비교</div>
            <div className="wl-table-wrap">
                <table className="wl-table">
                    <thead>
                    <tr>
                        <th>추정 방식</th>
                        <th>kcal/h</th>
                        <th>필요 시간</th>
                        <th>필요 거리</th>
                    </tr>
                    </thead>
                    <tbody>
                    {data.scenarios.map((s) => (
                        <tr key={s.label} className={s.primary ? "primary" : undefined}>
                            <td>
                                {s.label}
                                <span className="wl-basis">{s.basis}</span>
                            </td>
                            <td>{s.kcalPerHour.toLocaleString()}</td>
                            <td>{s.hoursPerKg} h</td>
                            <td>{s.kmPerKg.toLocaleString()} km</td>
                        </tr>
                    ))}
                    </tbody>
                </table>
            </div>

            <div className="wl-section-title">AI 코칭</div>
            <div className="wl-ai">
                {narrative ? (
                    <p className="wl-ai-text">{narrative}</p>
                ) : (
                    <>
                        <button
                            className="wl-ai-btn"
                            onClick={runAi}
                            disabled={aiState === "loading"}
                        >
                            {aiState === "loading" ? "분석 중…" : "🤖 AI 분석 실행"}
                        </button>
                        <span className="wl-ai-hint">
                            위 수치를 근거로 코칭 의견을 생성합니다.
                        </span>
                    </>
                )}
                {aiError && <div className="wl-ai-error">{aiError}</div>}
            </div>

            <div className="wl-notes">
                읽을 때 주의할 점
                <ul>
                    <li>
                        <strong>라이딩 기여분만</strong> 계산한 값입니다. 기초대사량만 먹으면
                        라이딩을 안 해도 일상 활동만으로 하루 약 {data.dailyDeficitKcal} kcal
                        적자라, 그것만으로 {data.dailyDeficitDaysPerKg}일이면 1kg이 빠집니다.
                    </li>
                    <li>
                        <strong>체중계 1kg ≠ 체지방 1kg</strong>입니다. 글리코겐과 수분만으로
                        1~2kg가 움직여 단기 체중 변화로는 검증되지 않습니다.
                    </li>
                    <li>
                        단순 에너지수지 모델이라 대사 적응이나 회복 저하는 반영하지 않습니다.
                        기초대사량만 먹으면서 장시간 타는 건 상당히 큰 적자이니, 실제로 진행하실
                        거면 전문가와 상의하시길 권합니다.
                    </li>
                </ul>
            </div>
        </div>
    );
}

function Card({ title, value, unit }: { title: string; value: number; unit: string }) {
    return (
        <div className="wl-card">
            <div className="wl-card-title">{title}</div>
            <div className="wl-card-value">
                {value.toLocaleString()}
                <span className="wl-card-unit">{unit}</span>
            </div>
        </div>
    );
}
