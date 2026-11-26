import { Routes, Route, Navigate, useNavigate } from "react-router-dom";

import Layout from "@/components/common/Layout";
import { RequireAdmin } from "@/components/common/AdminOnly";
import BodyRecordPage from "@/pages/ride/body/BodyRecordPage";
import WeightLossAnalysisPage from "@/pages/ride/body/WeightLossAnalysisPage";
import DailyAnalysisPage from "@/pages/ride/analysis/DailyAnalysisPage";
import AthleteChatPage from "@/pages/ride/analysis/AthleteChatPage";
import PowerProfilePage from "@/pages/ride/analysis/PowerProfilePage";
import WorkLogPage from "@/pages/persona/WorkLogPage";
import HealthCheckupPage from "@/pages/ride/body/HealthCheckupPage";
import HealthTreatmentPage from "@/pages/ride/body/HealthTreatmentPage";
import HandlerSettlementPage from "@/pages/human/HandlerSettlementPage";
import HandlerTripsPage from "@/pages/human/HandlerTripsPage";
import ReadingNotesPage from "@/pages/human/ReadingNotesPage";
import DiaryPage from "@/pages/human/DiaryPage";
import LecturesPage from "@/pages/human/LecturesPage";
import DevRequestsPage from "@/pages/DevRequestsPage";
import DocsPage from "@/pages/DocsPage";
import BodyGoalPage from "@/pages/ride/plan/BodyGoalPage";
import FittingDataPage from "@/pages/ride/plan/FittingDataPage";
import GearPlanPage from "@/pages/ride/plan/GearPlanPage";
import ExerciseItemPage from "@/pages/ride/health/ExerciseItemPage";
import ExerciseLogPage from "@/pages/ride/health/ExerciseInputPage";
import ExerciseHistoryPage from "@/pages/ride/health/ExerciseHistoryPage";
import CardioPage from "@/pages/ride/health/CardioPage";
import RidingRecordPage from "@/pages/ride/riding/RidingRecordPage";
import GlossaryPage from "@/pages/ride/guide/GlossaryPage";
import RidingMapPage from "@/pages/ride/riding/RidingMapPage";
import RandonneuringRecordPage from "@/pages/ride/riding/RandonneuringRecordPage";
import BrevePlanPage from "@/pages/ride/plan/BrevePlanPage";
import PermanentCoursesPage from "@/pages/ride/plan/PermanentCoursesPage";
import LiveRidePage from "@/pages/ride/plan/LiveRidingPage";
import ActivityDetailPage from "@/pages/ride/riding/ActivityDetailPage";
import DeviceListPage from "@/pages/ride/riding/DeviceListPage";
import RideLivePage from "@/pages/ride/riding/RideLivePage";
import PersonaPortfolioPage from "@/pages/persona/PersonaPortfolioPage";
import CareerCompanyDetailPage from "@/pages/persona/CareerCompanyDetailPage";
import ProfileManagePage from "@/pages/persona/ProfileManagePage";
import DevExperiencePage from "@/pages/persona/DevExperiencePage";
import PersonaChatPage from "@/pages/persona/PersonaChatPage";
import PersonaGate from "@/pages/persona/PersonaGate";
import LottoStatsPage from "@/pages/lottery/lotto/LottoStatsPage";
import TossAccountPage from "@/pages/stock/TossAccountPage";
import LottoCreatePage from "@/pages/lottery/lotto/LottoCreatePage";
import PensionStatsPage from "@/pages/lottery/pension/PensionStatsPage";
import LottoRecordsPage from "@/pages/lottery/lotto/LottoRecordsPage";
import BlogHomePage from "@/pages/blog/BlogHomePage";
import PensionRecordsPage from "@/pages/lottery/pension/PensionRecordsPage";
import PensionCreatePage from "@/pages/lottery/pension/PensionCreatePage";
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
                <Route path="/records/daily-analysis" element={<RequireAdmin><DailyAnalysisPage /></RequireAdmin>} />
                <Route path="/records/athlete-chat" element={<RequireAdmin><AthleteChatPage /></RequireAdmin>} />
                {/* 업무 일지는 개발 페르소나로 이동 — 예전 주소는 새 주소로 보낸다 */}
                <Route path="/records/worklog" element={<Navigate to="/persona/developer/worklog" replace />} />
                <Route path="/records/body" element={<BodyRecordPage />} />
                <Route path="/records/body/weight-loss" element={<WeightLossAnalysisPage />} />
                <Route path="/records/body/health-checkup" element={<RequireAdmin><HealthCheckupPage /></RequireAdmin>} />
                <Route path="/records/body/treatments" element={<RequireAdmin><HealthTreatmentPage /></RequireAdmin>} />
                <Route path="/records/health" element={<Navigate to="/records/health/history" replace />} />
                <Route path="/records/health/items" element={<ExerciseItemPage />} />
                <Route path="/records/health/log" element={<ExerciseLogPage />} />
                <Route path="/records/health/history" element={<ExerciseHistoryPage />} />
                <Route path="/records/health/cardio" element={<CardioPage />} />
                <Route path="/records/riding" element={<RidingRecordPage />} />
                <Route path="/records/riding-map" element={<RidingMapPage />} />
                <Route path="/records/randonneuring" element={<RandonneuringRecordPage />} />
                <Route path="/records/power-profile" element={<PowerProfilePage />} />
                <Route path="/plan/brevet" element={<BrevePlanPage />} />
                <Route path="/plan/permanent" element={<PermanentCoursesPage />} />
                <Route path="/plan/live" element={<LiveRidePage />} />
                <Route path="/plan/touring" element={<div style={{ padding: 24 }}>🏕️ 투어링 계획 (준비중)</div>} />
                <Route path="/plan/body-goal" element={<BodyGoalPage />} />
                <Route path="/guide/glossary" element={<GlossaryPage />} />
                <Route path="/plan/fitting" element={<FittingDataPage />} />
                <Route path="/plan/gear" element={<GearPlanPage />} />
                <Route path="/records/riding/:id" element={<ActivityDetailPage />} />
                <Route path="/records/riding/:id/live" element={<RideLivePage />} />
                <Route path="/persona" element={<Navigate to="/" replace />} />
                <Route path="/persona/developer" element={<PersonaPortfolioPage />} />
                <Route path="/persona/developer/manage" element={<RequireAdmin><ProfileManagePage /></RequireAdmin>} />
                <Route path="/persona/developer/experiences" element={<RequireAdmin><DevExperiencePage /></RequireAdmin>} />
                <Route path="/persona/developer/chat" element={<PersonaChatPage />} />
                <Route path="/persona/developer/worklog" element={<RequireAdmin><WorkLogPage /></RequireAdmin>} />
                <Route path="/persona/developer/worklog/upload" element={<RequireAdmin><WorkLogPage initialTab="upload" /></RequireAdmin>} />
                <Route path="/persona/developer/career-detail/:companyId" element={<CareerCompanyDetailPage />} />
                <Route path="/persona/developer/career-detail/:companyId/:page" element={<CareerCompanyDetailPage />} />
                <Route path="/persona/developer/:section" element={<PersonaPortfolioPage />} />

                {/* 개발자 / 인간 / 증권 페르소나 블로그 (대메뉴 → 소메뉴 → 글) */}
                {["developer", "human", "stock"].map((p) => (
                    <Route key={p}>
                        <Route path={`/${p}`} element={<BlogHomePage />} />
                        {p !== "developer" && (
                            <Route path={`/${p}/manage`} element={<RequireAdmin><MenuManagePage /></RequireAdmin>} />
                        )}
                        <Route path={`/${p}/sub/:subId`} element={<SubCategoryPage />} />
                        <Route path={`/${p}/post/new`} element={<RequireAdmin><PostEditorPage /></RequireAdmin>} />
                        <Route path={`/${p}/post/:postId`} element={<PostDetailPage />} />
                        <Route path={`/${p}/post/:postId/edit`} element={<RequireAdmin><PostEditorPage /></RequireAdmin>} />
                    </Route>
                ))}

                {/* 증권 페르소나 - 토스증권 계좌·보유 종목·거래내역 (관리자 전용, 읽기만) */}
                <Route path="/stock/toss" element={<RequireAdmin><TossAccountPage /></RequireAdmin>} />

                {/* 인간 페르소나 - 핸들러 월별 정산 PDF (관리자 전용) */}
                <Route path="/human/handler-settlement" element={<RequireAdmin><HandlerSettlementPage /></RequireAdmin>} />
                <Route path="/human/handler-trips" element={<RequireAdmin><HandlerTripsPage /></RequireAdmin>} />
                {/* 인간 페르소나 - 독서 노트 기록 CSV 업로드 (관리자 전용) */}
                <Route path="/human/reading-notes" element={<RequireAdmin><ReadingNotesPage /></RequireAdmin>} />
                {/* 인간 페르소나 - 일기 (메모 → 새벽 초안 → 확인 후 블로그 게시, 관리자 전용) */}
                <Route path="/human/diary" element={<RequireAdmin><DiaryPage /></RequireAdmin>} />
                {/* 인간 페르소나 - 강연 요약 목록 (일기 메모의 강연 필기 → 요약, 태그·연사·기간 필터, 관리자 전용) */}
                <Route path="/human/lectures" element={<RequireAdmin><LecturesPage /></RequireAdmin>} />

                {/* 개발 요청함 (관리자 전용) — 서버 워커가 Claude 로 작업해 개발 서버에 배포 */}
                <Route path="/dev-requests" element={<RequireAdmin><DevRequestsPage /></RequireAdmin>} />
                <Route path="/docs" element={<Navigate to="/docs/plan" replace />} />
                <Route path="/docs/:slug" element={<RequireAdmin><DocsPage /></RequireAdmin>} />
                <Route path="/docs/:slug/:date" element={<RequireAdmin><DocsPage /></RequireAdmin>} />

                {/* 인간 페르소나 - 로또 (관리자 전용) */}
                {/* 인간 페르소나 - 연금복권 (관리자 전용) */}
                <Route path="/lottery" element={<Navigate to="/lottery/lotto/stats" replace />} />
                <Route path="/lottery/pension" element={<Navigate to="/lottery/pension/stats" replace />} />
                <Route path="/lottery/pension/stats" element={<RequireAdmin><PensionStatsPage /></RequireAdmin>} />

                <Route path="/lottery/lotto" element={<Navigate to="/lottery/lotto/stats" replace />} />
                <Route path="/lottery/lotto/stats" element={<RequireAdmin><LottoStatsPage /></RequireAdmin>} />
                <Route path="/lottery/lotto/create" element={<RequireAdmin><LottoCreatePage /></RequireAdmin>} />

                <Route path="/bikes" element={<BikeListPage />} />
                <Route path="/bikes/devices" element={<DeviceListPage />} />
                <Route path="/lottery/lotto/records" element={<RequireAdmin><LottoRecordsPage /></RequireAdmin>} />
                <Route path="/lottery/pension/records" element={<RequireAdmin><PensionRecordsPage /></RequireAdmin>} />
                <Route path="/lottery/pension/create" element={<RequireAdmin><PensionCreatePage /></RequireAdmin>} />
                <Route path="/bikes/register" element={<RequireAdmin><BikeRegisterPage /></RequireAdmin>} />
                <Route path="/bikes/:id" element={<BikeDetailPage />} />
            </Route>

            <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
    );
}