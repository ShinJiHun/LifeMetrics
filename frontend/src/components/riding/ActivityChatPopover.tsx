import {useEffect, useRef, useState} from "react";
import {useTts} from "@/hooks/useTts.ts";

interface ChatMessage {
    role: "user" | "assistant";
    content: string;
}

type ChatMode = "text" | "voice";

interface Props {
    activityId: number;
    userId?: number;
}

export default function ActivityChatPopover({activityId, userId = 1}: Props) {
    const [open, setOpen] = useState(false);
    const {supported: ttsSupported, speaking, speak, stop} = useTts('riding');
    const [mode, setMode] = useState<ChatMode>("text");
    const [messages, setMessages] = useState<ChatMessage[]>([
        {
            role: "assistant",
            content: "이 라이딩 기록에 대해 궁금한 점을 물어보세요. 페이싱, 강도, 회복, 다음 훈련 추천 등 무엇이든 답해드립니다.",
        },
    ]);
    const [input, setInput] = useState("");
    const [sending, setSending] = useState(false);
    const scrollRef = useRef<HTMLDivElement>(null);
    const inputRef = useRef<HTMLTextAreaElement>(null);

    useEffect(() => {
        if (scrollRef.current) {
            scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
        }
    }, [messages, open]);

    useEffect(() => {
        if (open && inputRef.current) inputRef.current.focus();
    }, [open]);

    // 텍스트 모드로 전환되면 진행 중인 읽기 중단
    const switchMode = (next: ChatMode) => {
        setMode(next);
        if (next === "text") stop();
    };

    const handleSend = async () => {
        const text = input.trim();
        if (!text || sending) return;
        stop(); // 진행 중인 읽기 중단

        const userMsg: ChatMessage = {role: "user", content: text};
        const nextHistory = [...messages, userMsg];
        setMessages(nextHistory);
        setInput("");
        setSending(true);

        try {
            const payload = {
                messages: nextHistory
                    .filter(m => m.role === "user" || m.role === "assistant")
                    .slice(1)
                    .map(m => ({role: m.role, content: m.content})),
            };

            const res = await fetch(`/api/ai/chat/activity/${activityId}?userId=${userId}`, {
                method: "POST",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify(payload),
            });

            if (!res.ok) {
                const errText = await res.text();
                console.error("[채팅 응답 실패]", res.status, errText);
                setMessages(prev => [...prev, {
                    role: "assistant",
                    content: `요청 실패 (HTTP ${res.status}). 잠시 후 다시 시도해주세요.`,
                }]);
                return;
            }

            const data = await res.json();
            const reply = data.reply || "(빈 응답)";
            setMessages(prev => [...prev, {
                role: "assistant",
                content: reply,
            }]);
            // 보이스 버전일 때만 자동 읽기 (전송 제스처 직후라 autoplay 정책 통과)
            if (mode === "voice" && ttsSupported) speak(reply);
        } catch (e) {
            console.error("[채팅 네트워크 에러]", e);
            setMessages(prev => [...prev, {
                role: "assistant",
                content: "네트워크 오류가 발생했습니다.",
            }]);
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
        <>
            <style>{`
                @keyframes lm-chat-pulse {
                    0%, 100% { box-shadow: 0 4px 20px rgba(99, 102, 241, 0.4); }
                    50% { box-shadow: 0 4px 28px rgba(99, 102, 241, 0.7); }
                }
                @keyframes lm-chat-typing {
                    0%, 60%, 100% { opacity: 0.3; transform: translateY(0); }
                    30% { opacity: 1; transform: translateY(-3px); }
                }
                .lm-chat-scroll::-webkit-scrollbar { width: 6px; }
                .lm-chat-scroll::-webkit-scrollbar-thumb {
                    background: #475569; border-radius: 3px;
                }
            `}</style>

            {/* FAB 버튼 */}
            <button
                onClick={() => setOpen(o => !o)}
                style={{...S.fab, ...(open ? S.fabOpen : {})}}
                aria-label="라이딩 채팅 열기"
                title="이 라이딩에 대해 물어보기"
            >
                {open ? (
                    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2.5">
                        <path d="M18 6L6 18M6 6l12 12"/>
                    </svg>
                ) : (
                    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2">
                        <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"/>
                    </svg>
                )}
            </button>

            {/* 채팅 패널 */}
            {open && (
                <div style={S.panel}>
                    <div style={S.header}>
                        <div style={S.headerTitle}>
                            <span style={S.headerIcon}>🤖</span>
                            <span>라이딩 코치 챗</span>
                        </div>
                        <div style={S.headerRight}>
                            {speaking && (
                                <button onClick={stop} style={S.ttsBtn} title="읽기 정지">⏹</button>
                            )}
                            <button onClick={() => setOpen(false)} style={S.closeBtn}>×</button>
                        </div>
                    </div>

                    {/* 모드 탭 */}
                    <div style={S.tabBar} role="tablist">
                        <button
                            role="tab"
                            aria-selected={mode === "text"}
                            onClick={() => switchMode("text")}
                            style={{...S.tab, ...(mode === "text" ? S.tabActive : {})}}
                        >
                            💬 텍스트 버전
                        </button>
                        <button
                            role="tab"
                            aria-selected={mode === "voice"}
                            onClick={() => switchMode("voice")}
                            disabled={!ttsSupported}
                            title={ttsSupported ? "답변과 동시에 음성으로 읽어줍니다" : "이 브라우저는 음성 합성을 지원하지 않습니다"}
                            style={{
                                ...S.tab,
                                ...(mode === "voice" ? S.tabActive : {}),
                                ...(!ttsSupported ? S.tabDisabled : {}),
                            }}
                        >
                            🔊 보이스 버전
                        </button>
                    </div>

                    <div ref={scrollRef} className="lm-chat-scroll" style={S.messageList}>
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
                                {/* 보이스 버전에서만 답변별 수동 읽기 버튼 노출 */}
                                {mode === "voice" && ttsSupported && m.role === "assistant" && (
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
                            <div style={{...S.messageRow, justifyContent: "flex-start"}}>
                                <div style={{...S.bubble, ...S.bubbleAssistant, ...S.typingBubble}}>
                                    <span style={{...S.typingDot, animationDelay: "0s"}}/>
                                    <span style={{...S.typingDot, animationDelay: "0.15s"}}/>
                                    <span style={{...S.typingDot, animationDelay: "0.3s"}}/>
                                </div>
                            </div>
                        )}
                    </div>

                    <div style={S.inputRow}>
                        <textarea
                            ref={inputRef}
                            value={input}
                            onChange={e => setInput(e.target.value)}
                            onKeyDown={handleKeyDown}
                            placeholder="예: 이 라이딩에서 페이싱은 어땠어?"
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
                            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#fff" strokeWidth="2.2">
                                <path d="M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z"/>
                            </svg>
                        </button>
                    </div>
                </div>
            )}
        </>
    );
}

const S: Record<string, React.CSSProperties> = {
    fab: {
        position: "fixed",
        bottom: 24,
        right: 24,
        width: 56,
        height: 56,
        borderRadius: "50%",
        background: "linear-gradient(135deg,#2563eb,#6366f1)",
        border: "none",
        cursor: "pointer",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        boxShadow: "0 4px 20px rgba(99, 102, 241, 0.4)",
        animation: "lm-chat-pulse 2.4s ease-in-out infinite",
        zIndex: 1000,
        transition: "transform 0.2s",
    },
    fabOpen: {
        background: "#334155",
        animation: "none",
        transform: "scale(0.92)",
    },
    panel: {
        position: "fixed",
        bottom: 92,
        right: 24,
        width: 380,
        maxWidth: "calc(100vw - 48px)",
        height: 540,
        maxHeight: "calc(100vh - 120px)",
        background: "#1e293b",
        borderRadius: 14,
        border: "1px solid #334155",
        boxShadow: "0 12px 40px rgba(0, 0, 0, 0.4)",
        display: "flex",
        flexDirection: "column",
        overflow: "hidden",
        zIndex: 999,
    },
    header: {
        padding: "12px 16px",
        borderBottom: "1px solid #334155",
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        background: "linear-gradient(135deg, rgba(37,99,235,0.15), rgba(99,102,241,0.15))",
    },
    headerTitle: {
        display: "flex",
        alignItems: "center",
        gap: 8,
        fontSize: 14,
        fontWeight: 600,
        color: "#f1f5f9",
    },
    headerIcon: {
        fontSize: 16,
    },
    headerRight: {
        display: "flex",
        alignItems: "center",
        gap: 6,
    },
    closeBtn: {
        background: "transparent",
        border: "none",
        color: "#94a3b8",
        fontSize: 22,
        lineHeight: 1,
        cursor: "pointer",
        padding: "0 4px",
    },
    tabBar: {
        display: "flex",
        borderBottom: "1px solid #334155",
        background: "#1e293b",
    },
    tab: {
        flex: 1,
        padding: "10px 0",
        background: "transparent",
        border: "none",
        borderBottom: "2px solid transparent",
        color: "#94a3b8",
        fontSize: 13,
        fontWeight: 600,
        cursor: "pointer",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        gap: 6,
        transition: "color 0.15s, border-color 0.15s, background 0.15s",
    },
    tabActive: {
        color: "#f1f5f9",
        borderBottom: "2px solid #6366f1",
        background: "linear-gradient(135deg, rgba(37,99,235,0.12), rgba(99,102,241,0.12))",
    },
    tabDisabled: {
        opacity: 0.4,
        cursor: "not-allowed",
    },
    ttsBtn: {
        background: "transparent",
        border: "1px solid #334155",
        borderRadius: 8,
        color: "#cbd5e1",
        fontSize: 13,
        cursor: "pointer",
        padding: "3px 7px",
        lineHeight: 1,
    },
    messageList: {
        flex: 1,
        overflowY: "auto",
        padding: "14px 14px 8px",
        display: "flex",
        flexDirection: "column",
        gap: 10,
    },
    messageRow: {
        display: "flex",
        width: "100%",
        alignItems: "flex-end",
    },
    bubble: {
        maxWidth: "85%",
        padding: "9px 12px",
        borderRadius: 12,
        fontSize: 13,
        lineHeight: 1.5,
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
    speakBtn: {
        background: "transparent",
        border: "none",
        color: "#64748b",
        fontSize: 13,
        cursor: "pointer",
        padding: "0 4px",
        alignSelf: "flex-end",
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
        animation: "lm-chat-typing 1.2s ease-in-out infinite",
    },
    inputRow: {
        padding: 10,
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
        padding: "8px 12px",
        color: "#f1f5f9",
        fontSize: 13,
        resize: "none",
        outline: "none",
        fontFamily: "inherit",
        lineHeight: 1.4,
        maxHeight: 96,
    },
    sendBtn: {
        width: 36,
        height: 36,
        borderRadius: 10,
        background: "linear-gradient(135deg,#2563eb,#6366f1)",
        border: "none",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        flexShrink: 0,
    },
};
