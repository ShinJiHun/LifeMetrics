// src/components/exercise/ExerciseSetEditor.tsx
export default function ExerciseSetEditor({
                                              sets,
                                              onChange,
                                          }: {
    sets: any[];
    onChange: (sets: any[]) => void;
}) {
    const addSet = () => {
        onChange([
            ...sets,
            { set_number: sets.length + 1, weight: "", reps: "" },
        ]);
    };

    const updateSet = (index: number, key: string, value: any) => {
        const next = [...sets];
        next[index][key] = value;
        onChange(next);
    };

    return (
        <div>
            <h4>세트 입력</h4>

            {sets.map((s, i) => (
                <div key={i} style={{ display: "flex", gap: 8 }}>
                    <span>{i + 1}세트</span>
                    <input
                        type="number"
                        placeholder="kg"
                        value={s.weight}
                        onChange={e =>
                            updateSet(i, "weight", Number(e.target.value))
                        }
                    />
                    <input
                        type="number"
                        placeholder="reps"
                        value={s.reps}
                        onChange={e =>
                            updateSet(i, "reps", Number(e.target.value))
                        }
                    />
                </div>
            ))}

            <button onClick={addSet}>➕ 세트 추가</button>
        </div>
    );
}
