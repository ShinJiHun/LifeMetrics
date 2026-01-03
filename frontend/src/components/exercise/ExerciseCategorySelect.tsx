// src/components/exercise/ExerciseCategorySelect.tsx
import { useEffect, useState } from "react";

export default function ExerciseCategorySelect({
                                                   onSelect,
                                               }: {
    onSelect: (id: number) => void;
}) {
    const [categories, setCategories] = useState<any[]>([]);

    useEffect(() => {
        // TODO: Python API
        setCategories([
            { id: 1, name: "가슴" },
            { id: 2, name: "등" },
            { id: 3, name: "하체" },
        ]);
    }, []);

    return (
        <label>
            운동 부위
            <select onChange={e => onSelect(Number(e.target.value))}>
                <option value="">선택</option>
                {categories.map(c => (
                    <option key={c.id} value={c.id}>
                        {c.name}
                    </option>
                ))}
            </select>
        </label>
    );
}
