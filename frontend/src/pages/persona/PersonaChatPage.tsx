import {useEffect, useRef, useState } from "react";
import {useTts} from "@/hooks/useTts.ts";

interface ChatMessage {
    role: "user" | "assistant";
    content: string;
}

interface PersonaStatus {
    postCount: number;
    profileGenerated: boolean;
    profileGeneratedAt: string | null;
    profilePostCount: number | null;
}

export default function PersonaChatPage() {
    const [messages, setMessages] = useState<ChatMessage[]>([
        {
            role: "assistant",
            content:
                "안녕하세요! 신지훈의 이력서·포트폴리오와 블로그 글을 바탕으로 신지훈에 대해 알려드리는 챗봇이에요. 관심사, 개발 이력, 취미, 특정 글 내용 등 무엇이든 물어보세요.",
        },
    ]);
    const [input, setInput] = useState("");
    const [sending, setSending] = useState(false);
    const [refreshing, setRefreshing] = useState(false);
    const [status, setStatus] = useState<PersonaStatus | null>(null);
    const scrollRef = useRef<HTMLDivElement>(null);
    const { supported: ttsSupported, speaking, speak, stop } = useTts('persona');
    const [ttsEnabled, setTtsEnabled] = useState(false);

    const loadStatus = async () => {
        try {
            const res = await fetch("/api/persona/status");
            if (res.ok) setStatus(await res.json());
        } catch {
            /* 상태 조회 실패는 무시 */
        }
    };

    useEffect(() => {
        loadStatus();
    }, []);

    useEffect(() => {
        if (scrollRef.current) {
            scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
        }
    }, [messages]);

    const handleRefresh = async () => {
        if (refreshing) return;
        setRefreshing(true);
        setMessages(prev => [
            ...prev,
            { role: "assistant", content: "🔄 블로그를 다시 크롤링하고 페르소나 프로필을 갱신하는 중입니다… (1~2분 소요)" },
        ]);
        try {
            const res = await fetch("/api/persona/refresh", { method: "POST" });
            const data = await res.json();
            setMessages(prev => [
                ...prev,
                {
                    role: "assistant",
                    content: res.ok
                        ? `✅ ${data.message}`
                        : `갱신 실패 (HTTP ${res.status})`,
                },
            ]);
            await loadStatus();
        } catch (e) {
            setMessages(prev => [
                ...prev,
                { role: "assistant", content: "갱신 중 네트워크 오류가 발생했습니다." },
            ]);
        } finally {
            setRefreshing(false);
        }
    };

    const handleSend = async () => {
        const text = input.trim();
        if (!text || sending) return;
        stop(); // 진행 중인 읽기 중단

        const userMsg: ChatMessage = { role: "user", content: text };
        const nextHistory = [...messages, userMsg];
        setMessages(nextHistory);
        setInput("");
        setSending(true);

        try {
            const payload = {
                messages: nextHistory
                    .filter(m => m.role === "user" || m.role === "assistant")
                    .slice(1)
                    .map(m => ({ role: m.role, content: m.content })),
            };

            const res = await fetch("/api/persona/chat", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload),
            });

            if (!res.ok) {
                const errText = await res.text();
                console.error("[페르소나 채팅 실패]", res.status, errText);
                setMessages(prev => [
                    ...prev,
                    { role: "assistant", content: `요청 실패 (HTTP ${res.status}). 잠시 후 다시 시도해주세요.` },
                ]);
                return;
            }

            const data = await res.json();
            const reply = data.reply || "(빈 응답)";
            setMessages(prev => [...prev, { role: "assistant", content: reply }]);
            if (ttsEnabled) speak(reply); // 자동 읽기 (전송 제스처 직후라 autoplay 정책 통과)
        } catch (e) {
            console.error("[페르소나 채팅 네트워크 에러]", e);
            setMessages(prev => [
                ...prev,
                { role: "assistant", content: "네트워크 오류가 발생했습니다." },
            ]);
        } finally {
            setSending(false);
        }
    };

    const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
        if (e.key === "Enter" && !e.shiftKey) {
            e.preventDefault();
            handleSend();
        }
    };

    return (
        <div style={S.page}>
            <style>{`
                .lm-persona-scroll::-webkit-scrollbar { width: 6px; }
                .lm-persona-scroll::-webkit-scrollbar-thumb { background: #475569; border-radius: 3px; }
                @keyframes lm-persona-typing {
                    0%, 60%, 100% { opacity: 0.3; transform: translateY(0); }
                    30% { opacity: 1; transform: translateY(-3px); }
                }
            `}</style>

            <div style={S.card}>
                <div style={S.header}>
                    <div style={S.headerTitle}>
                        <span style={{ fontSize: 18 }}>🧑‍💻</span>
                        <span>신지훈 페르소나 챗</span>
                    </div>
                    <div style={S.headerRight}>
                        <span style={S.statusText}>
                            {status
                                ? `글 ${status.postCount}개 · 프로필 ${status.profileGenerated ? "생성됨" : "없음"}`
                                : "상태 확인 중…"}
                        </span>

                        {ttsSupported && (
                            <button
                                onClick={() => {
                                    const n = !ttsEnabled;
                                    setTtsEnabled(n);
                                    if (!n) stop();
                                }}
                                style={{ ...S.refreshBtn, ...(ttsEnabled ? S.ttsOn : {}) }}
                                title="응답 자동 읽기"
                            >
                                {ttsEnabled ? "🔊 읽기 켜짐" : "🔇 읽기 꺼짐"}
                            </button>
                        )}
                        {speaking && (
                            <button onClick={stop} style={S.refreshBtn} title="읽기 정지">
                                ⏹ 정지
                            </button>
                        )}

                        <button
                            onClick={handleRefresh}
                            disabled={refreshing}
                            style={{ ...S.refreshBtn, opacity: refreshing ? 0.5 : 1 }}
                            title="블로그 재크롤링 + 프로필 재생성"
                        >
                            {refreshing ? "갱신 중…" : "↻ 블로그 새로고침"}
                        </button>
                    </div>
                </div>

                <div ref={scrollRef} className="lm-persona-scroll" style={S.messageList}>
                    {messages.map((m, i) => (
                        <div
                            key={i}
                            style={{
                                ...S.messageRow,
                                justifyContent: m.role === "user" ? "flex-end" : "flex-start",
                            }}
                        >
                            <div
                                style={{
                                    ...S.bubble,
                                    ...(m.role === "user" ? S.bubbleUser : S.bubbleAssistant),
                                }}
                            >
                                {m.content}
                            </div>
                            {ttsSupported && m.role === "assistant" && (
                                <button
                                    onClick={() => speak(m.content)}
                                    style={S.speakBtn}
                                    title="이 답변 읽기"
                                >
                                    🔊
                                </button>
                            )}
                        </div>
                    ))}
                    {sending && (
                        <div style={{ ...S.messageRow, justifyContent: "flex-start" }}>
                            <div style={{ ...S.bubble, ...S.bubbleAssistant, ...S.typingBubble }}>
                                <span style={{ ...S.typingDot, animationDelay: "0s" }} />
                                <span style={{ ...S.typingDot, animationDelay: "0.15s" }} />
                                <span style={{ ...S.typingDot, animationDelay: "0.3s" }} />
                            </div>
                        </div>
                    )}
                </div>

                <div style={S.inputRow}>
                    <textarea
                        value={input}
                        onChange={e => setInput(e.target.value)}
                        onKeyDown={handleKeyDown}
                        placeholder="예: 이 사람 자전거 얼마나 타? / 어떤 개발 글을 썼어?"
                        rows={1}
                        disabled={sending}
                        style={S.input}
                    />
                    <button
                        onClick={handleSend}
                        disabled={sending || !input.trim()}
                        style={{
                            ...S.sendBtn,
                            opacity: sending || !input.trim() ? 0.4 : 1,
                            cursor: sending || !input.trim() ? "default" : "pointer",
                        }}
                    >
                        전송
                    </button>
                </div>
            </div>
        </div>
    );
}

const S: Record<string, React.CSSProperties> = {
    page: {
        padding: 24,
        display: "flex",
        justifyContent: "center",
    },
    card: {
        width: "100%",
        maxWidth: 760,
        height: "calc(100vh - 120px)",
        background: "#1e293b",
        borderRadius: 14,
        border: "1px solid #334155",
        display: "flex",
        flexDirection: "column",
        overflow: "hidden",
    },
    header: {
        padding: "14px 18px",
        borderBottom: "1px solid #334155",
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        gap: 12,
        flexWrap: "wrap",
        background: "linear-gradient(135deg, rgba(37,99,235,0.15), rgba(99,102,241,0.15))",
    },
    headerTitle: {
        display: "flex",
        alignItems: "center",
        gap: 8,
        fontSize: 15,
        fontWeight: 600,
        color: "#f1f5f9",
    },
    headerRight: {
        display: "flex",
        alignItems: "center",
        gap: 10,
    },
    statusText: {
        fontSize: 12,
        color: "#94a3b8",
    },
    refreshBtn: {
        background: "#334155",
        color: "#e2e8f0",
        border: "1px solid #475569",
        borderRadius: 8,
        padding: "6px 10px",
        fontSize: 12,
        cursor: "pointer",
    },
    messageList: {
        flex: 1,
        overflowY: "auto",
        padding: "16px 16px 8px",
        display: "flex",
        flexDirection: "column",
        gap: 10,
    },
    messageRow: {
        display: "flex",
        width: "100%",
    },
    bubble: {
        maxWidth: "85%",
        padding: "10px 14px",
        borderRadius: 12,
        fontSize: 14,
        lineHeight: 1.6,
        whiteSpace: "pre-wrap",
        wordBreak: "break-word",
    },
    bubbleUser: {
        background: "linear-gradient(135deg,#2563eb,#6366f1)",
        color: "#fff",
        borderBottomRightRadius: 4,
    },
    bubbleAssistant: {
        background: "#0f172a",
        color: "#e2e8f0",
        border: "1px solid #334155",
        borderBottomLeftRadius: 4,
    },
    typingBubble: {
        display: "flex",
        alignItems: "center",
        gap: 4,
        padding: "12px 14px",
    },
    typingDot: {
        width: 6,
        height: 6,
        borderRadius: "50%",
        background: "#94a3b8",
        display: "inline-block",
        animation: "lm-persona-typing 1.2s ease-in-out infinite",
    },
    inputRow: {
        padding: 12,
        borderTop: "1px solid #334155",
        display: "flex",
        gap: 8,
        alignItems: "flex-end",
        background: "#0f172a",
    },
    input: {
        flex: 1,
        background: "#1e293b",
        border: "1px solid #334155",
        borderRadius: 10,
        padding: "10px 14px",
        color: "#f1f5f9",
        fontSize: 14,
        resize: "none",
        outline: "none",
        fontFamily: "inherit",
        lineHeight: 1.4,
        maxHeight: 120,
    },
    sendBtn: {
        height: 40,
        padding: "0 18px",
        borderRadius: 10,
        background: "linear-gradient(135deg,#2563eb,#6366f1)",
        border: "none",
        color: "#fff",
        fontSize: 14,
        fontWeight: 600,
        flexShrink: 0,
    },speakBtn: {
        background: "transparent",
        border: "none",
        color: "#94a3b8",
        cursor: "pointer",
        fontSize: 14,
        padding: "0 6px",
        alignSelf: "center",
        flexShrink: 0,
    },
    ttsOn: {
        background: "linear-gradient(135deg,#2563eb,#6366f1)",
        color: "#fff",
        borderColor: "transparent",
    },
};
