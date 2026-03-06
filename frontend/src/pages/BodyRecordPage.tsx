import { useState, useMemo, useEffect, useRef } from "react";
import { useBodyRecords } from "@/hooks/useBodyRecords";

import BodyMetricCards from "@/components/BodyMetricCards";
import BodyMetricChart from "@/components/BodyMetricChart";
import HumanModelView from "@/components/Human/HumanModelView";

import type { MetricKey } from "@/types/MetricKey";
import type { BodySummaryRecord } from "@/types/BodySummaryRecord";
import type { AiProvider } from "@/types/AiProvider";

import "@/styles/global.css";
import "@/styles/body-record.css"

export default function BodyRecordPage() {
    const { records, refetch } = useBodyRecords();
    const fileInputRef = useRef<HTMLInputElement>(null);
    const [uploading, setUploading] = useState(false);

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

    /* 4️⃣ AI 모델 */
    const [selectedProvider, setSelectedProvider] = useState<AiProvider>("openai");

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

    const handleDateClick = (recordDate: string) => {
        const index = sortedRecords.findIndex(r => r.recordDate === recordDate);
        if (index !== -1) {
            setCurrentIndex(index);
        }
    };

    /* 📤 인바디 업로드 */
    const handleUploadClick = () => {
        fileInputRef.current?.click();
    };

    const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        if (!file) return;

        setUploading(true);
        const formData = new FormData();
        formData.append("file", file);

        try {
            const res = await fetch("/api/body/inbody/upload", {
                method: "POST",
                body: formData,
            });
            const data = await res.json();

            if (data.success) {
                alert("업로드 완료! 잠시 후 데이터가 반영됩니다.");
                // 몇 초 후 데이터 새로고침
                setTimeout(() => refetch?.(), 5000);
            } else {
                alert("업로드 실패: " + data.message);
            }
        } catch (err) {
            alert("업로드 중 오류가 발생했습니다.");
        } finally {
            setUploading(false);
            if (fileInputRef.current) {
                fileInputRef.current.value = "";
            }
        }
    };

    const AI_PROVIDER_LABEL = import.meta.env.VITE_AI_PROVIDER_LABEL ?? {
        openai: "OpenAI",
        geminai: "Gemeni",
        claude: "Claude"
    };

    return (
        <div className="body-page">
            {/* 🔝 컬럼별 헤더 */}
            <div className="column-header-grid">
                {/* 좌측 헤더 */}
                <h2 className="column-title">
                    📊 신체 변화 요약
                </h2>

                {/* 우측 헤더 */}
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
                    {/* 📤 인바디 업로드 버튼 */}
                    <button
                        className="inbody-upload-btn"
                        onClick={handleUploadClick}
                        disabled={uploading}
                    >
                        {uploading ? "업로드 중..." : "📷 인바디 기록 업로드"}
                    </button>
                    <input
                        type="file"
                        ref={fileInputRef}
                        onChange={handleFileChange}
                        accept="image/*"
                        style={{ display: "none" }}
                    />

                    <BodyMetricCards
                        latest={current}
                        selected={selectedMetric}
                        onSelect={setSelectedMetric}
                    />

                    <BodyMetricChart
                        data={sortedRecords}
                        metricKey={selectedMetric}
                        onDateClick={handleDateClick}
                    />

                    <div className="ai-radio-group">
                        {Object.entries(AI_PROVIDER_LABEL).map(([key, label]) => (
                            <label key={key} className="ai-radio-item">
                                <input
                                    type="radio"
                                    name="ai-provider"
                                    value={key}
                                    checked={selectedProvider === key}
                                    onChange={() => setSelectedProvider(key as AiProvider)}
                                />
                                <span>{label as string}</span>
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
                            <p className="estimate-note">x
                                ※ 치수 정보는 추정값을 기반으로 시각화되었습니다.
                            </p>
                        )}
                    </div>
                </div>

                <div className="body-right">
                    <h3 className="section-title">체형 시각화</h3>
                    <HumanModelView summary={current}/>
                </div>
            </div>
        </div>
    );
}
