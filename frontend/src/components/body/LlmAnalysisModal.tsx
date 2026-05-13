import "@/styles/llm-analysis-modal.css";

type LlmAnalysis = {
    summary?: string;
    current_evaluation?: {
        muscle?: string;
        fat?: string;
        overall?: string;
    };
    trend?: string | null;
    goal_status?: {
        body?: string;
        cycling?: string;
    };
    recommendations?: string[];
};

type Props = {
    isOpen: boolean;
    onClose: () => void;
    rawLlmJson: string | null | undefined;
    recordDate: string;
};

export default function LlmAnalysisModal({
                                             isOpen,
                                             onClose,
                                             rawLlmJson,
                                             recordDate,
                                         }: Props) {
    if (!isOpen) return null;

    const renderContent = () => {
        if (!rawLlmJson) {
            return (
                <p className="estimate-note">※ 저장된 LLM 응답이 없습니다.</p>
            );
        }

        let parsed: LlmAnalysis | null = null;
        try {
            const node = JSON.parse(rawLlmJson);
            parsed = Array.isArray(node) ? node[0] : node;
        } catch {
            return (
                <pre style={{ margin: 0, whiteSpace: "pre-wrap", wordBreak: "break-word" }}>
                    {rawLlmJson}
                </pre>
            );
        }

        if (!parsed || typeof parsed !== "object") {
            return <p className="estimate-note">※ 응답 JSON이 비어 있습니다.</p>;
        }

        const hasAnyContent =
            parsed.summary ||
            parsed.current_evaluation ||
            parsed.trend ||
            parsed.goal_status ||
            (parsed.recommendations && parsed.recommendations.length > 0);

        if (!hasAnyContent) {
            return (
                <pre style={{ margin: 0, whiteSpace: "pre-wrap", wordBreak: "break-word" }}>
                    {rawLlmJson}
                </pre>
            );
        }

        return (
            <div className="ai-analysis">
                {parsed.summary && (
                    <div className="ai-section ai-summary">
                        <span className="ai-summary-icon">💡</span>
                        <span className="ai-summary-text">{parsed.summary}</span>
                    </div>
                )}

                {parsed.current_evaluation && (
                    <div className="ai-section">
                        <h4 className="ai-section-title">📊 현재 평가</h4>
                        <div className="ai-eval-grid">
                            {parsed.current_evaluation.muscle && (
                                <div className="ai-eval-item">
                                    <span className="ai-eval-label">💪 근육</span>
                                    <span className="ai-eval-value">{parsed.current_evaluation.muscle}</span>
                                </div>
                            )}
                            {parsed.current_evaluation.fat && (
                                <div className="ai-eval-item">
                                    <span className="ai-eval-label">🍔 체지방</span>
                                    <span className="ai-eval-value">{parsed.current_evaluation.fat}</span>
                                </div>
                            )}
                            {parsed.current_evaluation.overall && (
                                <div className="ai-eval-item">
                                    <span className="ai-eval-label">⚖️ 종합</span>
                                    <span className="ai-eval-value">{parsed.current_evaluation.overall}</span>
                                </div>
                            )}
                        </div>
                    </div>
                )}

                {parsed.trend && (
                    <div className="ai-section">
                        <h4 className="ai-section-title">📈 변화 추이</h4>
                        <p className="ai-trend">{parsed.trend}</p>
                    </div>
                )}

                {parsed.goal_status && (
                    <div className="ai-section">
                        <h4 className="ai-section-title">🎯 목표 대비 현황</h4>
                        <div className="ai-eval-grid">
                            {parsed.goal_status.body && (
                                <div className="ai-eval-item">
                                    <span className="ai-eval-label">🏃 신체</span>
                                    <span className="ai-eval-value">{parsed.goal_status.body}</span>
                                </div>
                            )}
                            {parsed.goal_status.cycling && (
                                <div className="ai-eval-item">
                                    <span className="ai-eval-label">🚴 사이클</span>
                                    <span className="ai-eval-value">{parsed.goal_status.cycling}</span>
                                </div>
                            )}
                        </div>
                    </div>
                )}

                {Array.isArray(parsed.recommendations) &&
                    parsed.recommendations.length > 0 && (
                        <div className="ai-section">
                            <h4 className="ai-section-title">✅ 권고사항</h4>
                            <ul className="ai-recommendations">
                                {parsed.recommendations.map((rec, i) => (
                                    <li key={i}>{rec}</li>
                                ))}
                            </ul>
                        </div>
                    )}
            </div>
        );
    };

    return (
        <div className="llm-modal-overlay" onClick={onClose}>
            <div className="llm-modal-box" onClick={(e) => e.stopPropagation()}>
                <div className="llm-modal-header">
                    <h3 className="llm-modal-title">🤖 Claude 분석 · {recordDate}</h3>
                    <button className="llm-modal-close" onClick={onClose} aria-label="닫기">
                        ✕
                    </button>
                </div>
                <div className="llm-modal-body">{renderContent()}</div>
            </div>
        </div>
    );
}