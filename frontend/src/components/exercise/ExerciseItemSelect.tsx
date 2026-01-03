// src/components/exercise/ExerciseItemSelect.tsx
import { useEffect, useState } from "react";

export default function ExerciseItemSelect({
                                               categoryId,
                                               onSelect,
                                           }: {
    categoryId: number;
    onSelect: (id: number) => void;
}) {
    const [items, setItems] = useState<any[]>([]);

    useEffect(() => {
        // TODO: Python API
        setItems([
            { id: 10, name: "벤치프레스" },
            { id: 11, name: "인클라인 벤치프레스" },
        ]);
    }, [categoryId]);

    return (
        <label>
            운동 종류
            <select onChange={e => onSelect(Number(e.target.value))}>
                <option value="">선택</option>
                {items.map(i => (
                    <option key={i.id} value={i.id}>
                        {i.name}
                    </option>
                ))}
            </select>
        </label>
    );
}
