import { useState, useMemo, useEffect } from "react";
import { useBodyRecords } from "@/hooks/useBodyRecords";

import BodyMetricCards from "@/components/BodyMetricCards";
import BodyMetricChart from "@/components/BodyMetricChart";
import HumanModelView from "@/components/Human/HumanModelView";

import SecretLottoPanel from "@/components/SecretLottoPanel";
import { useSecretToggle } from "@/hooks/useSecretToggle";

import type { MetricKey } from "@/types/MetricKey";
import type { BodySummaryRecord } from "@/types/BodySummaryRecord";
import { AiProvider, AI_PROVIDER_LABEL } from "@/types/AiProvider";

import "@/styles/body-record.css";

export default function BodyRecordPage() {
    const { records } = useBodyRecords();

    /* 🕵️‍♂️ SECRET LOTTO */
    const showSecret = true;
    const [openSecret, setOpenSecret] = useState(false);

    /* 1️⃣ 날짜 기준 정렬 */
    const sortedRecords = useMemo<BodySummaryRecord[]>(() => {
        return [...records].sort(
            (a, b) =>
                new Date(a.recordDate).getTime() -
                new Date(b.recordDate).getTime()
        );
    }, [records]);

    /* 2️⃣ 선택 지표 */
    const [selectedMetric, setSelectedMetric] =
        useState<MetricKey>("weight");

    /* 3️⃣ 날짜 인덱스 */
    const [currentIndex, setCurrentIndex] = useState(0);

    /* 4️⃣ AI Provider */
    const [selectedProvider, setSelectedProvider] =
        useState<AiProvider>("openai");

    useEffect(() => {
        if (sortedRecords.length > 0) {
            setCurrentIndex(sortedRecords.length - 1);
        }
    }, [sortedRecords]);

    const current = sortedRecords[currentIndex];
    if (!current) return null;

    const goPrev = () => setCurrentIndex(i => Math.max(0, i - 1));
    const goNext = () =>
        setCurrentIndex(i => Math.min(sortedRecords.length - 1, i + 1));

    return (
        <div className="body-page">
            {/* 🔝 헤더 */}
            <div className="column-header-grid">
                <h2 className="column-title">📊 신체 변화 요약</h2>

                <div className="column-date">
                    <button onClick={goPrev} disabled={currentIndex === 0}>◀</button>
                    <span>{current.recordDate}</span>
                    <button
                        onClick={goNext}
                        disabled={currentIndex === sortedRecords.length - 1}
                    >
                        ▶
                    </button>
                </div>
            </div>

            <div className="body-split-layout">
                <div className="body-left">
                    <BodyMetricCards
                        latest={current}
                        selected={selectedMetric}
                        onSelect={setSelectedMetric}
                    />

                    <BodyMetricChart
                        data={sortedRecords}
                        metricKey={selectedMetric}
                        title={selectedMetric}
                    />

                    {/* 🤖 AI 선택 */}
                    <div className="ai-radio-group">
                        {(Object.keys(AI_PROVIDER_LABEL) as AiProvider[]).map(provider => (
                            <label key={provider} className="ai-radio-item">
                                <input
                                    type="radio"
                                    name="ai-provider"
                                    checked={selectedProvider === provider}
                                    onChange={() => setSelectedProvider(provider)}
                                />
                                <span>{AI_PROVIDER_LABEL[provider]}</span>
                            </label>
                        ))}
                    </div>

                    <div className="summary-text-box">
                        <p>
                            <strong>[{AI_PROVIDER_LABEL[selectedProvider]} 분석]</strong>
                            {current.recordDate} 기준 체중은
                            <strong> {current.weight}kg</strong> 입니다.
                        </p>
                        <p>
                            체지방률은 <strong>{current.bodyFatPercentage}%</strong>,
                            내장지방 레벨은{" "}
                            <strong>{current.visceralFatLevel ?? "측정 없음"}</strong> 입니다.
                        </p>

                        {!current.isMeasured && (
                            <p className="estimate-note">
                                ※ 치수 정보는 추정값을 기반으로 시각화되었습니다.
                            </p>
                        )}
                    </div>
                </div>

                <div className="body-right">
                    <h3 className="section-title">체형 시각화</h3>
                    {false && <HumanModelView summary={current} />}
                </div>
            </div>

            <div
                onClick={() => setOpenSecret(o => !o)}
                style={{
                    position: "fixed",
                    bottom: 0,
                    right: 0,
                    width: 18,
                    height: 18,
                    opacity: 0,
                    zIndex: 9999,
                    cursor: "pointer",
                }}
            />
        </div>
    );
}
