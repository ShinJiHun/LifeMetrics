import { useEffect, useRef, useState } from "react";
import type { CSSProperties } from "react";
import { Html5Qrcode } from "html5-qrcode";

const QR_ELEMENT_ID = "pension-qr-reader";

export default function PensionQrScanner({
    accent,
    onScan,
    onError,
}: {
    accent: string;
    onScan: (data: { round: number; jo: number; numbers: number[] }) => void;
    onError?: (message: string) => void;
}) {
    const scannerRef = useRef<Html5Qrcode | null>(null);
    const fileInputRef = useRef<HTMLInputElement>(null);
    const [cameraOn, setCameraOn] = useState(false);
    const [busy, setBusy] = useState(false);

    useEffect(() => {
        return () => {
            const scanner = scannerRef.current;
            if (scanner?.isScanning) scanner.stop().catch(() => {});
        };
    }, []);

    const getScanner = () => {
        if (!scannerRef.current) {
            scannerRef.current = new Html5Qrcode(QR_ELEMENT_ID);
        }
        return scannerRef.current;
    };

    const parseQrText = (text: string): { round: number; jo: number; numbers: number[] } | null => {
        try {
            // 연금복권 QR 형식 예상: "회차:123,조:1,번호:12,34,56,78,90,12"
            // 또는 다른 형식일 수 있으니 유연하게 파싱
            
            // 숫자만 추출
            const numbersMatch = text.match(/\d+/g);
            if (!numbersMatch || numbersMatch.length < 7) {
                return null;
            }

            // 첫 번째 숫자: 회차
            const round = parseInt(numbersMatch[0], 10);
            // 두 번째 숫자: 당첨조 (1-5 범위 확인)
            const jo = parseInt(numbersMatch[1], 10);
            
            if (jo < 1 || jo > 5) {
                return null;
            }

            // 나머지 6개: 번호
            const numbers = numbersMatch.slice(2, 8).map(n => parseInt(n, 10));
            
            if (numbers.length !== 6) {
                return null;
            }

            return { round, jo, numbers };
        } catch {
            return null;
        }
    };

    const handleQrScan = (qrText: string) => {
        const parsed = parseQrText(qrText);
        if (!parsed) {
            onError?.("QR 코드 형식을 인식하지 못했습니다. 예: 회차,당첨조,번호들");
            return;
        }
        onScan(parsed);
        stopCamera();
    };

    const stopCamera = () => {
        const scanner = scannerRef.current;
        if (scanner?.isScanning) {
            scanner.stop().catch(() => {}).finally(() => setCameraOn(false));
        } else {
            setCameraOn(false);
        }
    };

    const startCamera = async () => {
        setCameraOn(true);
        try {
            const scanner = getScanner();
            await scanner.start(
                { facingMode: "environment" },
                { fps: 10, qrbox: 220 },
                (decodedText) => {
                    handleQrScan(decodedText);
                },
                () => {
                    // 프레임마다 못 읽었을 때 호출됨 - 스캔 중에는 정상이므로 무시
                },
            );
        } catch (err) {
            setCameraOn(false);
            onError?.((err as Error).message || "카메라를 시작할 수 없습니다.");
        }
    };

    const handleFilePick = () => fileInputRef.current?.click();

    const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        if (!file) return;
        setBusy(true);
        try {
            const text = await getScanner().scanFile(file, false);
            handleQrScan(text);
        } catch {
            onError?.("이미지에서 QR 코드를 찾지 못했습니다.");
        } finally {
            setBusy(false);
            if (fileInputRef.current) fileInputRef.current.value = "";
        }
    };

    const btn = (active?: boolean): CSSProperties => ({
        padding: "10px 16px",
        borderRadius: 10,
        border: `1px solid ${accent}`,
        background: active ? accent : "#fff",
        color: active ? "#fff" : accent,
        fontWeight: 600,
        fontSize: 14,
        cursor: "pointer",
    });

    return (
        <div>
            <div style={{ display: "flex", gap: 10, marginBottom: 12, flexWrap: "wrap" }}>
                <button type="button" onClick={cameraOn ? stopCamera : startCamera} style={btn(cameraOn)}>
                    {cameraOn ? "카메라 끄기" : "📷 카메라로 QR 스캔"}
                </button>
                <input
                    ref={fileInputRef}
                    type="file"
                    accept="image/*"
                    onChange={handleFileChange}
                    style={{ display: "none" }}
                />
                <button type="button" onClick={handleFilePick} disabled={busy} style={btn(false)}>
                    {busy ? "인식 중..." : "🖼 QR 이미지 파일에서 인식"}
                </button>
            </div>
            <div
                id={QR_ELEMENT_ID}
                style={{ width: "100%", maxWidth: 320, ...(cameraOn ? {} : { display: "none" }) }}
            />
        </div>
    );
}
