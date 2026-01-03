// src/App.tsx
import { Routes, Route, Navigate } from "react-router-dom";

import Layout from "@/components/Layout";
import BodyRecordPage from "@/pages/BodyRecordPage";
import HealthRecordPage from "@/pages/HealthRecordPage";
import ExerciseLogPage from "@/pages/ExerciseLogPage";

export default function App() {
    return (
        <Routes>
            {/* 🔹 사이드바 포함 영역 */}
            <Route element={<Layout />}>
                <Route path="/" element={<BodyRecordPage />} />
                <Route
                    path="/records/health"
                    element={<HealthRecordPage />}
                />
                <Route
                    path="/records/health/exercise"
                    element={<ExerciseLogPage />}
                />
            </Route>

            {/* fallback */}
            <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
    );
}
