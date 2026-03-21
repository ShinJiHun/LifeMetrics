import { Routes, Route, Navigate } from "react-router-dom";

import Layout from "@/components/Layout";
import BodyRecordPage from "@/pages/BodyRecordPage";
import ExerciseItemPage from "@/pages/ExerciseItemPage";
import ExerciseLogPage from "@/pages/ExerciseInputPage";
import ExerciseHistoryPage from "@/pages/ExerciseHistoryPage";
import RidingRecordPage from "@/pages/RidingRecordPage";
import BrevePlanPage from "@/pages/BrevePlanPage";
import ActivityDetailPage from "@/pages/ActivityDetailPage";

import "@/styles/global.css";

export default function App() {
    return (
        <Routes>
            <Route element={<Layout />}>
                <Route path="/" element={<BodyRecordPage />} />
                <Route path="/records/body" element={<BodyRecordPage />} />
                <Route path="/records/health" element={<Navigate to="/records/health/history" replace />} />
                {/*<Route path="/records/health" element={<Navigate to="/records/health/log" replace />} />*/}
                <Route path="/records/health/items" element={<ExerciseItemPage />} />
                <Route path="/records/health/log" element={<ExerciseLogPage />} />
                <Route path="/records/health/history" element={<ExerciseHistoryPage />} />
                <Route path="/records/riding" element={<RidingRecordPage />} />
                <Route path="/plan/brevet" element={<BrevePlanPage />} />
                <Route path="/plan/touring" element={<div style={{padding:24}}>🏕️ 투어링 계획 (준비중)</div>} />
                <Route path="/records/riding/:id" element={<ActivityDetailPage />} />
            </Route>

            <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
    );
}
