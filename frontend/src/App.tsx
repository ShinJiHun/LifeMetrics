import { Routes, Route, Navigate, useNavigate } from "react-router-dom";

import Layout from "@/components/common/Layout";
import { RequireAdmin } from "@/components/common/AdminOnly";
import BodyRecordPage from "@/pages/ride/body/BodyRecordPage";
import WeightLossAnalysisPage from "@/pages/ride/body/WeightLossAnalysisPage";
import HealthCheckupPage from "@/pages/ride/body/HealthCheckupPage";
import ExerciseItemPage from "@/pages/ride/health/ExerciseItemPage";
import ExerciseLogPage from "@/pages/ride/health/ExerciseInputPage";
import ExerciseHistoryPage from "@/pages/ride/health/ExerciseHistoryPage";
import RidingRecordPage from "@/pages/ride/riding/RidingRecordPage";
import BrevePlanPage from "@/pages/ride/plan/BrevePlanPage";
import PermanentCoursesPage from "@/pages/ride/plan/PermanentCoursesPage";
import LiveRidePage from "@/pages/ride/plan/LiveRidingPage";
import ActivityDetailPage from "@/pages/ride/riding/ActivityDetailPage";
import RideLivePage from "@/pages/ride/riding/RideLivePage";
import PersonaPortfolioPage from "@/pages/persona/PersonaPortfolioPage";
import CareerCompanyDetailPage from "@/pages/persona/CareerCompanyDetailPage";
import ProfileManagePage from "@/pages/persona/ProfileManagePage";
import PersonaChatPage from "@/pages/persona/PersonaChatPage";
import PersonaGate from "@/pages/persona/PersonaGate";
import LottoStatsPage from "@/pages/human/lotto/LottoStatsPage";
import LottoCreatePage from "@/pages/human/lotto/LottoCreatePage";
import BlogHomePage from "@/pages/blog/BlogHomePage";
import MenuManagePage from "@/pages/blog/MenuManagePage";
import SubCategoryPage from "@/pages/blog/SubCategoryPage";
import PostDetailPage from "@/pages/blog/PostDetailPage";
import PostEditorPage from "@/pages/blog/PostEditorPage";

import "@/styles/global.css";
import BikeRegisterPage from "@/pages/ride/riding/Bikeregisterpage.tsx";
import BikeListPage from "@/pages/ride/riding/BikeListPage.tsx";
import BikeDetailPage from "@/pages/ride/riding/BikeDetailPage.tsx";

function PersonaGateRoute() {
    const navigate = useNavigate();
    return <PersonaGate onSelect={(_key, route) => navigate(route)} />;
}

export default function App() {
    return (
        <Routes>
            {/* 첫 페이지: 페르소나 선택 게이트 (사이드바 Layout 밖, 풀스크린) */}
            <Route path="/" element={<PersonaGateRoute />} />

            <Route element={<Layout />}>
                <Route path="/records/body" element={<BodyRecordPage />} />
                <Route path="/records/body/weight-loss" element={<WeightLossAnalysisPage />} />
                <Route path="/records/body/health-checkup" element={<RequireAdmin><HealthCheckupPage /></RequireAdmin>} />
                <Route path="/records/health" element={<Navigate to="/records/health/history" replace />} />
                <Route path="/records/health/items" element={<ExerciseItemPage />} />
                <Route path="/records/health/log" element={<ExerciseLogPage />} />
                <Route path="/records/health/history" element={<ExerciseHistoryPage />} />
                <Route path="/records/riding" element={<RidingRecordPage />} />
                <Route path="/plan/brevet" element={<BrevePlanPage />} />
                <Route path="/plan/permanent" element={<PermanentCoursesPage />} />
                <Route path="/plan/live" element={<LiveRidePage />} />
                <Route path="/plan/touring" element={<div style={{ padding: 24 }}>🏕️ 투어링 계획 (준비중)</div>} />
                <Route path="/records/riding/:id" element={<ActivityDetailPage />} />
                <Route path="/records/riding/:id/live" element={<RideLivePage />} />
                <Route path="/persona" element={<Navigate to="/" replace />} />
                <Route path="/persona/developer" element={<PersonaPortfolioPage />} />
                <Route path="/persona/developer/manage" element={<RequireAdmin><ProfileManagePage /></RequireAdmin>} />
                <Route path="/persona/developer/chat" element={<PersonaChatPage />} />
                <Route path="/persona/developer/career-detail/:companyId" element={<CareerCompanyDetailPage />} />
                <Route path="/persona/developer/career-detail/:companyId/:page" element={<CareerCompanyDetailPage />} />
                <Route path="/persona/developer/:section" element={<PersonaPortfolioPage />} />

                {/* 개발자 / 인간 페르소나 블로그 (대메뉴 → 소메뉴 → 글) */}
                {["developer", "human"].map((p) => (
                    <Route key={p}>
                        <Route path={`/${p}`} element={<BlogHomePage />} />
                        {p === "human" && (
                            <Route path={`/${p}/manage`} element={<RequireAdmin><MenuManagePage /></RequireAdmin>} />
                        )}
                        <Route path={`/${p}/sub/:subId`} element={<SubCategoryPage />} />
                        <Route path={`/${p}/post/new`} element={<RequireAdmin><PostEditorPage /></RequireAdmin>} />
                        <Route path={`/${p}/post/:postId`} element={<PostDetailPage />} />
                        <Route path={`/${p}/post/:postId/edit`} element={<RequireAdmin><PostEditorPage /></RequireAdmin>} />
                    </Route>
                ))}

                {/* 인간 페르소나 - 로또 (관리자 전용) */}
                <Route path="/human/lotto" element={<Navigate to="/human/lotto/stats" replace />} />
                <Route path="/human/lotto/stats" element={<RequireAdmin><LottoStatsPage /></RequireAdmin>} />
                <Route path="/human/lotto/create" element={<RequireAdmin><LottoCreatePage /></RequireAdmin>} />

                <Route path="/bikes" element={<BikeListPage />} />
                <Route path="/bikes/register" element={<RequireAdmin><BikeRegisterPage /></RequireAdmin>} />
                <Route path="/bikes/:id" element={<BikeDetailPage />} />
            </Route>

            <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
    );
}