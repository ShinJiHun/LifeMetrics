import { useState, useMemo, useEffect, useRef } from "react";
import { useBodyRecords } from "@/hooks/useBodyRecords.ts";

import BodyMetricCards from "@/components/body/BodyMetricCards.tsx";
import BodyMetricChart from "@/components/body/BodyMetricChart.tsx";
import HumanModelView from "@/components/human/HumanModelView.tsx";
import LlmAnalysisModal from "@/components/body/LlmAnalysisModal.tsx";

import type { MetricKey } from "@/types/MetricKey.ts";
import type { BodySummaryRecord } from "@/types/BodySummaryRecord.ts";

import "@/styles/global.css";
import "@/styles/body-record.css";

type MeasurementTab = "FITDAYS" | "INBODY";

const TAB_LABELS: Record<MeasurementTab, string> = {
    FITDAYS: "⚖️ FitDays",
    INBODY: "📊 인바디",
};

const TAB_COLORS: Record<MeasurementTab, string> = {
    FITDAYS: "#10b981",
    INBODY: "#8b5cf6",
};

export default function BodyRecordPage() {
    const { records, refetch } = useBodyRecords();

    const fileInputRef = useRef<HTMLInputElement>(null);
    const [uploading, setUploading] = useState(false);
    const [uploadType, setUploadType] = useState<MeasurementTab>("FITDAYS");
    const [activeTab, setActiveTab] = useState<MeasurementTab>("FITDAYS");
    const [selectedMetric, setSelectedMetric] = useState<MetricKey>("weight");
    const [currentIndex, setCurrentIndex] = useState(0);
    const [showAnalysisModal, setShowAnalysisModal] = useState(false);

    // 탭에 따라 필터링
    const filteredRecords = useMemo<BodySummaryRecord[]>(() => {
        return [...records]
            .filter((r) => r.measurementType === activeTab)
            .sort(
                (a, b) =>
                    new Date(a.recordDate).getTime() - new Date(b.recordDate).getTime()
            );
    }, [records, activeTab]);

    useEffect(() => {
        if (filteredRecords.length > 0) {
            setCurrentIndex(filteredRecords.length - 1);
        }
    }, [filteredRecords]);

    const current = filteredRecords[currentIndex];

    const goPrev = () => setCurrentIndex((i) => Math.max(0, i - 1));
    const goNext = () =>
        setCurrentIndex((i) => Math.min(filteredRecords.length - 1, i + 1));

    const handleDateClick = (recordDate: string) => {
        const index = filteredRecords.findIndex((r) => r.recordDate === recordDate);
        if (index !== -1) setCurrentIndex(index);
    };

    const handleUploadClick = (type: MeasurementTab) => {
        setUploadType(type);
        setTimeout(() => fileInputRef.current?.click(), 0);
    };

    const handleFileChange = async (
        e: React.ChangeEvent<HTMLInputElement>
    ) => {
        const file = e.target.files?.[0];
        if (!file) return;

        setUploading(true);
        const formData = new FormData();
        formData.append("file", file);

        const endpoint =
            uploadType === "FITDAYS"
                ? "/api/body/fitdays/upload"
                : "/api/body/inbody/upload";

        if (uploadType === "INBODY") {
            formData.append("type", "INBODY");
        }

        try {
            const res = await fetch(endpoint, { method: "POST", body: formData });
            const data = await res.json();

            if (data.success) {
                if (uploadType === "FITDAYS") {
                    alert(
                        `FitDays 업로드 완료! ${data.recordDate} 체중 ${data.weight}kg 저장되었습니다.`
                    );
                    refetch?.();
                } else {
                    alert("인바디 업로드 완료! 잠시 후 데이터가 반영됩니다.");
                    setTimeout(() => refetch?.(), 5000);
                }
            } else {
                alert("업로드 실패: " + data.message);
            }
        } catch {
            alert("업로드 중 오류가 발생했습니다.");
        } finally {
            setUploading(false);
            if (fileInputRef.current) fileInputRef.current.value = "";
        }
    };

    return (
        <div className="body-page">
            <div className="column-header-grid">
                <h2 className="column-title">📊 신체 변화 요약</h2>
                {current && (
                    <div className="column-date">
                        <button onClick={goPrev} disabled={currentIndex === 0}>
                            ◀
                        </button>
                        <span>{current.recordDate}</span>
                        <button
                            onClick={goNext}
                            disabled={currentIndex === filteredRecords.length - 1}
                        >
                            ▶
                        </button>
                    </div>
                )}
            </div>

            {/* 측정 타입 탭 */}
            <div style={{ display: "flex", gap: 8, marginBottom: 16 }}>
                {(["FITDAYS", "INBODY"] as MeasurementTab[]).map((tab) => (
                    <button
                        key={tab}
                        onClick={() => setActiveTab(tab)}
                        style={{
                            padding: "6px 20px",
                            borderRadius: 20,
                            border: "2px solid",
                            borderColor:
                                activeTab === tab ? TAB_COLORS[tab] : "#e5e7eb",
                            background:
                                activeTab === tab ? TAB_COLORS[tab] : "#fff",
                            color: activeTab === tab ? "#fff" : "#6b7280",
                            fontWeight: activeTab === tab ? 700 : 400,
                            fontSize: 13,
                            cursor: "pointer",
                            transition: "all 0.2s",
                        }}
                    >
                        {TAB_LABELS[tab]}
                        <span
                            style={{
                                marginLeft: 6,
                                fontSize: 11,
                                opacity: 0.8,
                            }}
                        >
                            ({records.filter((r) => r.measurementType === tab).length})
                        </span>
                    </button>
                ))}
            </div>

            {!current ? (
                <div
                    style={{
                        padding: 40,
                        textAlign: "center",
                        color: "#6b7280",
                    }}
                >
                    {TAB_LABELS[activeTab]} 기록이 없습니다.
                </div>
            ) : (
                <div className="body-split-layout">
                    <div className="body-left">
                        {/* 업로드 버튼 */}
                        <div className="body-action-buttons">
                            <button
                                className="body-upload-btn"
                                onClick={() => handleUploadClick("FITDAYS")}
                                disabled={uploading}
                                style={{
                                    borderColor:
                                        uploading && uploadType === "FITDAYS"
                                            ? "#10b981"
                                            : undefined,
                                }}
                            >
                                {uploading && uploadType === "FITDAYS"
                                    ? "처리 중..."
                                    : "⚖️ FitDays 업로드"}
                            </button>
                            <button
                                className="body-upload-btn"
                                onClick={() => handleUploadClick("INBODY")}
                                disabled={uploading}
                                style={{
                                    borderColor:
                                        uploading && uploadType === "INBODY"
                                            ? "#8b5cf6"
                                            : undefined,
                                }}
                            >
                                {uploading && uploadType === "INBODY"
                                    ? "업로드 중..."
                                    : "📷 인바디 업로드"}
                            </button>
                        </div>

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
                            data={filteredRecords}
                            metricKey={selectedMetric}
                            measurementType={activeTab}
                            onDateClick={handleDateClick}
                        />

                        {/* AI 분석 모달 트리거 버튼 */}
                        <button
                            className="ai-analysis-trigger-btn"
                            onClick={() => setShowAnalysisModal(true)}
                            disabled={!current.rawLlmJson}
                        >
                            🤖 Claude AI 분석 보기
                            {!current.rawLlmJson && (
                                <span style={{ fontSize: 12, opacity: 0.7, marginLeft: 6 }}>
                                    (분석 데이터 없음)
                                </span>
                            )}
                        </button>
                    </div>

                    <div className="body-right">
                        <h3 className="section-title">체형 시각화</h3>
                        <HumanModelView summary={current} />
                    </div>
                </div>
            )}

            {/* AI 분석 모달 */}
            <LlmAnalysisModal
                isOpen={showAnalysisModal}
                onClose={() => setShowAnalysisModal(false)}
                rawLlmJson={current?.rawLlmJson}
                recordDate={current?.recordDate ?? ""}
            />
        </div>
    );
}