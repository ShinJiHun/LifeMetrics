import { useState, useEffect } from "react";
import "@/styles/global.css";
import "@/styles/exercise-input.css";

const API_BASE = import.meta.env.VITE_API_URL || "";

const EQUIPMENT_TYPES = [
    { id: "all", name: "전체" },
    { id: "FREE_WEIGHT", name: "바벨/덤벨" },
    { id: "MACHINE", name: "머신" },
    { id: "CABLE", name: "케이블" },
    { id: "BODYWEIGHT", name: "맨몸" },
];

interface Category {
    id: number;
    name: string;
    description: string;
}

interface ExerciseItem {
    id: number;
    categoryId: number;
    nameKo: string;
    nameEn: string;
    equipmentType: string;
    description: string;
    mediaUrl: string;
}

interface ExerciseSet {
    setNumber: number;
    weight: string;
    reps: string;
}

interface SelectedExercise {
    item: ExerciseItem;
    sets: ExerciseSet[];
    restTime: string;  // 휴식 시간 (초)
    memo: string;      // 메모
}

export default function ExerciseInputPage() {
    const [categories, setCategories] = useState<Category[]>([]);
    const [items, setItems] = useState<ExerciseItem[]>([]);
    const [selectedCategory, setSelectedCategory] = useState<number | null>(null);
    const [selectedEquipment, setSelectedEquipment] = useState<string>("all");
    const [selectedExercises, setSelectedExercises] = useState<SelectedExercise[]>([]);
    const [searchQuery, setSearchQuery] = useState("");
    const [isPanelExpanded, setIsPanelExpanded] = useState(true);
    const [loading, setLoading] = useState(false);
    const [saving, setSaving] = useState(false);
    const [isPT, setIsPT] = useState(false);  // PT 여부

    // 카테고리 로드
    useEffect(() => {
        fetch(`${API_BASE}/api/exercise/categories`)
            .then((res) => res.json())
            .then((data) => {
                setCategories(data);
            })
            .catch((err) => console.error("카테고리 로드 실패:", err));
    }, []);

    // 운동 종목 로드
    useEffect(() => {
        setLoading(true);
        const url = selectedCategory
            ? `${API_BASE}/api/exercise/items?categoryId=${selectedCategory}`
            : `${API_BASE}/api/exercise/items`;

        fetch(url)
            .then((res) => res.json())
            .then((data) => {
                setItems(data);
                setLoading(false);
            })
            .catch((err) => {
                console.error("운동 종목 로드 실패:", err);
                setLoading(false);
            });
    }, [selectedCategory]);

    // 종목 필터링
    const filteredItems = items.filter((item) => {
        const matchEquipment = selectedEquipment === "all" || item.equipmentType === selectedEquipment;
        const matchSearch =
            !searchQuery ||
            item.nameKo?.includes(searchQuery) ||
            item.nameEn?.toLowerCase().includes(searchQuery.toLowerCase());
        return matchEquipment && matchSearch;
    });

    // 종목 선택
    const handleSelectItem = (item: ExerciseItem) => {
        const exists = selectedExercises.find((e) => e.item.id === item.id);
        if (exists) return;

        setSelectedExercises([
            ...selectedExercises,
            {
                item,
                sets: [{ setNumber: 1, weight: "", reps: "" }],
                restTime: "60",  // 기본 휴식 시간 60초
                memo: "",
            },
        ]);
        setIsPanelExpanded(true);
    };

    // 세트 추가
    const addSet = (exerciseId: number) => {
        setSelectedExercises((prev) =>
            prev.map((e) =>
                e.item.id === exerciseId
                    ? {
                        ...e,
                        sets: [...e.sets, { setNumber: e.sets.length + 1, weight: "", reps: "" }],
                    }
                    : e
            )
        );
    };

    // 세트 삭제
    const removeSet = (exerciseId: number, setIndex: number) => {
        setSelectedExercises((prev) =>
            prev.map((e) =>
                e.item.id === exerciseId
                    ? {
                        ...e,
                        sets: e.sets
                            .filter((_, i) => i !== setIndex)
                            .map((s, i) => ({ ...s, setNumber: i + 1 })),
                    }
                    : e
            )
        );
    };

    // 세트 값 변경
    const updateSet = (exerciseId: number, setIndex: number, field: "weight" | "reps", value: string) => {
        setSelectedExercises((prev) =>
            prev.map((e) =>
                e.item.id === exerciseId
                    ? {
                        ...e,
                        sets: e.sets.map((s, i) => (i === setIndex ? { ...s, [field]: value } : s)),
                    }
                    : e
            )
        );
    };

    // 휴식 시간 변경
    const updateRestTime = (exerciseId: number, value: string) => {
        setSelectedExercises((prev) =>
            prev.map((e) =>
                e.item.id === exerciseId ? { ...e, restTime: value } : e
            )
        );
    };

    // 메모 변경
    const updateMemo = (exerciseId: number, value: string) => {
        setSelectedExercises((prev) =>
            prev.map((e) =>
                e.item.id === exerciseId ? { ...e, memo: value } : e
            )
        );
    };

    // 운동 삭제
    const removeExercise = (exerciseId: number) => {
        setSelectedExercises((prev) => prev.filter((e) => e.item.id !== exerciseId));
    };

    // 저장
    const handleSave = async () => {
        if (selectedExercises.length === 0) {
            alert("운동을 선택해주세요!");
            return;
        }

        // 빈 세트 체크
        for (const exercise of selectedExercises) {
            for (const set of exercise.sets) {
                if (!set.weight || !set.reps) {
                    alert(`${exercise.item.nameKo}의 모든 세트에 무게와 횟수를 입력해주세요!`);
                    return;
                }
            }
        }

        setSaving(true);

        try {
            const today = new Date().toISOString().split("T")[0];

            const payload = {
                sessionDate: today,
                userId: 1, // TODO: 실제 유저 ID로 변경
                isPT: isPT,
                exercises: selectedExercises.map((e) => ({
                    exerciseItemId: e.item.id,
                    restTimeSec: parseInt(e.restTime) || 60,
                    memo: e.memo,
                    sets: e.sets.map((s) => ({
                        setNumber: s.setNumber,
                        weight: parseFloat(s.weight),
                        reps: parseInt(s.reps),
                    })),
                })),
            };

            const response = await fetch(`${API_BASE}/api/exercise/log`, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                },
                body: JSON.stringify(payload),
            });

            if (response.ok) {
                alert("운동 기록이 저장되었습니다! 💪");
                setSelectedExercises([]);
                setIsPT(false);
            } else {
                throw new Error("저장 실패");
            }
        } catch (err) {
            console.error("저장 실패:", err);
            alert("저장에 실패했습니다. 다시 시도해주세요.");
        } finally {
            setSaving(false);
        }
    };

    const totalSets = selectedExercises.reduce((sum, e) => sum + e.sets.length, 0);

    const getCategoryName = (categoryId: number) => {
        return categories.find((c) => c.id === categoryId)?.name || "";
    };

    const getEquipmentName = (equipmentType: string) => {
        const map: Record<string, string> = {
            FREE_WEIGHT: "프리웨이트",
            MACHINE: "머신",
            CABLE: "케이블",
            BODYWEIGHT: "맨몸",
            BAND: "밴드",
            MIXED: "복합",
        };
        return map[equipmentType] || equipmentType;
    };

    return (
        <div className="exercise-input-page">
            <h2>🏋️ 운동 기록 입력</h2>

            {/* PT 토글 */}
            <div className="pt-toggle">
                <label className={`toggle-label ${isPT ? "active" : ""}`}>
                    <input
                        type="checkbox"
                        checked={isPT}
                        onChange={(e) => setIsPT(e.target.checked)}
                    />
                    <span className="toggle-text">{isPT ? "🏆 PT 운동" : "🏃 개인 운동"}</span>
                </label>
            </div>

            <div className="exercise-layout">
                {/* 왼쪽: 종목 선택 */}
                <div className="exercise-selector">
                    {/* 부위 탭 */}
                    <div className="category-tabs">
                        <button
                            className={`category-tab ${selectedCategory === null ? "active" : ""}`}
                            onClick={() => setSelectedCategory(null)}
                        >
                            전체
                        </button>
                        {categories.map((cat) => (
                            <button
                                key={cat.id}
                                className={`category-tab ${selectedCategory === cat.id ? "active" : ""}`}
                                onClick={() => setSelectedCategory(cat.id)}
                            >
                                {cat.name}
                            </button>
                        ))}
                    </div>

                    {/* 장비 필터 */}
                    <div className="equipment-filter">
                        {EQUIPMENT_TYPES.map((eq) => (
                            <button
                                key={eq.id}
                                className={`equipment-btn ${selectedEquipment === eq.id ? "active" : ""}`}
                                onClick={() => setSelectedEquipment(eq.id)}
                            >
                                {eq.name}
                            </button>
                        ))}
                    </div>

                    {/* 검색 */}
                    <input
                        type="text"
                        placeholder="운동 검색..."
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        className="search-input"
                    />

                    {/* 종목 목록 */}
                    {loading ? (
                        <div className="loading">로딩 중...</div>
                    ) : (
                        <div className="item-grid">
                            {filteredItems.map((item) => (
                                <div
                                    key={item.id}
                                    className={`item-card ${selectedExercises.find((e) => e.item.id === item.id) ? "selected" : ""}`}
                                    onClick={() => handleSelectItem(item)}
                                >
                                    <div className="item-tags">
                                        <span className="item-category">{getCategoryName(item.categoryId)}</span>
                                        <span className="item-equipment">{getEquipmentName(item.equipmentType)}</span>
                                    </div>
                                    <h4>{item.nameKo}</h4>
                                    <p>{item.nameEn}</p>
                                </div>
                            ))}
                            {filteredItems.length === 0 && <p className="no-results">검색 결과가 없습니다</p>}
                        </div>
                    )}
                </div>

                {/* 오른쪽: 선택된 운동 & 세트 입력 */}
                <div className={`exercise-input-area ${isPanelExpanded ? "expanded" : "collapsed"}`}>
                    {/* 모바일 토글 헤더 */}
                    <div className="panel-header" onClick={() => setIsPanelExpanded(!isPanelExpanded)}>
                        <h3>
                            📝 오늘의 운동{" "}
                            {selectedExercises.length > 0 && `(${selectedExercises.length}종목, ${totalSets}세트)`}
                            {isPT && " 🏆PT"}
                        </h3>
                        <button className="toggle-btn">{isPanelExpanded ? "▼" : "▲"}</button>
                    </div>

                    {isPanelExpanded && (
                        <div className="panel-content">
                            {selectedExercises.length === 0 ? (
                                <p className="empty-message">왼쪽에서 운동을 선택하세요</p>
                            ) : (
                                <div className="selected-exercises">
                                    {selectedExercises.map((exercise) => (
                                        <div key={exercise.item.id} className="exercise-card">
                                            <div className="exercise-header">
                                                <h4>{exercise.item.nameKo}</h4>
                                                <button
                                                    className="remove-btn"
                                                    onClick={() => removeExercise(exercise.item.id)}
                                                >
                                                    ✕
                                                </button>
                                            </div>

                                            {/* 휴식 시간 & 메모 */}
                                            <div className="exercise-options">
                                                <div className="option-row">
                                                    <label>⏱️ 휴식</label>
                                                    <input
                                                        type="number"
                                                        placeholder="60"
                                                        value={exercise.restTime}
                                                        onChange={(e) => updateRestTime(exercise.item.id, e.target.value)}
                                                        className="rest-input"
                                                    />
                                                    <span>초</span>
                                                </div>
                                                <div className="option-row memo-row">
                                                    <label>📝 메모</label>
                                                    <input
                                                        type="text"
                                                        placeholder="메모 입력..."
                                                        value={exercise.memo}
                                                        onChange={(e) => updateMemo(exercise.item.id, e.target.value)}
                                                        className="memo-input"
                                                    />
                                                </div>
                                            </div>

                                            <div className="sets-table">
                                                <div className="sets-header">
                                                    <span>세트</span>
                                                    <span>무게 (kg)</span>
                                                    <span>횟수</span>
                                                    <span></span>
                                                </div>
                                                {exercise.sets.map((set, idx) => (
                                                    <div key={idx} className="set-row">
                                                        <span className="set-number">{set.setNumber}</span>
                                                        <input
                                                            type="number"
                                                            placeholder="0"
                                                            value={set.weight}
                                                            onChange={(e) =>
                                                                updateSet(exercise.item.id, idx, "weight", e.target.value)
                                                            }
                                                        />
                                                        <input
                                                            type="number"
                                                            placeholder="0"
                                                            value={set.reps}
                                                            onChange={(e) =>
                                                                updateSet(exercise.item.id, idx, "reps", e.target.value)
                                                            }
                                                        />
                                                        {exercise.sets.length > 1 && (
                                                            <button
                                                                className="remove-set-btn"
                                                                onClick={() => removeSet(exercise.item.id, idx)}
                                                            >
                                                                🗑
                                                            </button>
                                                        )}
                                                    </div>
                                                ))}
                                            </div>

                                            <button className="add-set-btn" onClick={() => addSet(exercise.item.id)}>
                                                + 세트 추가
                                            </button>
                                        </div>
                                    ))}

                                    <button className="save-btn" onClick={handleSave} disabled={saving}>
                                        {saving ? "저장 중..." : "💾 저장하기"}
                                    </button>
                                </div>
                            )}
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}