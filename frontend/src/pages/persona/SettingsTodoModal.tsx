import { useState } from "react";
import api from "@/lib/axios";

interface Props {
    onClose: () => void;
}

type Step = "password" | "todos";

/**
 * 첫 페이지 설정(⚙️) 모달.
 * 1) 비밀번호 입력 → 검증
 * 2) 해야 할 일 입력 → 서버가 AI로 정리 후 Slack 전송("확인 …" 메시지)
 */
export default function SettingsTodoModal({ onClose }: Props) {
    const [step, setStep] = useState<Step>("password");
    const [password, setPassword] = useState("");
    const [items, setItems] = useState<string[]>([""]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [done, setDone] = useState<string | null>(null);

    const verify = async () => {
        setError(null);
        setLoading(true);
        try {
            await api.post("/todos/verify", { password });
            setStep("todos");
        } catch (e: unknown) {
            const status = (e as { response?: { status?: number } })?.response?.status;
            setError(
                status === 401
                    ? "비밀번호가 올바르지 않습니다."
                    : "서버에 연결할 수 없습니다. 백엔드가 실행 중인지 확인하세요."
            );
        } finally {
            setLoading(false);
        }
    };

    const updateItem = (i: number, value: string) => {
        setItems((prev) => prev.map((v, idx) => (idx === i ? value : v)));
    };
    const addItem = () => setItems((prev) => [...prev, ""]);
    const removeItem = (i: number) =>
        setItems((prev) => (prev.length === 1 ? prev : prev.filter((_, idx) => idx !== i)));

    const submit = async () => {
        const clean = items.map((s) => s.trim()).filter(Boolean);
        if (clean.length === 0) {
            setError("할 일을 하나 이상 입력하세요.");
            return;
        }
        setError(null);
        setLoading(true);
        try {
            const res = await api.post("/todos/notify", { password, items: clean });
            if (res.data?.sent) {
                setDone(`${res.data.count}개 항목을 정리해 Slack으로 보냈어요. 메시지를 확인하세요!`);
            } else {
                setError("전송에 실패했어요. Slack Webhook 설정을 확인하세요.");
            }
        } catch (e: unknown) {
            const msg =
                (e as { response?: { data?: { error?: string } } })?.response?.data?.error ??
                "전송 중 오류가 발생했습니다.";
            setError(msg);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="lm-modal-backdrop" onClick={onClose}>
            <style>{`
        .lm-modal-backdrop {
          position: fixed; inset: 0; z-index: 1000;
          background: rgba(20, 24, 40, 0.45);
          display: flex; align-items: center; justify-content: center;
          padding: 20px;
          font-family: "Pretendard", -apple-system, "Apple SD Gothic Neo", system-ui, sans-serif;
        }
        .lm-modal {
          background: #fff; border-radius: 18px; width: 100%; max-width: 460px;
          padding: 28px 26px; box-shadow: 0 24px 60px rgba(20,30,70,0.25);
          max-height: 90vh; overflow-y: auto;
        }
        .lm-modal h2 { margin: 0 0 4px; font-size: 22px; font-weight: 800; color: #1b2236; }
        .lm-modal p.sub { margin: 0 0 20px; font-size: 14px; color: #8a90a3; }
        .lm-field {
          width: 100%; box-sizing: border-box; padding: 12px 14px;
          border: 1.5px solid #e2e5ee; border-radius: 10px; font-size: 15px;
          font-family: inherit; color: #1b2236; outline: none;
        }
        .lm-field:focus { border-color: #2563eb; }
        .lm-row { display: flex; gap: 8px; align-items: center; margin-bottom: 10px; }
        .lm-row .lm-field { flex: 1; }
        .lm-iconbtn {
          border: none; background: #f1f3f9; color: #8a90a3; cursor: pointer;
          width: 38px; height: 38px; border-radius: 9px; font-size: 18px; flex: none;
        }
        .lm-iconbtn:hover { background: #e7ebf5; color: #4b5167; }
        .lm-add {
          border: 1.5px dashed #c8cee0; background: #fff; color: #2563eb; cursor: pointer;
          width: 100%; padding: 10px; border-radius: 10px; font-size: 14px; font-weight: 700;
          font-family: inherit; margin-top: 2px;
        }
        .lm-add:hover { background: #f5f8ff; }
        .lm-actions { display: flex; gap: 10px; margin-top: 22px; }
        .lm-btn-primary, .lm-btn-ghost {
          flex: 1; padding: 13px; border-radius: 10px; font-size: 15px; font-weight: 700;
          font-family: inherit; cursor: pointer; border: none;
        }
        .lm-btn-primary { background: #2563eb; color: #fff; }
        .lm-btn-primary:disabled { opacity: 0.55; cursor: default; }
        .lm-btn-ghost { background: #f1f3f9; color: #4b5167; }
        .lm-error { margin: 12px 0 0; color: #dc2626; font-size: 13.5px; }
        .lm-done { margin: 8px 0 0; color: #16a34a; font-size: 14.5px; font-weight: 600; }
      `}</style>

            <div className="lm-modal" onClick={(e) => e.stopPropagation()}>
                {done ? (
                    <>
                        <h2>전송 완료 ✅</h2>
                        <p className="lm-done">{done}</p>
                        <div className="lm-actions">
                            <button className="lm-btn-primary" onClick={onClose}>닫기</button>
                        </div>
                    </>
                ) : step === "password" ? (
                    <>
                        <h2>⚙️ 설정</h2>
                        <p className="sub">비밀번호를 입력하면 할 일 관리로 들어갑니다.</p>
                        <input
                            className="lm-field"
                            type="password"
                            placeholder="비밀번호"
                            value={password}
                            autoFocus
                            onChange={(e) => setPassword(e.target.value)}
                            onKeyDown={(e) => e.key === "Enter" && verify()}
                        />
                        {error && <p className="lm-error">{error}</p>}
                        <div className="lm-actions">
                            <button className="lm-btn-ghost" onClick={onClose}>취소</button>
                            <button className="lm-btn-primary" onClick={verify} disabled={loading || !password}>
                                {loading ? "확인 중…" : "들어가기"}
                            </button>
                        </div>
                    </>
                ) : (
                    <>
                        <h2>📝 해야 할 일</h2>
                        <p className="sub">입력하면 AI가 정리해서 Slack으로 알림을 보냅니다.</p>
                        {items.map((v, i) => (
                            <div className="lm-row" key={i}>
                                <input
                                    className="lm-field"
                                    placeholder={`할 일 ${i + 1}`}
                                    value={v}
                                    autoFocus={i === items.length - 1}
                                    onChange={(e) => updateItem(i, e.target.value)}
                                    onKeyDown={(e) => e.key === "Enter" && addItem()}
                                />
                                <button className="lm-iconbtn" onClick={() => removeItem(i)} title="삭제">×</button>
                            </div>
                        ))}
                        <button className="lm-add" onClick={addItem}>+ 항목 추가</button>
                        {error && <p className="lm-error">{error}</p>}
                        <div className="lm-actions">
                            <button className="lm-btn-ghost" onClick={onClose}>취소</button>
                            <button className="lm-btn-primary" onClick={submit} disabled={loading}>
                                {loading ? "보내는 중…" : "AI 정리 후 알림 보내기"}
                            </button>
                        </div>
                    </>
                )}
            </div>
        </div>
    );
}