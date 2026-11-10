import {useCallback, useState} from "react";
import {useDropzone} from "react-dropzone";

// 따릉이 이용내역 캡처 → 활동 추가.
// 1) 미리보기(dryRun)로 인식 결과를 보여주고 2) "저장"을 누르면 같은 이미지로 다시 저장 요청한다.
interface RideResult {
    status: "saved" | "duplicate" | "preview" | "fail";
    activityId?: number;
    rentStation?: string;
    returnStation?: string;
    rentTime?: string;
    returnTime?: string;
    distanceKm?: number;
    durationMin?: number;
    calories?: number;
    caloriesSource?: "screen" | "met";
    routeSource?: "route" | "straight" | "none";
    message?: string;
}

interface FileResult {
    filename: string;
    status: string;
    message?: string;
    rides: RideResult[];
}

const STATUS_LABEL: Record<string, string> = {
    preview: "🔎 미리보기",
    saved: "✅ 저장됨",
    duplicate: "⏭️ 이미 있음",
    fail: "❌ 실패",
};

const ROUTE_LABEL: Record<string, string> = {
    route: "추정 경로",
    straight: "직선 표시",
    none: "경로 없음",
};

const fmtTime = (s?: string) => (s ? s.replace("T", " ").slice(5, 16) : "-");

export default function DdareungiUploadModal({onClose, onSuccess}: {
    onClose: () => void;
    onSuccess: () => void;
}) {
    const [files, setFiles] = useState<File[]>([]);
    const [results, setResults] = useState<FileResult[]>([]);
    const [busy, setBusy] = useState<"" | "preview" | "save">("");
    const [error, setError] = useState<string | null>(null);
    const [saved, setSaved] = useState(false);

    const send = useCallback(async (targets: File[], dryRun: boolean) => {
        setBusy(dryRun ? "preview" : "save");
        setError(null);
        const formData = new FormData();
        targets.forEach(f => formData.append("files", f));
        try {
            const res = await fetch(`/api/ddareungi/upload?dryRun=${dryRun}`, {method: "POST", body: formData});
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            const data: FileResult[] = await res.json();
            setResults(data);
            if (!dryRun) {
                setSaved(true);
                if (data.some(f => f.rides.some(r => r.status === "saved"))) onSuccess();
            }
        } catch (e) {
            setError(`요청 실패: ${(e as Error).message}`);
        } finally {
            setBusy("");
        }
    }, [onSuccess]);

    const onDrop = useCallback((accepted: File[]) => {
        if (!accepted.length) return;
        setFiles(accepted);
        setSaved(false);
        send(accepted, true);
    }, [send]);

    const {getRootProps, getInputProps, isDragActive} = useDropzone({
        onDrop,
        accept: {"image/png": [".png"], "image/jpeg": [".jpg", ".jpeg"], "image/webp": [".webp"]},
        multiple: true,
    });

    const savable = !saved && results.some(f => f.rides.some(r => r.status === "preview"));

    return (
        <div style={{
            position: "fixed", inset: 0, background: "rgba(0,0,0,0.6)",
            display: "flex", alignItems: "center", justifyContent: "center", zIndex: 1000
        }}>
            <div style={{background: "#1e293b", borderRadius: 12, padding: 24, width: 520, maxWidth: "92vw"}}>
                <div style={{display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12}}>
                    <h3 style={{color: "#e2e8f0", margin: 0}}>🚲 따릉이 이용내역 업로드</h3>
                    <button onClick={onClose} style={{
                        background: "none", border: "none", color: "#94a3b8", fontSize: 20, cursor: "pointer"
                    }}>✕
                    </button>
                </div>
                <p style={{color: "#64748b", fontSize: 12, margin: "0 0 12px"}}>
                    따릉이 앱 이용내역 화면 캡처를 올리면 대여·반납 정보와 칼로리를 읽고,
                    대여소 사이 자전거 길찾기로 경로를 추정해 활동으로 추가합니다.
                </p>

                <div {...getRootProps()} style={{
                    border: `2px dashed ${isDragActive ? "#22c55e" : "#334155"}`,
                    borderRadius: 8, padding: 28, textAlign: "center", cursor: "pointer",
                    background: isDragActive ? "rgba(34,197,94,0.1)" : "transparent"
                }}>
                    <input {...getInputProps()} />
                    <div style={{fontSize: 32, marginBottom: 6}}>🖼️</div>
                    <p style={{color: "#94a3b8", margin: 0}}>
                        {isDragActive ? "이미지를 놓으세요" : "캡처 이미지를 드래그하거나 클릭해서 선택 (png/jpg)"}
                    </p>
                </div>

                {busy && (
                    <p style={{color: "#94a3b8", textAlign: "center", marginTop: 14}}>
                        ⏳ {busy === "preview" ? "이미지 인식 중..." : "저장 중..."}
                    </p>
                )}
                {error && <p style={{color: "#f87171", fontSize: 13, marginTop: 12}}>{error}</p>}

                {results.length > 0 && (
                    <div style={{marginTop: 14, maxHeight: 300, overflowY: "auto", display: "flex", flexDirection: "column", gap: 8}}>
                        {results.map((f, i) => (
                            <div key={i}>
                                <div style={{color: "#64748b", fontSize: 12, marginBottom: 4}}>
                                    {f.filename} {f.message && <span style={{color: "#f87171"}}>— {f.message}</span>}
                                </div>
                                {f.rides.map((r, j) => (
                                    <div key={j} style={{background: "#0f172a", borderRadius: 6, padding: "8px 12px", marginBottom: 4}}>
                                        <div style={{display: "flex", justifyContent: "space-between", color: "#e2e8f0", fontSize: 13}}>
                                            <span>{r.rentStation ?? "?"} → {r.returnStation ?? "?"}</span>
                                            <span>{STATUS_LABEL[r.status] ?? r.status}</span>
                                        </div>
                                        <div style={{color: "#94a3b8", fontSize: 12, marginTop: 2}}>
                                            {fmtTime(r.rentTime)} ~ {fmtTime(r.returnTime)}
                                            {r.distanceKm != null && ` · ${r.distanceKm}km`}
                                            {r.durationMin != null && ` · ${r.durationMin}분`}
                                            {r.calories != null && ` · ${r.calories}kcal${r.caloriesSource === "met" ? "(추정)" : ""}`}
                                            {r.routeSource && ` · ${ROUTE_LABEL[r.routeSource]}`}
                                        </div>
                                        {r.message && <div style={{color: "#fbbf24", fontSize: 11, marginTop: 2}}>{r.message}</div>}
                                    </div>
                                ))}
                            </div>
                        ))}
                    </div>
                )}

                {savable && (
                    <button
                        disabled={!!busy}
                        onClick={() => send(files, false)}
                        style={{
                            marginTop: 14, width: "100%", padding: "10px 0", borderRadius: 8,
                            background: "#15803d", border: "none", color: "#f0fdf4", fontSize: 14, cursor: "pointer"
                        }}
                    >
                        저장
                    </button>
                )}
            </div>
        </div>
    );
}
