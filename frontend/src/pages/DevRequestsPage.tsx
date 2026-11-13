// 개발 요청함 (관리자 전용).
// 여기서 요청을 쓰면 서버 워커가 Claude Code 로 작업 → 빌드 → 개발 서버(8081)에 배포한다.
// 개발 서버에서 확인한 뒤 "운영 반영"을 누르면 master 머지 → 운영 배포. 고칠 게 있으면 "추가 요청".
import { useCallback, useEffect, useState } from "react";
import type { CSSProperties } from "react";
import {
    approveDevRequest,
    cancelDevRequest,
    createDevRequest,
    fetchDevRequest,
    fetchDevRequests,
    rejectDevRequest,
    type DevRequest,
    type DevRequestStatus,
} from "@/api/devRequests";

const DEV_URL = "http://34.172.162.148:8081";
const INK = "#111827";
const MUTED = "#6b7280";
const BORDER = "#e5e7eb";

const STATUS: Record<DevRequestStatus, { label: string; color: string; bg: string }> = {
    QUEUED: { label: "대기", color: "#4b5563", bg: "#f3f4f6" },
    RUNNING: { label: "작업 중", color: "#1d4ed8", bg: "#dbeafe" },
    DEV_READY: { label: "개발 서버 확인", color: "#047857", bg: "#d1fae5" },
    FAILED: { label: "실패", color: "#b91c1c", bg: "#fee2e2" },
    APPROVED: { label: "운영 반영 대기", color: "#7c3aed", bg: "#ede9fe" },
    PROMOTING: { label: "운영 반영 중", color: "#7c3aed", bg: "#ede9fe" },
    DONE: { label: "운영 반영됨", color: "#065f46", bg: "#a7f3d0" },
    REJECTED: { label: "반려", color: "#6b7280", bg: "#f3f4f6" },
    CANCELLED: { label: "취소", color: "#6b7280", bg: "#f3f4f6" },
    SUPERSEDED: { label: "추가 요청으로 이어짐", color: "#6b7280", bg: "#f3f4f6" },
};
const ACTIVE: DevRequestStatus[] = ["QUEUED", "RUNNING", "APPROVED", "PROMOTING"];

function errorMessage(e: unknown): string {
    const anyErr = e as { response?: { status?: number; data?: { message?: string } } };
    if (anyErr.response?.status === 403) return "관리자 로그인이 필요합니다.";
    return anyErr.response?.data?.message || "요청에 실패했습니다.";
}

const when = (s: string | null) => (s ? s.replace("T", " ").slice(0, 16) : "–");

function Badge({ status }: { status: DevRequestStatus }) {
    const m = STATUS[status] ?? STATUS.QUEUED;
    return (
        <span style={{ fontSize: 12, fontWeight: 600, padding: "2px 8px", borderRadius: 999, color: m.color, background: m.bg, whiteSpace: "nowrap" }}>
            {m.label}
        </span>
    );
}

export default function DevRequestsPage() {
    const [items, setItems] = useState<DevRequest[]>([]);
    const [selectedId, setSelectedId] = useState<number | null>(null);
    const [detail, setDetail] = useState<DevRequest | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [body, setBody] = useState("");
    const [followUp, setFollowUp] = useState("");
    const [busy, setBusy] = useState(false);
    const [showLog, setShowLog] = useState(false);

    const load = useCallback(async () => {
        try {
            setItems(await fetchDevRequests());
            setError(null);
        } catch (e) {
            setError(errorMessage(e));
        }
    }, []);

    const loadDetail = useCallback(async (id: number) => {
        try {
            setDetail(await fetchDevRequest(id));
        } catch (e) {
            setError(errorMessage(e));
        }
    }, []);

    useEffect(() => {
        load();
    }, [load]);

    useEffect(() => {
        if (selectedId != null) loadDetail(selectedId);
        else setDetail(null);
    }, [selectedId, loadDetail]);

    // 진행 중인 요청이 있으면 5초마다 새로고침
    const anyActive = items.some((r) => ACTIVE.includes(r.status));
    useEffect(() => {
        if (!anyActive) return;
        const t = window.setInterval(() => {
            load();
            if (selectedId != null) loadDetail(selectedId);
        }, 5000);
        return () => window.clearInterval(t);
    }, [anyActive, selectedId, load, loadDetail]);

    const act = async (fn: () => Promise<DevRequest>, confirmText?: string) => {
        if (confirmText && !window.confirm(confirmText)) return;
        setBusy(true);
        try {
            const r = await fn();
            setSelectedId(r.id);
            setDetail(r);
            await load();
            setError(null);
        } catch (e) {
            setError(errorMessage(e));
        } finally {
            setBusy(false);
        }
    };

    const submit = () =>
        act(async () => {
            const r = await createDevRequest(body.trim());
            setBody("");
            return r;
        });

    const submitFollowUp = () =>
        detail &&
        act(async () => {
            const r = await createDevRequest(followUp.trim(), undefined, detail.id);
            setFollowUp("");
            return r;
        });

    return (
        <div style={{ maxWidth: 960, margin: "0 auto", padding: "8px 0 40px", color: INK }}>
            <h2 style={{ margin: "0 0 4px" }}>🛠 개발 요청함</h2>
            <p style={{ margin: "0 0 16px", color: MUTED, fontSize: 14, lineHeight: 1.5 }}>
                요청을 남기면 서버에서 Claude 가 작업해 <a href={DEV_URL} target="_blank" rel="noreferrer">개발 서버</a>에
                올립니다. 확인 후 <b>운영 반영</b>을 눌러야 운영에 배포됩니다. 한 번에 하나씩 처리합니다.
            </p>

            {error && <div style={{ ...box, borderColor: "#fecaca", background: "#fef2f2", color: "#b91c1c", marginBottom: 12 }}>{error}</div>}

            <div style={{ ...box, marginBottom: 20 }}>
                <textarea
                    value={body}
                    onChange={(e) => setBody(e.target.value)}
                    placeholder={"무엇을 바꾸고 싶은지 적어 주세요.\n예) 파워 프로필 화면에 최근 90일과 이전 90일 파워 커브를 겹쳐서 비교해 줘"}
                    rows={5}
                    style={textarea}
                />
                <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 8 }}>
                    <button onClick={submit} disabled={busy || !body.trim()} style={primaryBtn(busy || !body.trim())}>
                        요청 보내기
                    </button>
                </div>
            </div>

            <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                {items.length === 0 && <div style={{ color: MUTED, fontSize: 14 }}>아직 요청이 없습니다.</div>}
                {items.map((r) => (
                    <div key={r.id}>
                        <button
                            onClick={() => {
                                setSelectedId(selectedId === r.id ? null : r.id);
                                setShowLog(false);
                            }}
                            style={{
                                ...box,
                                width: "100%",
                                textAlign: "left",
                                cursor: "pointer",
                                display: "flex",
                                gap: 10,
                                alignItems: "center",
                                borderColor: selectedId === r.id ? "#93c5fd" : BORDER,
                                font: "inherit",
                                color: INK,
                            }}
                        >
                            <span style={{ color: MUTED, fontSize: 13, minWidth: 32 }}>#{r.id}</span>
                            <span style={{ flex: 1, minWidth: 0, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                                {r.parentId && <span style={{ color: MUTED }}>↳ </span>}
                                {r.title}
                            </span>
                            <span style={{ color: MUTED, fontSize: 12, whiteSpace: "nowrap" }}>
                                {when(r.createdAt)}
                            </span>
                            <Badge status={r.status} />
                        </button>

                        {selectedId === r.id && detail?.id === r.id && (
                            <Detail
                                r={detail}
                                busy={busy}
                                showLog={showLog}
                                setShowLog={setShowLog}
                                followUp={followUp}
                                setFollowUp={setFollowUp}
                                onFollowUp={submitFollowUp}
                                onCancel={() => act(() => cancelDevRequest(r.id))}
                                onApprove={() =>
                                    act(() => approveDevRequest(r.id), "운영(ride.tho881.me)에 반영합니다. master 에 머지되고 운영 서버가 재시작됩니다. 계속할까요?")
                                }
                                onReject={() => act(() => rejectDevRequest(r.id), "이 요청을 반려할까요? (개발 서버는 다음 요청 때 덮어써집니다)")}
                            />
                        )}
                    </div>
                ))}
            </div>
        </div>
    );
}

function Detail(props: {
    r: DevRequest;
    busy: boolean;
    showLog: boolean;
    setShowLog: (v: boolean) => void;
    followUp: string;
    setFollowUp: (v: string) => void;
    onFollowUp: () => void;
    onCancel: () => void;
    onApprove: () => void;
    onReject: () => void;
}) {
    const { r, busy } = props;
    const canFollowUp = r.status === "DEV_READY" || r.status === "FAILED";

    return (
        <div style={{ ...box, borderTop: "none", borderTopLeftRadius: 0, borderTopRightRadius: 0, marginTop: -6, paddingTop: 16 }}>
            <Section title="요청">
                <div style={pre}>{r.body}</div>
            </Section>

            <div style={{ display: "flex", flexWrap: "wrap", gap: "4px 16px", fontSize: 12, color: MUTED, marginBottom: 12 }}>
                <span>요청 {when(r.createdAt)}</span>
                {r.startedAt && <span>시작 {when(r.startedAt)}</span>}
                {r.finishedAt && <span>종료 {when(r.finishedAt)}</span>}
                {r.promotedAt && <span>운영 반영 {when(r.promotedAt)}</span>}
                {r.branch && <span>브랜치 {r.branch}</span>}
                {r.commitSha && <span>커밋 {r.commitSha.slice(0, 7)}</span>}
                {r.costUsd != null && <span>Claude ${Number(r.costUsd).toFixed(2)}</span>}
            </div>

            {r.error && (
                <Section title="오류">
                    <div style={{ ...pre, color: "#b91c1c" }}>{r.error}</div>
                </Section>
            )}

            {r.summary && (
                <Section title="작업 보고">
                    <div style={pre}>{r.summary}</div>
                </Section>
            )}

            {r.diffStat && (
                <Section title="바뀐 파일">
                    <div style={{ ...pre, fontFamily: "ui-monospace, monospace", fontSize: 12 }}>{r.diffStat}</div>
                </Section>
            )}

            {r.logTail && (
                <div style={{ marginBottom: 12 }}>
                    <button onClick={() => props.setShowLog(!props.showLog)} style={linkBtn}>
                        {props.showLog ? "▾ 진행 로그 접기" : "▸ 진행 로그 보기"}
                    </button>
                    {props.showLog && (
                        <div style={{ ...pre, fontFamily: "ui-monospace, monospace", fontSize: 12, maxHeight: 360, overflow: "auto", background: "#f9fafb", padding: 10, borderRadius: 8, marginTop: 6 }}>
                            {r.logTail}
                        </div>
                    )}
                </div>
            )}

            <div style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
                {r.status === "QUEUED" && <button onClick={props.onCancel} disabled={busy} style={ghostBtn}>취소</button>}
                {r.status === "DEV_READY" && (
                    <>
                        <a href={DEV_URL} target="_blank" rel="noreferrer" style={{ ...ghostBtn, textDecoration: "none", display: "inline-block" }}>
                            개발 서버 열기 ↗
                        </a>
                        <button onClick={props.onApprove} disabled={busy} style={primaryBtn(busy)}>운영 반영</button>
                    </>
                )}
                {(r.status === "DEV_READY" || r.status === "FAILED") && (
                    <button onClick={props.onReject} disabled={busy} style={ghostBtn}>반려</button>
                )}
            </div>

            {canFollowUp && (
                <div style={{ marginTop: 16 }}>
                    <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 6 }}>추가 요청 (같은 작업을 이어서 수정)</div>
                    <textarea
                        value={props.followUp}
                        onChange={(e) => props.setFollowUp(e.target.value)}
                        placeholder="예) 그래프 색을 더 진하게, 모바일에서 범례가 잘려요"
                        rows={3}
                        style={textarea}
                    />
                    <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 8 }}>
                        <button onClick={props.onFollowUp} disabled={busy || !props.followUp.trim()} style={primaryBtn(busy || !props.followUp.trim())}>
                            추가 요청 보내기
                        </button>
                    </div>
                </div>
            )}
        </div>
    );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
    return (
        <div style={{ marginBottom: 12 }}>
            <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 4 }}>{title}</div>
            {children}
        </div>
    );
}

const box: CSSProperties = { border: `1px solid ${BORDER}`, borderRadius: 10, padding: 12, background: "#fff" };
const pre: CSSProperties = { whiteSpace: "pre-wrap", wordBreak: "break-word", fontSize: 14, lineHeight: 1.55 };
const textarea: CSSProperties = {
    width: "100%",
    boxSizing: "border-box",
    border: `1px solid ${BORDER}`,
    borderRadius: 8,
    padding: 10,
    font: "inherit",
    fontSize: 14,
    resize: "vertical",
};
const primaryBtn = (disabled: boolean): CSSProperties => ({
    border: "none",
    borderRadius: 8,
    padding: "8px 16px",
    fontWeight: 600,
    fontSize: 14,
    color: "#fff",
    background: disabled ? "#9ca3af" : "#2563eb",
    cursor: disabled ? "default" : "pointer",
});
const ghostBtn: CSSProperties = {
    border: `1px solid ${BORDER}`,
    borderRadius: 8,
    padding: "8px 14px",
    fontSize: 14,
    color: INK,
    background: "#fff",
    cursor: "pointer",
};
const linkBtn: CSSProperties = { border: "none", background: "none", padding: 0, color: "#2563eb", cursor: "pointer", fontSize: 13 };
