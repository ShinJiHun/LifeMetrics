// 애슐리트 페르소나 챗 (관리자 전용). 서버가 기록을 붙이거나(context 모드) AI 가 필요한 기록을 도구로 조회한다(tools 모드).
import { useEffect, useRef, useState } from "react";
import type { CSSProperties, FormEvent } from "react";
import api from "@/lib/axios";
import ChatMarkdown from "@/components/common/ChatMarkdown";

type ToolCall = { name: string; input?: Record<string, unknown>; error?: boolean };
type Message = { role: "user" | "assistant"; content: string; toolCalls?: ToolCall[] };
type Session = { id: number; title: string; mode: string | null; updatedAt: string };

const when = (iso: string) => iso.replace("T", " ").slice(0, 16);

// 근거 표시: "get_activities 2026-08-01~2026-08-31" 처럼 도구 이름 + 주요 입력
const toolLabel = (c: ToolCall) => {
    const i = c.input ?? {};
    const range = i.from ? `${String(i.from)}~${i.to ? String(i.to) : "오늘"}` : "";
    const extra = ["ride_type", "type"].map((k) => i[k]).filter(Boolean).join(" ");
    return [c.name, range, extra].filter(Boolean).join(" ") + (c.error ? " (오류)" : "");
};

const GREETING: Message = {
    role: "assistant",
    content:
        "안녕하세요, 애슐리트 페르소나 코치입니다. 라이딩·랜도너스, 식단·에너지 수지, 웨이트, 체성분과 목표, 치료·피팅 기록을 근거로 답할게요.",
};

const SUGGESTIONS = [
    "최근 2주 에너지 수지 보고 이번 주 다이어트 전략 짜줘",
    "목표 체성분까지 지금 페이스면 괜찮아?",
    "다음 브레베 대비해서 훈련이랑 식단 어떻게 할까?",
    "웨이트랑 라이딩 비중 어떻게 가져가는 게 좋을까?",
];

export default function AthleteChatPage() {
    const [messages, setMessages] = useState<Message[]>([GREETING]);
    const [input, setInput] = useState("");
    const [sending, setSending] = useState(false);
    // 저장된 대화: 첫 답변을 받으면 서버가 id 를 준다. 이후 질문은 같은 대화에 이어서 저장된다.
    const [sessionId, setSessionId] = useState<number | null>(null);
    const [sessions, setSessions] = useState<Session[] | null>(null);
    const [showList, setShowList] = useState(false);
    const [listError, setListError] = useState<string | null>(null);
    const bottomRef = useRef<HTMLDivElement>(null);

    const loadSessions = async () => {
        setListError(null);
        try {
            const { data } = await api.get<Session[]>("/athlete/chat/sessions");
            setSessions(data);
        } catch (e) {
            const status = (e as { response?: { status?: number } }).response?.status;
            setListError(status === 403 ? "관리자 로그인이 필요합니다." : "대화 목록을 불러오지 못했습니다.");
        }
    };

    const toggleList = () => {
        const next = !showList;
        setShowList(next);
        if (next) void loadSessions();
    };

    const newChat = () => {
        setMessages([GREETING]);
        setSessionId(null);
    };

    const openSession = async (id: number) => {
        try {
            const { data } = await api.get<Message[]>(`/athlete/chat/sessions/${id}`);
            setMessages([GREETING, ...data]);
            setSessionId(id);
            setShowList(false);
        } catch {
            setListError("대화를 열지 못했습니다.");
        }
    };

    const deleteSession = async (id: number) => {
        if (!window.confirm("이 대화를 삭제할까요?")) return;
        try {
            await api.delete(`/athlete/chat/sessions/${id}`);
            if (id === sessionId) newChat();
            await loadSessions();
        } catch {
            setListError("삭제하지 못했습니다.");
        }
    };

    useEffect(() => {
        bottomRef.current?.scrollIntoView({ behavior: "smooth" });
    }, [messages, sending]);

    const send = async (text: string) => {
        const content = text.trim();
        if (!content || sending) return;
        const next: Message[] = [...messages, { role: "user", content }];
        setMessages(next);
        setInput("");
        setSending(true);
        try {
            // 첫 인사는 화면용이라 보내지 않는다
            const { data } = await api.post<{ reply?: string; toolCalls?: ToolCall[]; sessionId?: number | null }>(
                "/athlete/chat",
                { sessionId, messages: next.slice(1).map(({ role, content }) => ({ role, content })) },
            );
            if (data.sessionId) setSessionId(data.sessionId);
            setMessages((prev) => [
                ...prev,
                { role: "assistant", content: data.reply || "(빈 응답)", toolCalls: data.toolCalls },
            ]);
        } catch (e) {
            const status = (e as { response?: { status?: number } }).response?.status;
            const msg =
                status === 401 || status === 403
                    ? "관리자 로그인이 필요합니다."
                    : `요청에 실패했습니다${status ? ` (HTTP ${status})` : ""}. 잠시 후 다시 시도해 주세요.`;
            setMessages((prev) => [...prev, { role: "assistant", content: msg }]);
        } finally {
            setSending(false);
        }
    };

    const onSubmit = (e: FormEvent) => {
        e.preventDefault();
        void send(input);
    };

    return (
        <div style={S.page}>
            <header style={S.header}>
                <h2 style={S.title}>🤖 애슐리트 페르소나</h2>
                <div style={S.headerBtns}>
                    <button type="button" style={S.resetBtn} onClick={toggleList} disabled={sending}>
                        {showList ? "목록 닫기" : "대화 목록"}
                    </button>
                    <button type="button" style={S.resetBtn} onClick={newChat} disabled={sending || messages.length <= 1}>
                        새 대화
                    </button>
                </div>
            </header>

            {showList && (
                <div style={S.listPanel}>
                    {listError && <div style={S.listEmpty}>{listError}</div>}
                    {!listError && sessions === null && <div style={S.listEmpty}>불러오는 중…</div>}
                    {!listError && sessions?.length === 0 && <div style={S.listEmpty}>저장된 대화가 없습니다.</div>}
                    {sessions?.map((s) => (
                        <div key={s.id} style={s.id === sessionId ? { ...S.listItem, ...S.listItemActive } : S.listItem}>
                            <button type="button" style={S.listOpen} onClick={() => void openSession(s.id)}>
                                <span style={S.listTitle}>{s.title}</span>
                                <span style={S.listDate}>{when(s.updatedAt)}</span>
                            </button>
                            <button type="button" style={S.listDelete} onClick={() => void deleteSession(s.id)} aria-label="대화 삭제">
                                삭제
                            </button>
                        </div>
                    ))}
                </div>
            )}

            <div style={S.log}>
                {messages.map((m, i) => (
                    <div key={i} style={m.role === "user" ? S.userRow : S.botRow}>
                        <div style={m.role === "user" ? S.userBubble : S.botBubble}>
                            {m.role === "assistant" ? <ChatMarkdown>{m.content}</ChatMarkdown> : m.content}
                            {m.toolCalls && m.toolCalls.length > 0 && (
                                <div style={S.sources}>근거: {m.toolCalls.map(toolLabel).join(" · ")}</div>
                            )}
                        </div>
                    </div>
                ))}
                {sending && (
                    <div style={S.botRow}>
                        <div style={{ ...S.botBubble, color: "#8a90a6" }}>기록 살펴보는 중…</div>
                    </div>
                )}
                {messages.length <= 1 && (
                    <div style={S.suggestions}>
                        {SUGGESTIONS.map((s) => (
                            <button key={s} type="button" style={S.chip} onClick={() => void send(s)}>
                                {s}
                            </button>
                        ))}
                    </div>
                )}
                <div ref={bottomRef} />
            </div>

            <form onSubmit={onSubmit} style={S.form}>
                <textarea
                    value={input}
                    onChange={(e) => setInput(e.target.value)}
                    onKeyDown={(e) => {
                        if (e.key === "Enter" && !e.shiftKey && !e.nativeEvent.isComposing) {
                            e.preventDefault();
                            void send(input);
                        }
                    }}
                    placeholder="예: 이번 주 식단 어떻게 가져갈까? (Shift+Enter 줄바꿈)"
                    rows={2}
                    style={S.input}
                    disabled={sending}
                />
                <button type="submit" style={S.sendBtn} disabled={sending || !input.trim()}>
                    보내기
                </button>
            </form>
        </div>
    );
}

const bubble: CSSProperties = {
    maxWidth: "min(720px, 85%)",
    padding: "10px 14px",
    borderRadius: 14,
    lineHeight: 1.6,
    fontSize: 14,
    whiteSpace: "pre-wrap",
    wordBreak: "break-word",
};

const S: Record<string, CSSProperties> = {
    page: {
        display: "flex",
        flexDirection: "column",
        height: "calc(100vh - 48px)",
        maxWidth: 900,
        margin: "0 auto",
        padding: "16px",
        boxSizing: "border-box",
    },
    header: { display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: 12 },
    title: { margin: 0, fontSize: 20 },
    headerBtns: { display: "flex", gap: 6 },
    listPanel: {
        maxHeight: 260,
        overflowY: "auto",
        border: "1px solid #e5e7ef",
        borderRadius: 10,
        marginBottom: 8,
        background: "#fff",
    },
    listEmpty: { padding: "12px 14px", color: "#6b7186", fontSize: 13 },
    listItem: { display: "flex", alignItems: "center", borderBottom: "1px solid #eef0f5" },
    listItemActive: { background: "#eef2ff" },
    listOpen: {
        flex: 1,
        display: "flex",
        justifyContent: "space-between",
        gap: 12,
        padding: "10px 14px",
        border: "none",
        background: "transparent",
        cursor: "pointer",
        textAlign: "left",
        minWidth: 0,
    },
    listTitle: { fontSize: 14, color: "#1e2235", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" },
    listDate: { fontSize: 12, color: "#8a90a6", whiteSpace: "nowrap" },
    listDelete: {
        border: "none",
        background: "transparent",
        color: "#b91c1c",
        fontSize: 12,
        padding: "10px 14px",
        cursor: "pointer",
    },
    resetBtn: {
        border: "1px solid #d6d9e4",
        background: "#fff",
        borderRadius: 8,
        padding: "6px 12px",
        cursor: "pointer",
        fontSize: 13,
    },
    log: {
        flex: 1,
        overflowY: "auto",
        display: "flex",
        flexDirection: "column",
        gap: 10,
        padding: "12px 4px",
    },
    userRow: { display: "flex", justifyContent: "flex-end" },
    botRow: { display: "flex", justifyContent: "flex-start" },
    userBubble: { ...bubble, background: "#2563eb", color: "#fff", borderBottomRightRadius: 4 },
    botBubble: { ...bubble, background: "#f1f3f9", color: "#1e2235", borderBottomLeftRadius: 4 },
    sources: { marginTop: 8, paddingTop: 6, borderTop: "1px solid #dfe3ee", fontSize: 12, color: "#6b7186" },
    suggestions: { display: "flex", flexWrap: "wrap", gap: 8, marginTop: 4 },
    chip: {
        border: "1px solid #c7d2fe",
        background: "#eef2ff",
        color: "#3730a3",
        borderRadius: 999,
        padding: "6px 12px",
        cursor: "pointer",
        fontSize: 13,
        textAlign: "left",
    },
    form: { display: "flex", gap: 8, paddingTop: 8, borderTop: "1px solid #e5e7ef" },
    input: {
        flex: 1,
        resize: "none",
        border: "1px solid #d6d9e4",
        borderRadius: 10,
        padding: "10px 12px",
        fontSize: 14,
        fontFamily: "inherit",
    },
    sendBtn: {
        border: "none",
        background: "#2563eb",
        color: "#fff",
        borderRadius: 10,
        padding: "0 18px",
        fontWeight: 700,
        cursor: "pointer",
    },
};
