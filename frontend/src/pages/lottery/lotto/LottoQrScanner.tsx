import { useEffect, useRef, useState } from "react";
import type { CSSProperties } from "react";
import { Html5Qrcode } from "html5-qrcode";

const QR_ELEMENT_ID = "lotto-qr-reader";

export default function LottoQrScanner({
    accent,
    onScan,
    onError,
}: {
    accent: string;
    onScan: (text: string) => void;
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
                    onScan(decodedText);
                    stopCamera();
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
            onScan(text);
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
            <div style={{ display: "flex", gap: 10, marginBottom: 12 }}>
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
