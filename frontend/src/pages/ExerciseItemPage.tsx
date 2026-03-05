// src/pages/ExerciseItemPage.tsx
import { useState, useEffect } from "react";
import api from "../lib/axios";
import "@/styles/exercise-item.css";

const EQUIPMENT_LABEL: Record<string, string> = {
  barbell: "바벨",
  dumbbell: "덤벨",
  machine: "머신",
  cable: "케이블",
  bodyweight: "맨몸",
  free: "프리",
  kettlebell: "케틀벨",
  cardio: "유산소",
  assisted: "어시스트",
};

const EQUIPMENT_COLOR: Record<string, string> = {
  barbell: "#ef4444",
  dumbbell: "#f97316",
  machine: "#8b5cf6",
  cable: "#06b6d4",
  bodyweight: "#22c55e",
  free: "#6b7280",
  kettlebell: "#eab308",
  cardio: "#ec4899",
  assisted: "#14b8a6",
};

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
  description?: string;
  mediaUrl?: string | null;
}

export default function ExerciseItemPage() {
  const [categories, setCategories] = useState<Category[]>([]);
  const [items, setItems] = useState<ExerciseItem[]>([]);
  const [loading, setLoading] = useState(true);

  const [selectedCategory, setSelectedCategory] = useState<number | null>(null);
  const [selectedEquipment, setSelectedEquipment] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [selectedItem, setSelectedItem] = useState<ExerciseItem | null>(null);
  const [onlyWithMedia, setOnlyWithMedia] = useState(false);

  // 카테고리 로드
  useEffect(() => {
    api.get("/exercise/categories").then((res) => {
      setCategories(res.data);
    });
  }, []);

  // 운동 목록 로드
  useEffect(() => {
    setLoading(true);
    const params: Record<string, any> = {};
    if (selectedCategory) params.categoryId = selectedCategory;
    if (onlyWithMedia) params.hasMedia = true;

    api.get("/exercise/items", { params })
        .then((res) => {
          setItems(res.data);
        })
        .finally(() => {
          setLoading(false);
        });
  }, [selectedCategory, onlyWithMedia]);

  // 카테고리 변경 시 장비 필터 초기화
  useEffect(() => {
    setSelectedEquipment(null);
  }, [selectedCategory]);

  // 해당 카테고리 내 장비 타입 목록
  const equipmentTypes = [...new Set(
      items.map(item => item.equipmentType)
  )];

  // 필터링
  const filteredItems = items.filter((item) => {
    const matchEquipment = !selectedEquipment || item.equipmentType === selectedEquipment;
    const matchSearch = item.nameKo.includes(search) || item.nameEn.toLowerCase().includes(search.toLowerCase());
    return matchEquipment && matchSearch;
  });

  const selectedCategoryInfo = categories.find((c) => c.id === selectedItem?.categoryId);

  return (
      <div className="exercise-item-page">
        {/* 헤더 */}
        <div className="page-header">
          <h2>🏋️ 운동 종목</h2>
          <div className="header-actions">
            <label className="media-filter">
              <input
                  type="checkbox"
                  checked={onlyWithMedia}
                  onChange={(e) => setOnlyWithMedia(e.target.checked)}
              />
              영상 있는 운동만
            </label>
            <button className="btn-add">+ 종목 추가</button>
          </div>
        </div>

        {/* 카테고리 탭 */}
        <div className="category-tabs">
          <button
              className={selectedCategory === null ? "active" : ""}
              onClick={() => setSelectedCategory(null)}
          >
            전체
          </button>
          {categories.map((cat) => (
              <button
                  key={cat.id}
                  className={selectedCategory === cat.id ? "active" : ""}
                  onClick={() => setSelectedCategory(cat.id)}
              >
                {cat.name}
              </button>
          ))}
        </div>

        {/* 장비 타입 서브 필터 */}
        {selectedCategory && equipmentTypes.length > 0 && (
            <div className="equipment-tabs">
              <button
                  className={!selectedEquipment ? "active" : ""}
                  onClick={() => setSelectedEquipment(null)}
              >
                전체
              </button>
              {equipmentTypes.map(type => (
                  <button
                      key={type}
                      className={selectedEquipment === type ? "active" : ""}
                      onClick={() => setSelectedEquipment(type)}
                      style={{
                        backgroundColor: selectedEquipment === type ? EQUIPMENT_COLOR[type] : undefined,
                        color: selectedEquipment === type ? 'white' : undefined
                      }}
                  >
                    {EQUIPMENT_LABEL[type] || type}
                  </button>
              ))}
            </div>
        )}

        {/* 검색 */}
        <div className="filter-bar">
          <input
              type="text"
              placeholder="운동 검색..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="search-input"
          />
          <span className="item-count">총 {filteredItems.length}개</span>
        </div>

        {/* 메인 레이아웃: 목록 + 상세 */}
        <div className="exercise-layout">
          {/* 좌측: 운동 목록 */}
          <div className="exercise-list-section">
            {loading ? (
                <div className="loading">로딩 중...</div>
            ) : (
                <div className="item-grid">
                  {filteredItems.map((item) => {
                    const category = categories.find((c) => c.id === item.categoryId);
                    const isSelected = selectedItem?.id === item.id;
                    return (
                        <div
                            key={item.id}
                            className={`item-card ${isSelected ? "selected" : ""}`}
                            onClick={() => setSelectedItem(item)}
                        >
                          <div className="card-header">
                            <span className="category-badge">{category?.name}</span>
                            <span
                                className="equipment-badge"
                                style={{ backgroundColor: EQUIPMENT_COLOR[item.equipmentType] || "#6b7280" }}
                            >
                        {EQUIPMENT_LABEL[item.equipmentType] || item.equipmentType}
                      </span>
                          </div>
                          <div className="item-name">{item.nameKo}</div>
                          <div className="item-name-en">{item.nameEn}</div>
                        </div>
                    );
                  })}
                </div>
            )}
          </div>

          {/* 우측: 상세 정보 */}
          <div className="exercise-detail-section">
            {selectedItem ? (
                <div className="detail-content">
                  <div className="detail-media">
                    {selectedItem.mediaUrl ? (
                        <video
                            src={selectedItem.mediaUrl}
                            autoPlay
                            loop
                            muted
                            playsInline
                        />
                    ) : (
                        <div className="media-placeholder">
                          <span>🎬</span>
                          <p>운동 영상 준비 중</p>
                        </div>
                    )}
                  </div>

                  <div className="detail-info">
                    <div className="detail-header">
                      <span className="category-badge">{selectedCategoryInfo?.name}</span>
                      <span
                          className="equipment-badge"
                          style={{ backgroundColor: EQUIPMENT_COLOR[selectedItem.equipmentType] || "#6b7280" }}
                      >
                    {EQUIPMENT_LABEL[selectedItem.equipmentType] || selectedItem.equipmentType}
                  </span>
                    </div>

                    <h3 className="detail-title">{selectedItem.nameKo}</h3>
                    <p className="detail-title-en">{selectedItem.nameEn}</p>

                    <div className="detail-description">
                      <h4>운동 설명</h4>
                      <p>{selectedItem.description || "설명이 없습니다."}</p>
                    </div>

                    <div className="detail-tips">
                      <h4>운동 팁</h4>
                      <ul>
                        <li>정확한 자세를 유지하세요</li>
                        <li>호흡을 잊지 마세요</li>
                        <li>무리하지 않는 무게로 시작하세요</li>
                      </ul>
                    </div>
                  </div>
                </div>
            ) : (
                <div className="detail-empty">
                  <span>👈</span>
                  <p>운동을 선택하면<br />상세 정보가 표시됩니다</p>
                </div>
            )}
          </div>
        </div>
      </div>
  );
}