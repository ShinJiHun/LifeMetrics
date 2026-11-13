// 개발 요청함 (관리자 전용) — Claude 와 채팅하듯 요청하고 답을 받는다.
// 대화 하나 = 브랜치 하나 + Claude 세션 하나. 메시지 하나 = dev_request 한 행(parentId 로 이어짐).
// 서버 워커가 메시지마다 Claude Code 를 이어서 실행한다. 코드를 바꾸면 빌드 → 개발 서버(8081) 배포,
// 질문이면 답만(ANSWERED). 개발 서버에서 확인한 뒤 "운영 반영"을 누르면 master 머지 → 운영 배포.
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { CSSProperties, KeyboardEvent } from "react";
import { useSearchParams } from "react-router-dom";
import Markdown from "@/components/common/Markdown";
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
const ACCENT = "#2563eb";

const STATUS: Record<DevRequestStatus, { label: string; color: string; bg: string }> = {
    QUEUED: { label: "대기", color: "#4b5563", bg: "#f3f4f6" },
    RUNNING: { label: "작업 중", color: "#1d4ed8", bg: "#dbeafe" },
    DEV_READY: { label: "개발 서버 확인", color: "#047857", bg: "#d1fae5" },
    ANSWERED: { label: "답변", color: "#0369a1", bg: "#e0f2fe" },
    FAILED: { label: "실패", color: "#b91c1c", bg: "#fee2e2" },
    APPROVED: { label: "운영 반영 대기", color: "#7c3aed", bg: "#ede9fe" },
    PROMOTING: { label: "운영 반영 중", color: "#7c3aed", bg: "#ede9fe" },
    DONE: { label: "운영 반영됨", color: "#065f46", bg: "#a7f3d0" },
    REJECTED: { label: "반려", color: "#6b7280", bg: "#f3f4f6" },
    CANCELLED: { label: "취소", color: "#6b7280", bg: "#f3f4f6" },
    SUPERSEDED: { label: "이어짐", color: "#6b7280", bg: "#f3f4f6" },
};
const ACTIVE: DevRequestStatus[] = ["QUEUED", "RUNNING", "APPROVED", "PROMOTING"];
// 서버 DevRequestService.FOLLOWABLE 와 같게
const FOLLOWABLE: DevRequestStatus[] = ["DEV_READY", "ANSWERED", "FAILED", "DONE", "REJECTED", "CANCELLED"];

interface Thread {
    rootId: number;
    title: string;
    turns: DevRequest[]; // 오래된 순
    last: DevRequest;
}

function errorMessage(e: unknown): string {
    const anyErr = e as { response?: { status?: number; data?: { message?: string } } };
    if (anyErr.response?.status === 403) return "관리자 로그인이 필요합니다.";
    return anyErr.response?.data?.message || "요청에 실패했습니다.";
}

const when = (s: string | null) => (s ? s.replace("T", " ").slice(0, 16) : "–");
const shortWhen = (s: string | null) => (s ? s.replace("T", " ").slice(5, 16) : "");

/** parentId 사슬을 따라 대화로 묶는다. 최근 대화가 위로. */
function buildThreads(items: DevRequest[]): Thread[] {
    const byId = new Map(items.map((r) => [r.id, r]));
    const rootOf = (r: DevRequest): number => {
        let cur = r;
        for (let i = 0; i < 100 && cur.parentId != null && byId.has(cur.parentId); i++) cur = byId.get(cur.parentId)!;
        return cur.id;
    };
    const groups = new Map<number, DevRequest[]>();
    for (const r of items) {
        const root = rootOf(r);
        if (!groups.has(root)) groups.set(root, []);
        groups.get(root)!.push(r);
    }
    return [...groups.entries()]
        .map(([rootId, turns]) => {
            turns.sort((a, b) => a.id - b.id);
            return { rootId, title: turns[0].title, turns, last: turns[turns.length - 1] };
        })
        .sort((a, b) => b.last.id - a.last.id);
}

/** 진행 로그에서 사람이 볼 만한 최근 줄(💬 생각, 🔧 도구, ▶ 단계). */
function progressLines(logTail: string | null, n = 6): string[] {
    if (!logTail) return [];
    return logTail
        .split("\n")
        .map((l) => l.replace(/^\d\d:\d\d:\d\d /, ""))
        .filter((l) => /^(💬|🔧|▶|✅|❌)/.test(l))
        .slice(-n);
}

function useNarrow(max = 760) {
    const q = `(max-width: ${max}px)`;
    const [narrow, setNarrow] = useState(() => window.matchMedia(q).matches);
    useEffect(() => {
        const m = window.matchMedia(q);
        const on = () => setNarrow(m.matches);
        m.addEventListener("change", on);
        return () => m.removeEventListener("change", on);
    }, [q]);
    return narrow;
}

function Badge({ status }: { status: DevRequestStatus }) {
    const m = STATUS[status] ?? STATUS.QUEUED;
    return (
        <span style={{ fontSize: 11, fontWeight: 600, padding: "2px 7px", borderRadius: 999, color: m.color, background: m.bg, whiteSpace: "nowrap" }}>
            {m.label}
        </span>
    );
}

export default function DevRequestsPage() {
    const [items, setItems] = useState<DevRequest[]>([]);
    const [params, setParams] = useSearchParams();
    const selectedRoot = params.get("t") ? Number(params.get("t")) : null;
    const [live, setLive] = useState<DevRequest | null>(null); // 진행 중인 마지막 메시지(로그 포함)
    const [error, setError] = useState<string | null>(null);
    const [draft, setDraft] = useState("");
    const [busy, setBusy] = useState(false);
    const narrow = useNarrow();

    const select = useCallback(
        (root: number | null) => {
            const next = new URLSearchParams(params);
            if (root == null) next.delete("t");
            else next.set("t", String(root));
            setParams(next, { replace: true });
            setLive(null);
        },
        [params, setParams],
    );

    const load = useCallback(async () => {
        try {
            setItems(await fetchDevRequests());
            setError(null);
        } catch (e) {
            setError(errorMessage(e));
        }
    }, []);

    useEffect(() => {
        load();
    }, [load]);

    const threads = useMemo(() => buildThreads(items), [items]);
    const thread = threads.find((t) => t.rootId === selectedRoot) ?? null;
    const last = thread?.last ?? null;
    const lastActive = last != null && ACTIVE.includes(last.status);
    const anyActive = items.some((r) => ACTIVE.includes(r.status));

    // 진행 중이면 목록 5초, 보고 있는 대화의 마지막 메시지(로그) 3초마다
    useEffect(() => {
        if (!anyActive) return;
        const t = window.setInterval(load, 5000);
        return () => window.clearInterval(t);
    }, [anyActive, load]);

    const lastId = last?.id;
    useEffect(() => {
        if (lastId == null || !lastActive) {
            setLive(null);
            return;
        }
        let stop = false;
        const tick = async () => {
            try {
                const r = await fetchDevRequest(lastId);
                if (!stop) setLive(r);
            } catch {
                /* 목록 갱신에서 오류를 보여 준다 */
            }
        };
        tick();
        const t = window.setInterval(tick, 3000);
        return () => {
            stop = true;
            window.clearInterval(t);
        };
    }, [lastId, lastActive]);

    // 목록에는 로그가 없으니, 진행 중인 마지막 메시지는 상세(live)로 바꿔 끼운다
    const turns = useMemo(() => {
        if (!thread) return [];
        return thread.turns.map((r) => (live && live.id === r.id ? live : r));
    }, [thread, live]);

    const act = async (fn: () => Promise<DevRequest>, confirmText?: string) => {
        if (confirmText && !window.confirm(confirmText)) return null;
        setBusy(true);
        try {
            const r = await fn();
            await load();
            setError(null);
            return r;
        } catch (e) {
            setError(errorMessage(e));
            return null;
        } finally {
            setBusy(false);
        }
    };

    const canSend = !thread || (last != null && FOLLOWABLE.includes(last.status));
    const send = async () => {
        const text = draft.trim();
        if (!text || busy || !canSend) return;
        const r = await act(() => createDevRequest(text, undefined, last?.id));
        if (r) {
            setDraft("");
            if (!thread) select(r.id);
        }
    };

    const placeholder = !thread
        ? "무엇을 바꾸거나 물어볼까요?\n예) 파워 프로필 화면에 최근 90일과 이전 90일 파워 커브를 겹쳐 보여 줘"
        : last?.status === "QUEUED" || last?.status === "RUNNING"
          ? "Claude 가 작업 중이에요. 끝나면 이어서 보낼 수 있어요."
          : last?.status === "APPROVED" || last?.status === "PROMOTING"
            ? "운영 반영 중이에요."
            : "이어서 말하기 — 수정할 점이나 궁금한 점";

    const showList = !narrow || selectedRoot == null;
    const showChat = !narrow || selectedRoot != null || threads.length === 0;

    return (
        <div style={{ maxWidth: 1100, margin: "0 auto", padding: "8px 0 16px", color: INK }}>
            <div style={{ display: "flex", alignItems: "baseline", gap: 10, flexWrap: "wrap", marginBottom: 8 }}>
                <h2 style={{ margin: 0 }}>🛠 개발 요청함</h2>
                <span style={{ color: MUTED, fontSize: 13 }}>
                    Claude 와 대화하듯 요청하세요. 코드를 바꾸면 <a href={DEV_URL} target="_blank" rel="noreferrer">개발 서버</a>에 올리고,
                    확인 후 <b>운영 반영</b>을 눌러야 운영에 배포됩니다. 한 번에 한 작업씩 처리해요.
                </span>
            </div>

            {error && <div style={{ ...card, borderColor: "#fecaca", background: "#fef2f2", color: "#b91c1c", marginBottom: 8, padding: 10 }}>{error}</div>}

            <div style={{ display: "flex", gap: 12, height: "calc(100vh - 150px)", minHeight: 480 }}>
                {showList && (
                    <div style={{ ...card, width: narrow ? "100%" : 270, flexShrink: 0, display: "flex", flexDirection: "column", overflow: "hidden" }}>
                        <div style={{ padding: 10, borderBottom: `1px solid ${BORDER}` }}>
                            <button onClick={() => select(null)} style={{ ...primaryBtn(false), width: "100%" }}>
                                ＋ 새 대화
                            </button>
                        </div>
                        <div style={{ overflowY: "auto", flex: 1 }}>
                            {threads.length === 0 && <div style={{ color: MUTED, fontSize: 13, padding: 12 }}>아직 대화가 없습니다.</div>}
                            {threads.map((t) => (
                                <button
                                    key={t.rootId}
                                    onClick={() => select(t.rootId)}
                                    style={{
                                        display: "block",
                                        width: "100%",
                                        textAlign: "left",
                                        border: "none",
                                        borderBottom: `1px solid ${BORDER}`,
                                        background: t.rootId === selectedRoot ? "#eff6ff" : "#fff",
                                        padding: "10px 12px",
                                        cursor: "pointer",
                                        font: "inherit",
                                        color: INK,
                                    }}
                                >
                                    <div style={{ fontSize: 14, fontWeight: 500, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                                        {t.title}
                                    </div>
                                    <div style={{ display: "flex", alignItems: "center", gap: 6, marginTop: 4 }}>
                                        <Badge status={t.last.status} />
                                        <span style={{ color: MUTED, fontSize: 11 }}>
                                            {t.turns.length > 1 ? `메시지 ${t.turns.length} · ` : ""}
                                            {shortWhen(t.last.createdAt)}
                                        </span>
                                    </div>
                                </button>
                            ))}
                        </div>
                    </div>
                )}

                {showChat && (
                    <div style={{ ...card, flex: 1, minWidth: 0, display: "flex", flexDirection: "column", overflow: "hidden" }}>
                        <div style={{ padding: "10px 14px", borderBottom: `1px solid ${BORDER}`, display: "flex", alignItems: "center", gap: 8, minHeight: 22 }}>
                            {narrow && (
                                <button onClick={() => select(null)} style={{ ...linkBtn, fontSize: 18 }} aria-label="대화 목록">
                                    ←
                                </button>
                            )}
                            <span style={{ fontWeight: 600, flex: 1, minWidth: 0, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                                {thread ? thread.title : "새 대화"}
                            </span>
                            {thread?.last.branch && <span style={{ color: MUTED, fontSize: 12 }}>{thread.last.branch}</span>}
                        </div>

                        <Messages turns={turns} threadKey={selectedRoot} busy={busy} act={act} />

                        <div style={{ borderTop: `1px solid ${BORDER}`, padding: 10 }}>
                            <Composer value={draft} onChange={setDraft} onSend={send} disabled={busy || !canSend} placeholder={placeholder} />
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
}

function Messages(props: {
    turns: DevRequest[];
    threadKey: number | null;
    busy: boolean;
    act: (fn: () => Promise<DevRequest>, confirmText?: string) => Promise<DevRequest | null>;
}) {
    const { turns, busy, act } = props;
    const endRef = useRef<HTMLDivElement>(null);
    const last = turns[turns.length - 1];
    const scrollKey = `${props.threadKey}:${turns.length}:${last?.status}:${last?.logTail?.length ?? 0}:${last?.summary?.length ?? 0}`;
    useEffect(() => {
        endRef.current?.scrollIntoView({ block: "end" });
    }, [scrollKey]);

    if (turns.length === 0) {
        return (
            <div style={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center", color: MUTED, fontSize: 14, padding: 24, textAlign: "center", lineHeight: 1.7 }}>
                바꾸고 싶은 것이나 궁금한 것을 적어 보내세요.
                <br />
                질문이면 답만 하고, 코드를 바꾸면 개발 서버에 올려 드려요.
            </div>
        );
    }

    return (
        <div style={{ flex: 1, overflowY: "auto", padding: "14px 14px 4px", background: "#f9fafb" }}>
            {turns.map((r, i) => (
                <div key={r.id}>
                    <UserBubble r={r} />
                    <ClaudeBubble
                        r={r}
                        isLast={i === turns.length - 1}
                        busy={busy}
                        onCancel={() => act(() => cancelDevRequest(r.id))}
                        onApprove={() =>
                            act(() => approveDevRequest(r.id), "운영(ride.tho881.me)에 반영합니다. master 에 머지되고 운영 서버가 재시작됩니다. 계속할까요?")
                        }
                        onReject={() => act(() => rejectDevRequest(r.id), "이 작업을 반려할까요? (개발 서버는 다음 작업 때 덮어써집니다)")}
                    />
                </div>
            ))}
            <div ref={endRef} />
        </div>
    );
}

function UserBubble({ r }: { r: DevRequest }) {
    return (
        <div style={{ display: "flex", flexDirection: "column", alignItems: "flex-end", marginBottom: 10 }}>
            <div style={{ maxWidth: "80%", background: ACCENT, color: "#fff", borderRadius: "14px 14px 4px 14px", padding: "8px 12px", ...pre }}>{r.body}</div>
            <span style={{ fontSize: 11, color: MUTED, marginTop: 3 }}>
                #{r.id} · {when(r.createdAt)}
            </span>
        </div>
    );
}

function ClaudeBubble(props: { r: DevRequest; isLast: boolean; busy: boolean; onCancel: () => void; onApprove: () => void; onReject: () => void }) {
    const { r, isLast, busy } = props;
    const [log, setLog] = useState<string | null>(null);
    const [showLog, setShowLog] = useState(false);
    const [showDiff, setShowDiff] = useState(false);

    const working = r.status === "RUNNING" || r.status === "PROMOTING";
    const progress = working || r.status === "APPROVED" ? progressLines(r.logTail) : [];
    const canApprove = r.status === "DEV_READY" || (r.status === "ANSWERED" && !!r.commitSha);
    const canReject = canApprove || r.status === "FAILED";
    const fileCount = r.diffStat ? r.diffStat.trim().split("\n").length - 1 : 0;

    const toggleLog = async () => {
        if (!showLog && log == null) {
            try {
                setLog((await fetchDevRequest(r.id)).logTail ?? "(로그 없음)");
            } catch {
                setLog("(로그를 불러오지 못했습니다)");
            }
        }
        setShowLog(!showLog);
    };

    return (
        <div style={{ display: "flex", gap: 8, alignItems: "flex-start", marginBottom: 16 }}>
            <div style={{ width: 28, height: 28, borderRadius: 999, background: "#fde68a", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 15, flexShrink: 0 }}>
                ✳︎
            </div>
            <div style={{ maxWidth: "85%", minWidth: 0, background: "#fff", border: `1px solid ${BORDER}`, borderRadius: "4px 14px 14px 14px", padding: "8px 12px" }}>
                {r.status === "QUEUED" && (
                    <div style={{ color: MUTED, fontSize: 14 }}>
                        <Dots /> 차례를 기다리는 중…
                    </div>
                )}

                {working && !r.summary && (
                    <div style={{ color: "#1d4ed8", fontSize: 14, marginBottom: progress.length ? 6 : 0 }}>
                        <Dots /> {r.status === "PROMOTING" ? "운영 반영 중" : "작업 중"}
                    </div>
                )}

                {r.summary && (
                    <div style={{ fontSize: 14 }}>
                        <Markdown>{r.summary}</Markdown>
                    </div>
                )}

                {working && r.summary && (
                    <div style={{ color: "#1d4ed8", fontSize: 13, marginTop: 6 }}>
                        <Dots /> {r.status === "PROMOTING" ? "운영 반영 중" : "빌드·개발 서버 배포 중"}
                    </div>
                )}

                {progress.length > 0 && (
                    <div style={{ ...mono, color: MUTED, background: "#f9fafb", borderRadius: 6, padding: "6px 8px", marginTop: 6 }}>
                        {progress.map((l, i) => (
                            <div key={i} style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                                {l}
                            </div>
                        ))}
                    </div>
                )}

                {r.error && <div style={{ ...pre, color: "#b91c1c", fontSize: 13, marginTop: r.summary ? 8 : 0 }}>{r.error}</div>}

                {r.status === "DEV_READY" && (
                    <div style={{ fontSize: 13, color: "#047857", marginTop: 8 }}>
                        ✅ 개발 서버에 올렸어요.{" "}
                        <a href={DEV_URL} target="_blank" rel="noreferrer">
                            열어 보기 ↗
                        </a>
                    </div>
                )}
                {r.status === "DONE" && <div style={{ fontSize: 13, color: "#065f46", marginTop: 8 }}>🎉 운영에 반영됐어요 ({when(r.promotedAt)})</div>}

                {r.diffStat && (
                    <div style={{ marginTop: 8 }}>
                        <button onClick={() => setShowDiff(!showDiff)} style={linkBtn}>
                            {showDiff ? "▾" : "▸"} 바뀐 파일{fileCount > 0 ? ` ${fileCount}개` : ""}
                        </button>
                        {showDiff && <div style={{ ...mono, ...pre, marginTop: 4 }}>{r.diffStat}</div>}
                    </div>
                )}

                <div style={{ display: "flex", flexWrap: "wrap", alignItems: "center", gap: "4px 10px", marginTop: 8, fontSize: 11, color: MUTED }}>
                    <Badge status={r.status} />
                    {r.commitSha && <span>커밋 {r.commitSha.slice(0, 7)}</span>}
                    {r.costUsd != null && <span>${Number(r.costUsd).toFixed(2)}</span>}
                    {r.finishedAt && <span>{when(r.finishedAt)}</span>}
                    {r.status !== "QUEUED" && (
                        <button onClick={toggleLog} style={{ ...linkBtn, fontSize: 11 }}>
                            {showLog ? "로그 접기" : "로그"}
                        </button>
                    )}
                </div>
                {showLog && <div style={{ ...mono, ...pre, maxHeight: 320, overflow: "auto", background: "#f9fafb", padding: 8, borderRadius: 6, marginTop: 6 }}>{working ? r.logTail : log}</div>}

                {isLast && (r.status === "QUEUED" || canApprove || canReject) && (
                    <div style={{ display: "flex", flexWrap: "wrap", gap: 8, marginTop: 10 }}>
                        {r.status === "QUEUED" && (
                            <button onClick={props.onCancel} disabled={busy} style={ghostBtn}>
                                취소
                            </button>
                        )}
                        {canApprove && (
                            <button onClick={props.onApprove} disabled={busy} style={primaryBtn(busy)}>
                                운영 반영
                            </button>
                        )}
                        {canReject && (
                            <button onClick={props.onReject} disabled={busy} style={ghostBtn}>
                                반려
                            </button>
                        )}
                    </div>
                )}
            </div>
        </div>
    );
}

function Composer(props: { value: string; onChange: (v: string) => void; onSend: () => void; disabled: boolean; placeholder: string }) {
    const onKey = (e: KeyboardEvent<HTMLTextAreaElement>) => {
        // 한글 조합 중 Enter 는 무시. Enter 보내기, Shift+Enter 줄바꿈
        if (e.key === "Enter" && !e.shiftKey && !e.nativeEvent.isComposing) {
            e.preventDefault();
            props.onSend();
        }
    };
    const empty = !props.value.trim();
    return (
        <div style={{ display: "flex", gap: 8, alignItems: "flex-end" }}>
            <textarea
                value={props.value}
                onChange={(e) => props.onChange(e.target.value)}
                onKeyDown={onKey}
                placeholder={props.placeholder}
                rows={Math.min(8, Math.max(2, props.value.split("\n").length))}
                style={textarea}
            />
            <button onClick={props.onSend} disabled={props.disabled || empty} style={{ ...primaryBtn(props.disabled || empty), height: 40, flexShrink: 0 }}>
                보내기
            </button>
        </div>
    );
}

function Dots() {
    return <span style={{ display: "inline-block", animation: "devreq-pulse 1.2s ease-in-out infinite" }}>●<style>{"@keyframes devreq-pulse{0%,100%{opacity:.25}50%{opacity:1}}"}</style></span>;
}

const card: CSSProperties = { border: `1px solid ${BORDER}`, borderRadius: 10, background: "#fff" };
const pre: CSSProperties = { whiteSpace: "pre-wrap", wordBreak: "break-word", fontSize: 14, lineHeight: 1.55 };
const mono: CSSProperties = { fontFamily: "ui-monospace, monospace", fontSize: 12, lineHeight: 1.5 };
const textarea: CSSProperties = {
    flex: 1,
    width: "100%",
    boxSizing: "border-box",
    border: `1px solid ${BORDER}`,
    borderRadius: 10,
    padding: "9px 11px",
    font: "inherit",
    fontSize: 14,
    resize: "none",
};
const primaryBtn = (disabled: boolean): CSSProperties => ({
    border: "none",
    borderRadius: 8,
    padding: "8px 16px",
    fontWeight: 600,
    fontSize: 14,
    color: "#fff",
    background: disabled ? "#9ca3af" : ACCENT,
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
const linkBtn: CSSProperties = { border: "none", background: "none", padding: 0, color: ACCENT, cursor: "pointer", fontSize: 13 };
