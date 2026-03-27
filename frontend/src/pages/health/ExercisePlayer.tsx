import { useState, useEffect, useRef } from "react";

// ─── 타입 ──────────────────────────────────────────────────────────────────
interface ExerciseCategory {
  id: number;
  name: string;
}

interface ExerciseItem {
  id: number;
  categoryId: number;
  nameKo: string;
  nameEn?: string;
  description?: string;
  equipmentType?: string;
  gifUrl?: string;
  youtubeUrl?: string;
}

type SetStatus = "pending" | "active" | "done";

interface SetRecord {
  status: SetStatus;
  reps: number;
}

interface PlayerExercise extends ExerciseItem {
  sets: number;
  reps: number;
  restSec: number;
}

const DEFAULT_SETS  = 4;
const DEFAULT_REPS  = 10;
const DEFAULT_REST  = 90;

// ─── 유틸 ──────────────────────────────────────────────────────────────────
function formatTime(sec: number) {
  const m = Math.floor(sec / 60);
  const s = sec % 60;
  return `${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
}

// ─── GIF 표시 ──────────────────────────────────────────────────────────────
function GifDisplay({ exercise }: { exercise: PlayerExercise }) {
  if (exercise.gifUrl) {
    return (
      <img
        src={exercise.gifUrl}
        alt={exercise.nameKo}
        style={{ width: "100%", height: "100%", objectFit: "contain", borderRadius: 16 }}
      />
    );
  }
  return (
    <div style={{
      width: "100%", height: "100%",
      display: "flex", flexDirection: "column",
      alignItems: "center", justifyContent: "center",
      background: "linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%)",
      borderRadius: 16, gap: 12,
    }}>
      <div style={{ fontSize: 80, lineHeight: 1, filter: "drop-shadow(0 0 20px rgba(99,179,237,0.5))" }}>
        💪
      </div>
      <div style={{ color: "#63b3ed", fontSize: 13, opacity: 0.7, letterSpacing: 2 }}>
        GIF 준비중
      </div>
    </div>
  );
}

// ─── 세트 도트 ─────────────────────────────────────────────────────────────
function SetDots({ sets, currentSet }: { sets: SetRecord[]; currentSet: number }) {
  return (
    <div style={{ display: "flex", gap: 8, justifyContent: "center" }}>
      {sets.map((s, i) => {
        const isActive = i === currentSet && s.status === "active";
        const isDone   = s.status === "done";
        return (
          <div key={i} style={{
            width: isDone ? 28 : isActive ? 32 : 24,
            height: isDone ? 28 : isActive ? 32 : 24,
            borderRadius: "50%",
            background: isDone
              ? "linear-gradient(135deg, #48bb78, #38a169)"
              : isActive
              ? "linear-gradient(135deg, #63b3ed, #4299e1)"
              : "rgba(255,255,255,0.08)",
            border: isActive ? "2px solid #bee3f8" : "2px solid transparent",
            display: "flex", alignItems: "center", justifyContent: "center",
            fontSize: 11, fontWeight: 700, color: "#fff",
            transition: "all 0.3s ease",
            boxShadow: isActive ? "0 0 16px rgba(99,179,237,0.6)" : "none",
          }}>
            {isDone ? "✓" : i + 1}
          </div>
        );
      })}
    </div>
  );
}

// ─── 휴식 타이머 ──────────────────────────────────────────────────────────
function RestTimer({ remaining, total, onSkip }: {
  remaining: number; total: number; onSkip: () => void;
}) {
  const r    = 54;
  const circ = 2 * Math.PI * r;
  const dash = circ * (remaining / total);

  return (
    <div style={{
      position: "absolute", inset: 0,
      background: "rgba(10,14,26,0.92)",
      backdropFilter: "blur(8px)", borderRadius: 20,
      display: "flex", flexDirection: "column",
      alignItems: "center", justifyContent: "center",
      gap: 20, zIndex: 10,
    }}>
      <div style={{ color: "#a0aec0", fontSize: 13, letterSpacing: 3 }}>휴식 중</div>
      <svg width={128} height={128} style={{ transform: "rotate(-90deg)" }}>
        <circle cx={64} cy={64} r={r} fill="none" stroke="rgba(255,255,255,0.06)" strokeWidth={8} />
        <circle cx={64} cy={64} r={r} fill="none"
          stroke={remaining <= 10 ? "#fc8181" : "#63b3ed"}
          strokeWidth={8}
          strokeDasharray={`${dash} ${circ}`}
          strokeLinecap="round"
          style={{ transition: "stroke-dasharray 1s linear, stroke 0.3s" }}
        />
      </svg>
      <div style={{
        position: "absolute",
        fontSize: 36, fontWeight: 800,
        color: remaining <= 10 ? "#fc8181" : "#e2e8f0",
        fontVariantNumeric: "tabular-nums",
      }}>
        {formatTime(remaining)}
      </div>
      <button onClick={onSkip} style={{
        marginTop: 8, padding: "8px 24px",
        background: "rgba(255,255,255,0.08)",
        border: "1px solid rgba(255,255,255,0.15)",
        borderRadius: 20, color: "#a0aec0",
        fontSize: 13, cursor: "pointer",
      }}>
        건너뛰기 →
      </button>
    </div>
  );
}

// ─── 메인 컴포넌트 ─────────────────────────────────────────────────────────
export default function ExercisePlayer() {
  const [categories, setCategories]   = useState<ExerciseCategory[]>([]);
  const [categoryId, setCategoryId]   = useState<number | null>(null);
  const [exercises, setExercises]     = useState<PlayerExercise[]>([]);
  const [loading, setLoading]         = useState(true);

  const [exIdx, setExIdx]             = useState(0);
  const [sets, setSets]               = useState<SetRecord[]>([]);
  const [currentSet, setCurrentSet]   = useState(0);
  const [resting, setResting]         = useState(false);
  const [restRemaining, setRestRemaining] = useState(0);
  const [elapsed, setElapsed]         = useState(0);
  const [sessionDone, setSessionDone] = useState(false);

  const timerRef   = useRef<ReturnType<typeof setInterval> | null>(null);
  const elapsedRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // ── 카테고리 로드 ───────────────────────────────────────────────
  useEffect(() => {
    fetch("/api/exercise/categories")
      .then(r => r.json())
      .then((cats: ExerciseCategory[]) => setCategories(cats))
      .catch(() => setCategories([]));
  }, []);

  // ── 운동 목록 로드 ──────────────────────────────────────────────
  useEffect(() => {
    setLoading(true);
    const url = categoryId
      ? `/api/exercise/items?categoryId=${categoryId}`
      : `/api/exercise/items`;

    fetch(url)
      .then(r => r.json())
      .then((items: ExerciseItem[]) => {
        setExercises(items.map(item => ({
          ...item,
          sets: DEFAULT_SETS,
          reps: DEFAULT_REPS,
          restSec: DEFAULT_REST,
        })));
        setExIdx(0);
        setSessionDone(false);
      })
      .catch(() => setExercises([]))
      .finally(() => setLoading(false));
  }, [categoryId]);

  const exercise = exercises[exIdx];

  // ── 운동 변경 시 세트 초기화 ────────────────────────────────────
  useEffect(() => {
    if (!exercise) return;
    setSets(Array.from({ length: exercise.sets }, (_, i) => ({
      status: i === 0 ? "active" : "pending",
      reps: exercise.reps,
    })));
    setCurrentSet(0);
    setResting(false);
    if (timerRef.current) clearInterval(timerRef.current);
  }, [exIdx, exercises]);

  // ── 전체 경과 시간 ──────────────────────────────────────────────
  useEffect(() => {
    elapsedRef.current = setInterval(() => setElapsed(p => p + 1), 1000);
    return () => { if (elapsedRef.current) clearInterval(elapsedRef.current); };
  }, []);

  // ── 휴식 타이머 ─────────────────────────────────────────────────
  useEffect(() => {
    if (!resting) return;
    timerRef.current = setInterval(() => {
      setRestRemaining(prev => {
        if (prev <= 1) { clearInterval(timerRef.current!); setResting(false); return 0; }
        return prev - 1;
      });
    }, 1000);
    return () => { if (timerRef.current) clearInterval(timerRef.current); };
  }, [resting]);

  const handleSetDone = () => {
    if (resting || !exercise) return;
    setSets(prev => {
      const next = [...prev];
      next[currentSet] = { ...next[currentSet], status: "done" };
      if (currentSet + 1 < exercise.sets)
        next[currentSet + 1] = { ...next[currentSet + 1], status: "active" };
      return next;
    });
    if (currentSet + 1 < exercise.sets) {
      setCurrentSet(c => c + 1);
      setResting(true);
      setRestRemaining(exercise.restSec);
    } else {
      if (exIdx + 1 >= exercises.length) {
        setSessionDone(true);
      } else {
        setTimeout(() => setExIdx(e => e + 1), 800);
      }
    }
  };

  const handleRepChange = (delta: number) => {
    setSets(prev => {
      const next = [...prev];
      next[currentSet] = { ...next[currentSet], reps: Math.max(1, next[currentSet].reps + delta) };
      return next;
    });
  };

  const skipRest = () => {
    if (timerRef.current) clearInterval(timerRef.current);
    setResting(false);
  };

  // ── 로딩 ────────────────────────────────────────────────────────
  if (loading) return (
    <div style={{ minHeight: "100vh", background: "#0a0e1a", display: "flex", alignItems: "center", justifyContent: "center", color: "#718096" }}>
      운동 목록 로딩 중...
    </div>
  );

  // ── 세션 완료 ───────────────────────────────────────────────────
  if (sessionDone) return (
    <div style={{ minHeight: "100vh", background: "#0a0e1a", display: "flex", alignItems: "center", justifyContent: "center", flexDirection: "column", gap: 24 }}>
      <div style={{ fontSize: 72 }}>🎉</div>
      <h2 style={{ color: "#e2e8f0", fontSize: 28, fontWeight: 800, margin: 0 }}>운동 완료!</h2>
      <p style={{ color: "#718096", margin: 0 }}>총 운동 시간: {formatTime(elapsed)}</p>
      <button onClick={() => { setExIdx(0); setElapsed(0); setSessionDone(false); }} style={{
        marginTop: 16, padding: "12px 32px",
        background: "linear-gradient(135deg, #4299e1, #3182ce)",
        border: "none", borderRadius: 24, color: "#fff",
        fontSize: 15, fontWeight: 700, cursor: "pointer",
      }}>
        다시 시작
      </button>
    </div>
  );

  if (!exercise) return (
    <div style={{ minHeight: "100vh", background: "#0a0e1a", display: "flex", alignItems: "center", justifyContent: "center", color: "#718096" }}>
      등록된 운동이 없습니다.
    </div>
  );

  const currentReps = sets[currentSet]?.reps ?? exercise.reps;
  const doneCount   = sets.filter(s => s.status === "done").length;

  return (
    <div style={{
      minHeight: "100vh", background: "#0a0e1a", color: "#e2e8f0",
      fontFamily: "'Pretendard', 'Apple SD Gothic Neo', sans-serif",
      display: "flex", flexDirection: "column",
    }}>
      {/* 헤더 */}
      <div style={{
        display: "flex", alignItems: "center", justifyContent: "space-between",
        padding: "20px 28px", borderBottom: "1px solid rgba(255,255,255,0.06)",
      }}>
        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <div style={{
            width: 36, height: 36, borderRadius: 10,
            background: "linear-gradient(135deg, #4299e1, #805ad5)",
            display: "flex", alignItems: "center", justifyContent: "center", fontSize: 16,
          }}>💪</div>
          <div>
            <div style={{ fontSize: 11, color: "#718096", letterSpacing: 2 }}>EXERCISE PLAYER</div>
            <div style={{ fontSize: 15, fontWeight: 700 }}>오늘의 운동</div>
          </div>
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: 6, color: "#718096", fontSize: 14 }}>
          <span>⏱</span>
          <span style={{ fontVariantNumeric: "tabular-nums", fontWeight: 600 }}>{formatTime(elapsed)}</span>
        </div>
      </div>

      {/* 카테고리 탭 */}
      <div style={{
        display: "flex", gap: 8, padding: "12px 28px",
        borderBottom: "1px solid rgba(255,255,255,0.06)",
        overflowX: "auto",
      }}>
        <button
          onClick={() => setCategoryId(null)}
          style={{
            padding: "6px 16px", borderRadius: 20, fontSize: 13, fontWeight: 600,
            border: "1px solid",
            borderColor: categoryId === null ? "#4299e1" : "rgba(255,255,255,0.1)",
            background: categoryId === null ? "rgba(66,153,225,0.15)" : "transparent",
            color: categoryId === null ? "#63b3ed" : "#4a5568",
            cursor: "pointer", whiteSpace: "nowrap", transition: "all 0.2s",
          }}
        >
          전체
        </button>
        {categories.map(cat => (
          <button
            key={cat.id}
            onClick={() => setCategoryId(cat.id)}
            style={{
              padding: "6px 16px", borderRadius: 20, fontSize: 13, fontWeight: 600,
              border: "1px solid",
              borderColor: categoryId === cat.id ? "#4299e1" : "rgba(255,255,255,0.1)",
              background: categoryId === cat.id ? "rgba(66,153,225,0.15)" : "transparent",
              color: categoryId === cat.id ? "#63b3ed" : "#4a5568",
              cursor: "pointer", whiteSpace: "nowrap", transition: "all 0.2s",
            }}
          >
            {cat.name}
          </button>
        ))}
      </div>

      {/* 운동 목록 탭 */}
      <div style={{
        display: "flex", gap: 0,
        borderBottom: "1px solid rgba(255,255,255,0.06)",
        overflowX: "auto", padding: "0 28px",
      }}>
        {exercises.map((ex, i) => {
          const isActive = i === exIdx;
          const isPast   = i < exIdx;
          return (
            <button key={ex.id} onClick={() => setExIdx(i)} style={{
              padding: "12px 16px", background: "none", border: "none",
              borderBottom: isActive ? "2px solid #4299e1" : "2px solid transparent",
              color: isActive ? "#63b3ed" : isPast ? "#48bb78" : "#4a5568",
              fontSize: 13, fontWeight: isActive ? 700 : 400,
              cursor: "pointer", whiteSpace: "nowrap", transition: "all 0.2s",
            }}>
              {isPast ? "✓ " : ""}{ex.nameKo}
            </button>
          );
        })}
      </div>

      {/* 메인 콘텐츠 */}
      <div style={{
        flex: 1, display: "flex", gap: 28, padding: "28px",
        maxWidth: 960, margin: "0 auto", width: "100%", boxSizing: "border-box",
      }}>
        {/* GIF */}
        <div style={{
          flex: "0 0 360px", position: "relative",
          borderRadius: 20, overflow: "hidden",
          border: "1px solid rgba(255,255,255,0.08)",
          aspectRatio: "1/1", height: "fit-content",
        }}>
          <GifDisplay exercise={exercise} />
          {resting && <RestTimer remaining={restRemaining} total={exercise.restSec} onSkip={skipRest} />}
        </div>

        {/* 컨트롤 */}
        <div style={{ flex: 1, display: "flex", flexDirection: "column", gap: 24 }}>
          <div>
            {exercise.equipmentType && (
              <div style={{
                display: "inline-block", padding: "3px 10px",
                background: "rgba(66,153,225,0.15)", border: "1px solid rgba(66,153,225,0.3)",
                borderRadius: 20, fontSize: 11, color: "#63b3ed", letterSpacing: 1, marginBottom: 10,
              }}>
                {exercise.equipmentType}
              </div>
            )}
            <h1 style={{ margin: 0, fontSize: 32, fontWeight: 800, letterSpacing: -0.5 }}>
              {exercise.nameKo}
            </h1>
            {exercise.nameEn && (
              <div style={{ fontSize: 13, color: "#4a5568", marginTop: 4 }}>{exercise.nameEn}</div>
            )}
            <p style={{ margin: "8px 0 0", color: "#718096", fontSize: 14, lineHeight: 1.6 }}>
              {exercise.description ?? ""}
            </p>
          </div>

          {/* 세트 현황 */}
          <div style={{
            background: "rgba(255,255,255,0.03)", border: "1px solid rgba(255,255,255,0.08)",
            borderRadius: 16, padding: 20,
          }}>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 16, fontSize: 13, color: "#718096" }}>
              <span>세트 진행</span>
              <span style={{ color: "#48bb78", fontWeight: 600 }}>{doneCount} / {exercise.sets} 완료</span>
            </div>
            <SetDots sets={sets} currentSet={currentSet} />
          </div>

          {/* 횟수 조절 */}
          <div style={{
            background: "rgba(255,255,255,0.03)", border: "1px solid rgba(255,255,255,0.08)",
            borderRadius: 16, padding: 24, textAlign: "center",
          }}>
            <div style={{ fontSize: 12, color: "#718096", letterSpacing: 2, marginBottom: 16 }}>
              세트 {currentSet + 1} 목표 횟수
            </div>
            <div style={{ display: "flex", alignItems: "center", justifyContent: "center", gap: 24 }}>
              <button onClick={() => handleRepChange(-1)} style={{
                width: 44, height: 44, borderRadius: "50%",
                background: "rgba(255,255,255,0.06)", border: "1px solid rgba(255,255,255,0.12)",
                color: "#e2e8f0", fontSize: 20, cursor: "pointer",
                display: "flex", alignItems: "center", justifyContent: "center",
              }}>−</button>
              <div style={{ fontSize: 56, fontWeight: 900, fontVariantNumeric: "tabular-nums", lineHeight: 1, minWidth: 80, textAlign: "center" }}>
                {currentReps}
              </div>
              <button onClick={() => handleRepChange(1)} style={{
                width: 44, height: 44, borderRadius: "50%",
                background: "rgba(255,255,255,0.06)", border: "1px solid rgba(255,255,255,0.12)",
                color: "#e2e8f0", fontSize: 20, cursor: "pointer",
                display: "flex", alignItems: "center", justifyContent: "center",
              }}>+</button>
            </div>
            <div style={{ fontSize: 12, color: "#4a5568", marginTop: 8 }}>회</div>
          </div>

          {/* 세트 완료 버튼 */}
          <button onClick={handleSetDone}
            disabled={resting || sets[currentSet]?.status === "done"}
            style={{
              padding: "18px",
              background: resting || sets[currentSet]?.status === "done"
                ? "rgba(255,255,255,0.04)"
                : "linear-gradient(135deg, #4299e1 0%, #3182ce 100%)",
              border: "none", borderRadius: 16,
              color: resting || sets[currentSet]?.status === "done" ? "#4a5568" : "#fff",
              fontSize: 16, fontWeight: 700,
              cursor: resting || sets[currentSet]?.status === "done" ? "not-allowed" : "pointer",
              transition: "all 0.2s", letterSpacing: 0.5,
              boxShadow: resting ? "none" : "0 8px 32px rgba(66,153,225,0.3)",
            }}>
            {resting
              ? `휴식 중... ${formatTime(restRemaining)}`
              : sets[currentSet]?.status === "done"
              ? "세트 완료 ✓"
              : `세트 ${currentSet + 1} 완료`}
          </button>

          {/* 다음 운동 미리보기 */}
          {exIdx + 1 < exercises.length && (
            <div style={{
              display: "flex", alignItems: "center", gap: 10, padding: "12px 16px",
              background: "rgba(255,255,255,0.02)", border: "1px solid rgba(255,255,255,0.06)",
              borderRadius: 12,
            }}>
              <span style={{ fontSize: 11, color: "#4a5568" }}>다음</span>
              <span style={{ fontSize: 13, color: "#718096" }}>
                {exercises[exIdx + 1].nameKo}
              </span>
              <span style={{ marginLeft: "auto", fontSize: 11, color: "#4a5568" }}>
                {exercises[exIdx + 1].sets}세트 × {exercises[exIdx + 1].reps}회
              </span>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
