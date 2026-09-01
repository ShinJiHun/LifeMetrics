import { useEffect, useMemo, useState } from "react";
import { fetchPermanentCourses } from "@/api/permanent";
import type { PermanentCourse } from "@/api/permanent";
import ActivityMap from "@/pages/ride/riding/ActivityMap";

interface CourseGroup {
    permanentNo: string;
    name: string;
    distanceKm?: number;
    timeLimitHm?: string;
    region?: string;
    polyline?: string | null;
    gpxVariants: string[]; // gpxLabel 목록 (없으면 빈 배열)
}

function groupByPermanentNo(courses: PermanentCourse[]): CourseGroup[] {
    const map = new Map<string, CourseGroup>();
    for (const c of courses) {
        let group = map.get(c.permanentNo);
        if (!group) {
            group = {
                permanentNo: c.permanentNo,
                name: c.name,
                distanceKm: c.distanceKm,
                timeLimitHm: c.timeLimitHm,
                region: c.region,
                polyline: c.polyline,
                gpxVariants: [],
            };
            map.set(c.permanentNo, group);
        }
        if (c.gpxLabel) group.gpxVariants.push(c.gpxLabel);
    }
    return Array.from(map.values());
}

export default function PermanentCoursesPage() {
    const [courses, setCourses] = useState<PermanentCourse[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [selected, setSelected] = useState<string>("");
    const [regionFilter, setRegionFilter] = useState("");

    useEffect(() => {
        fetchPermanentCourses()
            .then((data) => {
                setCourses(data);
                setError("");
            })
            .catch(() => setError("퍼머넌트 코스 목록을 불러오지 못했습니다."))
            .finally(() => setLoading(false));
    }, []);

    const groups = useMemo(() => groupByPermanentNo(courses), [courses]);

    const regions = useMemo(
        () => Array.from(new Set(groups.map((g) => g.region).filter(Boolean))) as string[],
        [groups]
    );

    const filtered = useMemo(
        () => (regionFilter ? groups.filter((g) => g.region === regionFilter) : groups),
        [groups, regionFilter]
    );

    useEffect(() => {
        if (!selected && filtered.length > 0) setSelected(filtered[0].permanentNo);
    }, [filtered, selected]);

    const current = filtered.find((g) => g.permanentNo === selected) ?? null;

    return (
        <div style={{ padding: 24, display: "flex", flexDirection: "column", gap: 16, height: "100%" }}>
            <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                <h1 style={{ margin: 0, fontSize: 20 }}>🗺️ 퍼머넌트 코스</h1>
                <span style={{ color: "#6b7280", fontSize: 13 }}>
                    {loading ? "불러오는 중..." : `${groups.length}개 코스`}
                </span>
            </div>

            {error && <div style={{ color: "#dc2626", fontSize: 13 }}>{error}</div>}

            {!loading && !error && (
                <div style={{ display: "grid", gridTemplateColumns: "340px 1fr", gap: 16, flex: 1, minHeight: 0 }}>
                    <div style={{ display: "flex", flexDirection: "column", gap: 10, minHeight: 0 }}>
                        <select
                            value={regionFilter}
                            onChange={(e) => setRegionFilter(e.target.value)}
                            style={{
                                padding: "8px 10px", borderRadius: 8, border: "1px solid #e5e7eb",
                                fontSize: 13, color: "#111",
                            }}
                        >
                            <option value="">전체 지역</option>
                            {regions.map((r) => (
                                <option key={r} value={r}>{r}</option>
                            ))}
                        </select>

                        <div style={{ overflowY: "auto", display: "flex", flexDirection: "column", gap: 8 }}>
                            {filtered.map((g) => (
                                <div
                                    key={g.permanentNo}
                                    onClick={() => setSelected(g.permanentNo)}
                                    style={{
                                        border: g.permanentNo === selected ? "2px solid #3b82f6" : "1px solid #e5e7eb",
                                        borderRadius: 10,
                                        padding: "10px 12px",
                                        cursor: "pointer",
                                        background: "#fff",
                                    }}
                                >
                                    <div style={{ fontSize: 13, fontWeight: 700, color: "#111" }}>
                                        {g.permanentNo} · {g.name}
                                    </div>
                                    <div style={{ fontSize: 12, color: "#6b7280", marginTop: 2 }}>
                                        {g.distanceKm ? `${g.distanceKm}km` : "-"}
                                        {g.timeLimitHm ? ` · 제한 ${g.timeLimitHm}` : ""}
                                        {g.region ? ` · ${g.region}` : ""}
                                    </div>
                                    {g.gpxVariants.length > 1 && (
                                        <div style={{ fontSize: 11, color: "#3b82f6", marginTop: 4 }}>
                                            경로 {g.gpxVariants.length}개 (지도는 대표 경로 1개만 표시)
                                        </div>
                                    )}
                                    {g.gpxVariants.length === 0 && (
                                        <div style={{ fontSize: 11, color: "#d97706", marginTop: 4 }}>
                                            gpx 없음
                                        </div>
                                    )}
                                </div>
                            ))}
                        </div>
                    </div>

                    <div style={{ borderRadius: 12, overflow: "hidden", border: "1px solid #e5e7eb" }}>
                        {current?.polyline ? (
                            <ActivityMap polyline={current.polyline} height={560} interactive />
                        ) : (
                            <div style={{
                                height: 560, display: "flex", alignItems: "center", justifyContent: "center",
                                color: "#9ca3af", fontSize: 14, background: "#f9fafb",
                            }}>
                                {current ? "이 코스는 아직 경로 정보가 없습니다." : "코스를 선택하세요."}
                            </div>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
}