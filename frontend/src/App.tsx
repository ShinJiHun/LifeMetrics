import { Routes, Route, Navigate } from "react-router-dom";

import Layout from "@/components/common/Layout";
import BodyRecordPage from "@/pages/body/BodyRecordPage";
import ExerciseItemPage from "@/pages/health/ExerciseItemPage";
import ExerciseLogPage from "@/pages/health/ExerciseInputPage";
import ExerciseHistoryPage from "@/pages/health/ExerciseHistoryPage";
import RidingRecordPage from "@/pages/riding/RidingRecordPage";
import BrevePlanPage from "@/pages/plan/BrevePlanPage";
import LiveRidePage from "@/pages/plan/LiveRidingPage";
import ActivityDetailPage from "@/pages/riding/ActivityDetailPage";
import PersonaChatPage from "@/pages/persona/PersonaChatPage";

import "@/styles/global.css";
import BikeRegisterPage from "@/pages/riding/Bikeregisterpage.tsx";
import BikeListPage from "@/pages/riding/BikeListPage.tsx";

export default function App() {
    return (
        <Routes>
            <Route element={<Layout />}>
                <Route path="/" element={<BodyRecordPage />} />
                <Route path="/records/body" element={<BodyRecordPage />} />
                <Route path="/records/health" element={<Navigate to="/records/health/history" replace />} />
                <Route path="/records/health/items" element={<ExerciseItemPage />} />
                <Route path="/records/health/log" element={<ExerciseLogPage />} />
                <Route path="/records/health/history" element={<ExerciseHistoryPage />} />
                <Route path="/records/riding" element={<RidingRecordPage />} />
                <Route path="/plan/brevet" element={<BrevePlanPage />} />
                <Route path="/plan/live" element={<LiveRidePage />} />
                <Route path="/plan/touring" element={<div style={{ padding: 24 }}>🏕️ 투어링 계획 (준비중)</div>} />
                <Route path="/records/riding/:id" element={<ActivityDetailPage />} />
                <Route path="/persona" element={<PersonaChatPage />} />
                <Route path="/bikes" element={<BikeListPage />} />
                <Route path="/bikes/register" element={<BikeRegisterPage />} />
            </Route>

            <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
    );
}