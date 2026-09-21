import { useState } from "react";
import {
    registerPensionTicket,
    type PensionTicket,
} from "@/api/pension";
import PensionQrScanner from "./PensionQrScanner";

const ACCENT = "#7c3aed";

export default function PensionCreatePage() {
    const [mode, setMode] = useState<"manual" | "qr">("manual");
    const [round, setRound] = useState<string>("");
    const [jo, setJo] = useState<string>("1");
    const [numberInput, setNumberInput] = useState<string>("");
    const [purchasedAt, setPurchasedAt] = useState<string>("");
    const [loading, setLoading] = useState(false);
    const [message, setMessage] = useState<string | null>(null);
    const [result, setResult] = useState<PensionTicket[] | null>(null);
    const [error, setError] = useState<string | null>(null);

    const handleNumberInputChange = (value: string) => {
        // 숫자만 허용, 최대 6자리
        const numValue = value.replace(/\D/g, "").slice(0, 6);
        setNumberInput(numValue);
    };

    const parseNumbers = (input: string): number[] | null => {
        // 6자리 숫자를 2자리씩 6개로 나누기
        if (input.length !== 6) return null;
        
        const nums: number[] = [];
        for (let i = 0; i < 6; i += 1) {
            const twoDigits = input.substring(i, i + 1);
            nums.push(parseInt(twoDigits, 10));
        }
        return nums;
    };

    const resetForm = () => {
        setRound("");
        setJo("1");
        setNumberInput("");
        setPurchasedAt("");
    };

    const handleSubmit = async () => {
        if (!round || !jo || numberInput.length !== 6) {
            setError("회차, 당첨조, 6자리 번호를 모두 입력해주세요");
            return;
        }

        const numbers = parseNumbers(numberInput);
        if (!numbers) {
            setError("번호 형식이 잘못되었습니다");
            return;
        }

        setLoading(true);
        setError(null);
        setMessage(null);
        setResult(null);

        try {
            const response = await registerPensionTicket(
                parseInt(round, 10),
                parseInt(jo, 10),
                numbers,
                purchasedAt || undefined
            );

            if (response.success) {
                setMessage(response.message);
                setResult(response.tickets || []);
                resetForm();
            } else {
                setError(response.message);
            }
        } catch (err) {
            setError((err as Error).message || "등록 중 오류가 발생했습니다");
        } finally {
            setLoading(false);
        }
    };

    const handleQrScan = async (data: { round: number; jo: number; numbers: number[] }) => {
        setLoading(true);
        setError(null);
        setMessage(null);
        setResult(null);

        try {
            const response = await registerPensionTicket(
                data.round,
                data.jo,
                data.numbers,
                undefined
            );

            if (response.success) {
                setMessage(response.message);
                setResult(response.tickets || []);
                setMode("manual");
                resetForm();
            } else {
                setError(response.message);
            }
        } catch (err) {
            setError((err as Error).message || "QR 등록 중 오류가 발생했습니다");
        } finally {
            setLoading(false);
        }
    };

    return (
        <div style={{ padding: 24, maxWidth: 720 }}>
            <h2 style={{ margin: 0 }}>➕ 연금복권 기록</h2>
            <p style={{ color: "#6b7280", fontSize: 14, marginTop: 8 }}>
                구매한 연금복권 정보를 기록합니다. 회차, 당첨조, 6자리 번호를 입력하면
                세트로 저장됩니다.
            </p>

            <div style={{ display: "flex", gap: 8, marginTop: 20 }}>
                <TabButton active={mode === "manual"} onClick={() => setMode("manual")}>
                    ✏️ 번호 입력
                </TabButton>
                <TabButton active={mode === "qr"} onClick={() => setMode("qr")}>
                    🔳 QR 스캔
                </TabButton>
            </div>

            {mode === "manual" ? (
                <div style={{ marginTop: 20 }}>
                    {/* 회차 입력 */}
                    <div style={{ marginBottom: 16 }}>
                        <label style={{ fontSize: 12, color: ACCENT, fontWeight: 700, display: "block", marginBottom: 6 }}>
                            회차 번호
                        </label>
                        <input
                            type="number"
                            value={round}
                            onChange={(e) => setRound(e.target.value)}
                            placeholder="예: 332"
                            style={{
                                width: "100%",
                                padding: "10px 12px",
                                borderRadius: 6,
                                border: "1px solid #e5e7eb",
                                fontSize: 14,
                            }}
                        />
                    </div>

                    {/* 당첨조 선택 */}
                    <div style={{ marginBottom: 16 }}>
                        <label style={{ fontSize: 12, color: ACCENT, fontWeight: 700, display: "block", marginBottom: 6 }}>
                            당첨조
                        </label>
                        <div style={{ display: "flex", gap: 8 }}>
                            {[1, 2, 3, 4, 5].map((j) => (
                                <button
                                    key={j}
                                    type="button"
                                    onClick={() => setJo(String(j))}
                                    style={{
                                        width: 40,
                                        height: 40,
                                        borderRadius: 6,
                                        border: `2px solid ${jo === String(j) ? ACCENT : "#e5e7eb"}`,
                                        background: jo === String(j) ? ACCENT + "20" : "#fff",
                                        color: jo === String(j) ? ACCENT : "#6b7280",
                                        fontWeight: 700,
                                        cursor: "pointer",
                                    }}
                                >
                                    {j}조
                                </button>
                            ))}
                        </div>
                    </div>

                    {/* 번호 입력 (6자리) */}
                    <div style={{ marginBottom: 16 }}>
                        <label style={{ fontSize: 12, color: ACCENT, fontWeight: 700, display: "block", marginBottom: 8 }}>
                            6자리 번호 입력
                        </label>
                        <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
                            <input
                                type="text"
                                value={numberInput}
                                onChange={(e) => handleNumberInputChange(e.target.value)}
                                placeholder="384363"
                                maxLength={6}
                                style={{
                                    flex: 1,
                                    padding: "12px 16px",
                                    borderRadius: 6,
                                    border: "1px solid #e5e7eb",
                                    fontSize: 20,
                                    fontWeight: 700,
                                    letterSpacing: 4,
                                    textAlign: "center",
                                }}
                            />
                            <div style={{ display: "flex", gap: 4 }}>
                                {numberInput.split("").map((digit, i) => (
                                    <div
                                        key={i}
                                        style={{
                                            width: 32,
                                            height: 32,
                                            display: "flex",
                                            alignItems: "center",
                                            justifyContent: "center",
                                            background: ACCENT + "20",
                                            color: ACCENT,
                                            borderRadius: 4,
                                            fontSize: 12,
                                            fontWeight: 700,
                                        }}
                                    >
                                        {digit || "-"}
                                    </div>
                                ))}
                            </div>
                        </div>
                        <div style={{ fontSize: 12, color: "#9ca3af", marginTop: 8 }}>
                            {numberInput.length}/6자리
                        </div>
                    </div>

                    {/* 구매일 선택 */}
                    <div style={{ marginBottom: 16 }}>
                        <label style={{ fontSize: 12, color: "#6b7280", fontWeight: 700, display: "block", marginBottom: 6 }}>
                            구매일 (선택사항)
                        </label>
                        <input
                            type="date"
                            value={purchasedAt}
                            onChange={(e) => setPurchasedAt(e.target.value)}
                            style={{
                                width: "100%",
                                padding: "10px 12px",
                                borderRadius: 6,
                                border: "1px solid #e5e7eb",
                                fontSize: 14,
                            }}
                        />
                    </div>

                    {/* 저장 버튼 */}
                    <button
                        type="button"
                        onClick={handleSubmit}
                        disabled={loading || numberInput.length !== 6}
                        style={{
                            width: "100%",
                            padding: 12,
                            background: ACCENT,
                            color: "#fff",
                            border: "none",
                            borderRadius: 6,
                            fontWeight: 700,
                            cursor: (loading || numberInput.length !== 6) ? "default" : "pointer",
                            opacity: (loading || numberInput.length !== 6) ? 0.6 : 1,
                        }}
                    >
                        {loading ? "저장 중..." : "세트 저장"}
                    </button>
                </div>
            ) : (
                <div style={{ marginTop: 16 }}>
                    <p style={{ color: "#6b7280", fontSize: 13, marginBottom: 12 }}>
                        연금복권 QR 코드를 스캔하거나 QR 이미지 파일을 선택하세요.
                    </p>
                    <PensionQrScanner
                        accent={ACCENT}
                        onScan={handleQrScan}
                        onError={(msg) => setError(msg)}
                    />
                    {loading && (
                        <div style={{ marginTop: 12, fontSize: 13, color: ACCENT, fontWeight: 600 }}>
                            저장 중...
                        </div>
                    )}
                </div>
            )}

            {message && (
                <div
                    style={{
                        marginTop: 16,
                        padding: 14,
                        background: "#f0fdf4",
                        border: "1px solid #bbf7d0",
                        borderRadius: 8,
                        color: "#166534",
                        fontSize: 14,
                    }}
                >
                    ✅ {message}
                </div>
            )}

            {error && (
                <div
                    style={{
                        marginTop: 16,
                        padding: 14,
                        background: "#fef2f2",
                        border: "1px solid #fecaca",
                        borderRadius: 8,
                        color: "#991b1b",
                        fontSize: 14,
                    }}
                >
                    ❌ {error}
                </div>
            )}

            {result && result.length > 0 && (
                <div style={{ marginTop: 24 }}>
                    <div style={{ fontWeight: 600, marginBottom: 12 }}>
                        ✅ 저장 완료
                    </div>
                    <div
                        style={{
                            padding: 16,
                            background: "#f9fafb",
                            borderRadius: 8,
                            border: `1px solid ${ACCENT}40`,
                        }}
                    >
                        {result.map((ticket) => (
                            <div key={ticket.id} style={{ marginBottom: 8 }}>
                                <div style={{ fontWeight: 700, marginBottom: 4 }}>
                                    {ticket.round}회 · {ticket.jo}조
                                </div>
                                <div style={{ display: "flex", gap: 4 }}>
                                    {[ticket.n1, ticket.n2, ticket.n3, ticket.n4, ticket.n5, ticket.n6].map((n, i) => (
                                        <div
                                            key={i}
                                            style={{
                                                width: 32,
                                                height: 32,
                                                display: "flex",
                                                alignItems: "center",
                                                justifyContent: "center",
                                                background: ACCENT + "20",
                                                color: ACCENT,
                                                borderRadius: 4,
                                                fontSize: 12,
                                                fontWeight: 700,
                                            }}
                                        >
                                            {n}
                                        </div>
                                    ))}
                                </div>
                            </div>
                        ))}
                    </div>
                </div>
            )}
        </div>
    );
}

function TabButton({
    active,
    onClick,
    children,
}: {
    active: boolean;
    onClick: () => void;
    children: React.ReactNode;
}) {
    return (
        <button
            type="button"
            onClick={onClick}
            style={{
                padding: "8px 16px",
                borderRadius: 999,
                border: "none",
                background: active ? ACCENT : "#f1f3f9",
                color: active ? "#fff" : "#6b7280",
                fontWeight: 700,
                fontSize: 13,
                cursor: "pointer",
            }}
        >
            {children}
        </button>
    );
}
