// src/components/common/AdminBadge.tsx
//
// 우측 상단 자물쇠 배지. 현재 모드(관리자/보기 전용)를 항상 보여주고,
// 클릭하면 비밀번호 입력창이 열린다. 관리자 상태에서 누르면 로그아웃.

import { useState } from "react";
import { useAdmin } from "@/lib/admin";

export default function AdminBadge({ style }: { style?: React.CSSProperties }) {
    const { isAdmin, loading, login, logout } = useAdmin();
    const [open, setOpen] = useState(false);
    const [password, setPassword] = useState("");
    const [error, setError] = useState("");
    const [busy, setBusy] = useState(false);

    if (loading) return null;

    const submit = async (e: React.FormEvent) => {
        e.preventDefault();
        setBusy(true);
        setError("");
        const ok = await login(password);
        setBusy(false);
        if (ok) {
            setOpen(false);
            setPassword("");
        } else {
            setError("비밀번호가 올바르지 않습니다.");
        }
    };

    const handleClick = async () => {
        if (isAdmin) {
            if (window.confirm("보기 전용 모드로 전환할까요?")) await logout();
        } else {
            setOpen(true);
            setError("");
        }
    };

    return (
        <>
            <button
                type="button"
                onClick={handleClick}
                title={isAdmin ? "관리자 모드 — 눌러서 보기 전용으로 전환" : "보기 전용 — 눌러서 관리자 로그인"}
                style={{
                    display: "inline-flex",
                    alignItems: "center",
                    gap: 6,
                    padding: "6px 12px",
                    borderRadius: 999,
                    border: "none",
                    cursor: "pointer",
                    fontSize: 12,
                    fontWeight: 700,
                    fontFamily: "inherit",
                    background: isAdmin ? "#dcfce7" : "#f1f5f9",
                    color: isAdmin ? "#15803d" : "#64748b",
                    ...style,
                }}
            >
                {isAdmin ? "🔓 관리자" : "🔒 보기 전용"}
            </button>

            {open && (
                <div
                    onClick={() => setOpen(false)}
                    style={{
                        position: "fixed",
                        inset: 0,
                        background: "rgba(15,23,42,0.45)",
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                        zIndex: 1000,
                    }}
                >
                    <form
                        onClick={(e) => e.stopPropagation()}
                        onSubmit={submit}
                        style={{
                            background: "#fff",
                            borderRadius: 16,
                            padding: 28,
                            width: "min(360px, 90vw)",
                            boxShadow: "0 20px 50px rgba(15,23,42,0.25)",
                            fontFamily: "inherit",
                        }}
                    >
                        <h2 style={{ margin: 0, fontSize: 18, fontWeight: 800, color: "#1e293b" }}>
                            관리자 로그인
                        </h2>
                        <p style={{ margin: "8px 0 20px", fontSize: 13, color: "#64748b" }}>
                            비밀번호를 입력하면 기록과 글을 작성할 수 있습니다.
                        </p>

                        <input
                            type="password"
                            value={password}
                            autoFocus
                            onChange={(e) => setPassword(e.target.value)}
                            placeholder="비밀번호"
                            style={{
                                width: "100%",
                                boxSizing: "border-box",
                                padding: "11px 14px",
                                borderRadius: 10,
                                border: "1px solid #cbd5e1",
                                fontSize: 14,
                                fontFamily: "inherit",
                            }}
                        />

                        {error && (
                            <div style={{ marginTop: 10, fontSize: 13, color: "#dc2626" }}>{error}</div>
                        )}

                        <div style={{ display: "flex", gap: 8, marginTop: 20 }}>
                            <button
                                type="button"
                                onClick={() => setOpen(false)}
                                style={{
                                    flex: 1,
                                    padding: "11px 0",
                                    borderRadius: 10,
                                    border: "1px solid #cbd5e1",
                                    background: "#fff",
                                    color: "#475569",
                                    fontSize: 14,
                                    fontWeight: 600,
                                    fontFamily: "inherit",
                                    cursor: "pointer",
                                }}
                            >
                                취소
                            </button>
                            <button
                                type="submit"
                                disabled={busy || !password}
                                style={{
                                    flex: 1,
                                    padding: "11px 0",
                                    borderRadius: 10,
                                    border: "none",
                                    background: busy || !password ? "#93c5fd" : "#2563eb",
                                    color: "#fff",
                                    fontSize: 14,
                                    fontWeight: 700,
                                    fontFamily: "inherit",
                                    cursor: busy || !password ? "default" : "pointer",
                                }}
                            >
                                {busy ? "확인 중..." : "로그인"}
                            </button>
                        </div>
                    </form>
                </div>
            )}
        </>
    );
}