// src/pages/HealthRecordPage.tsx
import { Link } from "react-router-dom";

export default function HealthRecordPage() {
    return (
        <div style={{ padding: 24 }}>
            <h2>🏋️ 헬스 기록</h2>

            <ul style={{ marginTop: 16 }}>
                <li>
                    <Link to="/records/health/exercise">
                        ➕ 운동 기록 입력
                    </Link>
                </li>
            </ul>
        </div>
    );
}
