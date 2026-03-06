import { useState, useEffect } from "react";
import { fetchActivities } from "@/api/activity";
import type { Activity } from "@/api/activity";
import ActivityMap from "@/components/ActivityMap";
import "@/styles/riding-record.css";

const formatDistance = (meters: number) => (meters / 1000).toFixed(1);
const formatTime = (seconds: number) => {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = seconds % 60;
    return h > 0
        ? `${h}:${m.toString().padStart(2, "0")}:${s.toString().padStart(2, "0")}`
        : `${m}:${s.toString().padStart(2, "0")}`;
};
const formatDate = (dateStr: string) => {
    const date = new Date(dateStr);
    const year : number = date.getFullYear();
    const month = date.getMonth() + 1;
    const day = date.getDate();
    const weekday = ["일", "월", "화", "수", "목", "금", "토"][date.getDay()];
    const hour = date.getHours();
    const minute = date.getMinutes().toString().padStart(2, "0");
    return `${year}년 ${month}월 ${day}일 (${weekday}) ${hour}:${minute}`;
};

function ActivityCard({ activity }: { activity: Activity }) {
    return (
        <div className="activity-card">
            <div className="activity-header">
                <div className="activity-date">{formatDate(activity.startTime)}</div>
                <div className="activity-gear">{activity.gearName || ""}</div>
            </div>

            <div className="activity-map">
                {activity.polyline ? (
                    <ActivityMap polyline={activity.polyline} height={200} />
                ) : (
                    <div className="map-placeholder">경로 데이터 없음</div>
                )}
            </div>

            <div className="activity-stats-main">
                <div className="stat-item large">
                    <span className="stat-value">{formatDistance(activity.totalDistance)}</span>
                    <span className="stat-label">km</span>
                </div>
                <div className="stat-item large">
                    <span className="stat-value">{formatTime(activity.movingTime)}</span>
                    <span className="stat-label">이동 시간</span>
                </div>
                <div className="stat-item large">
                    <span className="stat-value">{formatTime(activity.elapsedTime)}</span>
                    <span className="stat-label">전체 시간</span>
                </div>
                <div className="stat-item large">
                    <span className="stat-value">{activity.totalAscent?.toFixed(0) || 0}</span>
                    <span className="stat-label">m 획득고도</span>
                </div>
            </div>

            <div className="activity-stats-detail">
                <div className="stat-row">
                    <div className="stat-item">
                        <span className="stat-icon">⚡</span>
                        <span className="stat-label">평균 속도</span>
                        <span className="stat-value">{activity.avgSpeed?.toFixed(1) || 0} km/h</span>
                    </div>
                    <div className="stat-item">
                        <span className="stat-icon">🚀</span>
                        <span className="stat-label">최대 속도</span>
                        <span className="stat-value">{activity.maxSpeed?.toFixed(1) || 0} km/h</span>
                    </div>
                </div>
            </div>
        </div>
    );
}

export default function RidingRecordPage() {
    const [activities, setActivities] = useState<Activity[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        fetchActivities()
            .then(setActivities)
            .catch((e) => setError(e.message))
            .finally(() => setLoading(false));
    }, []);

    if (loading) return <div className="riding-page loading">로딩 중...</div>;
    if (error) return <div className="riding-page error">에러: {error}</div>;

    return (
        <div className="riding-page">
            <h2>🚴 라이딩 기록</h2>
            <div className="activity-feed">
                {activities.length === 0 ? (
                    <p>라이딩 기록이 없습니다.</p>
                ) : (
                    activities.map((activity) => (
                        <ActivityCard key={activity.id} activity={activity} />
                    ))
                )}
            </div>
        </div>
    );
}
