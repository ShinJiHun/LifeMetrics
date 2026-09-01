import { useState, useMemo, useEffect, useRef } from "react";
import { useBodyRecords } from "@/hooks/useBodyRecords.ts";
import { analyzeBodyRecord } from "@/api/body.ts";

import BodyMetricCards from "@/components/body/BodyMetricCards.tsx";
import BodyMetricChart from "@/components/body/BodyMetricChart.tsx";
import LlmAnalysisPanel from "@/components/body/LlmAnalysisPanel.tsx";

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
    const [selectedDate, setSelectedDate] = useState<string | null>(null);
    const [analyzing, setAnalyzing] = useState(false);
    const [analyzeError, setAnalyzeError] = useState<string | null>(null);

    // 탭에 따라 필터링
    const filteredRecords = useMemo<BodySummaryRecord[]>(() => {
        return [...records]
            .filter((r) => r.measurementType === activeTab)
            .sort(
                (a, b) =>
                    new Date(a.recordDate).getTime() - new Date(b.recordDate).getTime()
            );
    }, [records, activeTab]);

    // 최신 기록으로 초기 선택하되, 이미 고른 날짜가 새 목록에도 있으면 유지한다
    // (분석 실행 후 refetch 되어도 보던 기록에서 튕기지 않도록).
    useEffect(() => {
        if (filteredRecords.length === 0) {
            setSelectedDate(null);
            return;
        }
        if (!selectedDate || !filteredRecords.some((r) => r.recordDate === selectedDate)) {
            setSelectedDate(filteredRecords[filteredRecords.length - 1].recordDate);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [filteredRecords]);

    const currentIndex = selectedDate
        ? filteredRecords.findIndex((r) => r.recordDate === selectedDate)
        : -1;
    const current = currentIndex >= 0 ? filteredRecords[currentIndex] : undefined;

    const goPrev = () => {
        const i = Math.max(0, currentIndex - 1);
        setSelectedDate(filteredRecords[i]?.recordDate ?? null);
    };
    const goNext = () => {
        const i = Math.min(filteredRecords.length - 1, currentIndex + 1);
        setSelectedDate(filteredRecords[i]?.recordDate ?? null);
    };

    const handleDateClick = (recordDate: string) => setSelectedDate(recordDate);

    const handleAnalyze = async () => {
        if (!current) return;
        setAnalyzing(true);
        setAnalyzeError(null);
        try {
            await analyzeBodyRecord(current.id);
            await refetch?.();
        } catch (e) {
            setAnalyzeError((e as Error).message);
        } finally {
            setAnalyzing(false);
        }
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
            <div style={{ display: "flex", gap: 8, marginBottom: 16, flexWrap: "wrap" }}>
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
                    </div>

                    <div className="body-right">
                        <h3 className="section-title">🤖 Claude AI 분석</h3>
                        <LlmAnalysisPanel rawLlmJson={current.rawLlmJson} />
                        <button
                            className="ai-analyze-btn"
                            onClick={handleAnalyze}
                            disabled={analyzing}
                        >
                            {analyzing ? "분석 중…" : "🤖 분석 실행"}
                        </button>
                        {analyzeError && (
                            <div className="ai-analyze-error">{analyzeError}</div>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
}