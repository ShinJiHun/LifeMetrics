// 애슐리트 페르소나 챗 (관리자 전용). 질문마다 서버가 최근 운동·라이딩·랜도너스·체성분·식단 기록을 붙여 AI 에 보낸다.
import { useEffect, useRef, useState } from "react";
import type { CSSProperties, FormEvent } from "react";
import api from "@/lib/axios";

type ToolCall = { name: string; input?: Record<string, unknown>; error?: boolean };
type Message = { role: "user" | "assistant"; content: string; toolCalls?: ToolCall[] };

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
        "안녕하세요, 애슐리트 페르소나 코치입니다. 최근 2주 식단·에너지 수지, 90일 라이딩, 랜도너스 기록, 웨이트, 체성분과 목표를 보고 답할게요.",
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
    const bottomRef = useRef<HTMLDivElement>(null);

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
            const { data } = await api.post<{ reply?: string; toolCalls?: ToolCall[] }>("/athlete/chat", {
                messages: next.slice(1).map(({ role, content }) => ({ role, content })),
            });
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
                <button
                    type="button"
                    style={S.resetBtn}
                    onClick={() => setMessages([GREETING])}
                    disabled={sending || messages.length <= 1}
                >
                    새 대화
                </button>
            </header>

            <div style={S.log}>
                {messages.map((m, i) => (
                    <div key={i} style={m.role === "user" ? S.userRow : S.botRow}>
                        <div style={m.role === "user" ? S.userBubble : S.botBubble}>
                            {m.content}
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
