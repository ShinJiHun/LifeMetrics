// src/components/exercise/ExerciseLogForm.tsx
import { useState } from "react";
import ExerciseCategorySelect from "./ExerciseCategorySelect";
import ExerciseItemSelect from "./ExerciseItemSelect";
import ExerciseSetEditor from "./ExerciseSetEditor";

export default function ExerciseLogForm() {
    const [date, setDate] = useState<string>(
        new Date().toISOString().slice(0, 10)
    );
    const [categoryId, setCategoryId] = useState<number | null>(null);
    const [itemId, setItemId] = useState<number | null>(null);
    const [sets, setSets] = useState<any[]>([]);

    const handleSubmit = () => {
        const payload = {
            session_date: date,
            exercise_item_id: itemId,
            sets,
        };

        console.log("SUBMIT", payload);
        // TODO: Python API POST
    };

    return (
        <div className="card">
            <label>
                운동 날짜
                <input
                    type="date"
                    value={date}
                    onChange={e => setDate(e.target.value)}
                />
            </label>

            <ExerciseCategorySelect onSelect={setCategoryId} />

            {categoryId && (
                <ExerciseItemSelect
                    categoryId={categoryId}
                    onSelect={setItemId}
                />
            )}

            {itemId && (
                <ExerciseSetEditor sets={sets} onChange={setSets} />
            )}

            <button
                onClick={handleSubmit}
                disabled={!itemId || sets.length === 0}
                style={{ marginTop: 16 }}
            >
                💾 저장
            </button>
        </div>
    );
}
