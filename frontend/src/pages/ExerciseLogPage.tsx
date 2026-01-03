// src/pages/ExerciseLogPage.tsx
import { useState } from "react";

export default function ExerciseLogPage() {
    const [exercise, setExercise] = useState("");
    const [weight, setWeight] = useState("");
    const [reps, setReps] = useState("");

    return (
        <div style={{ padding: 24, maxWidth: 480 }}>
            <h2>✍️ 운동 기록 입력</h2>

            <div>
                <label>운동 종류</label>
                <input
                    value={exercise}
                    onChange={(e) => setExercise(e.target.value)}
                />
            </div>

            <div>
                <label>무게 (kg)</label>
                <input
                    value={weight}
                    onChange={(e) => setWeight(e.target.value)}
                />
            </div>

            <div>
                <label>횟수</label>
                <input
                    value={reps}
                    onChange={(e) => setReps(e.target.value)}
                />
            </div>

            <button style={{ marginTop: 12 }}>저장</button>
        </div>
    );
}
