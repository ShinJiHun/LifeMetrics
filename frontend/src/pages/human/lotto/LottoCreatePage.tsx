import { useRef, useState } from "react";
import { uploadLottoTicket, type LottoTicket } from "@/api/lotto";
import LottoNumberBall from "./LottoNumberBall";

const ACCENT = "#16a34a";

export default function LottoCreatePage() {
    const fileInputRef = useRef<HTMLInputElement>(null);
    const [uploading, setUploading] = useState(false);
    const [previewUrl, setPreviewUrl] = useState<string | null>(null);
    const [result, setResult] = useState<LottoTicket[] | null>(null);
    const [error, setError] = useState<string | null>(null);

    const handlePick = () => fileInputRef.current?.click();

    const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        if (!file) return;

        setPreviewUrl(URL.createObjectURL(file));
        setResult(null);
        setError(null);
        setUploading(true);

        try {
            const res = await uploadLottoTicket(file);
            if (res.success) {
                setResult(res.tickets);
            } else {
                setError(res.message);
            }
        } catch (err) {
            setError((err as Error).message || "업로드 중 오류가 발생했습니다.");
        } finally {
            setUploading(false);
            if (fileInputRef.current) fileInputRef.current.value = "";
        }
    };

    return (
        <div style={{ padding: 24, maxWidth: 720 }}>
            <h2 style={{ margin: 0 }}>➕ 로또 생성 (구매 티켓 등록)</h2>
            <p style={{ color: "#6b7280", fontSize: 14, marginTop: 8 }}>
                구매한 로또 용지 사진을 올리면 Claude가 회차와 게임별 번호를 읽어서 NAS에 원본을
                저장하고, 홀짝/고저 비율까지 함께 계산해 기록합니다.
            </p>

            <input
                ref={fileInputRef}
                type="file"
                accept="image/*"
                onChange={handleFileChange}
                style={{ display: "none" }}
            />

            <div
                onClick={handlePick}
                style={{
                    marginTop: 20,
                    border: `2px dashed ${uploading ? "#d1d5db" : ACCENT}`,
                    borderRadius: 16,
                    padding: 32,
                    textAlign: "center",
                    cursor: uploading ? "default" : "pointer",
                    background: "#f9fafb",
                }}
            >
                {previewUrl ? (
                    <img
                        src={previewUrl}
                        alt="업로드한 로또 용지"
                        style={{ maxWidth: "100%", maxHeight: 260, borderRadius: 10 }}
                    />
                ) : (
                    <div style={{ color: "#9aa0b2" }}>📷 클릭해서 로또 용지 사진 선택</div>
                )}
                <div style={{ marginTop: 12, fontSize: 13, color: ACCENT, fontWeight: 600 }}>
                    {uploading ? "인식 중..." : previewUrl ? "다른 사진으로 바꾸기" : "사진 선택"}
                </div>
            </div>

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
                    {error}
                </div>
            )}

            {result && result.length > 0 && (
                <div style={{ marginTop: 24 }}>
                    <div style={{ fontWeight: 600, marginBottom: 10 }}>
                        ✅ {result.length}게임 저장됨
                        {result[0].round != null ? ` · ${result[0].round}회` : " · 회차 인식 안 됨"}
                    </div>
                    <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
                        {result.map((t) => (
                            <div
                                key={t.id}
                                style={{
                                    display: "flex",
                                    alignItems: "center",
                                    gap: 12,
                                    padding: "12px 16px",
                                    background: "#f9fafb",
                                    borderRadius: 10,
                                    flexWrap: "wrap",
                                }}
                            >
                                <span style={{ fontSize: 12, fontWeight: 700, color: "#9aa0b2", width: 18 }}>
                                    {String.fromCharCode(64 + t.gameNo)}
                                </span>
                                <div style={{ display: "flex", gap: 4 }}>
                                    {t.numbers.map((n) => (
                                        <LottoNumberBall key={n} n={n} size={26} />
                                    ))}
                                </div>
                                <span style={{ fontSize: 12, color: "#6b7280" }}>
                                    홀:짝 {t.oddCount}:{t.evenCount} · 저:고 {t.lowCount}:{t.highCount} · 합 {t.sum}
                                    {t.consecutivePairCount > 0 && ` · 연속쌍 ${t.consecutivePairCount}`}
                                </span>
                            </div>
                        ))}
                    </div>
                </div>
            )}
        </div>
    );
}
