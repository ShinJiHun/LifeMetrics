import { Routes, Route } from "react-router-dom";
import Layout from "./components/Layout";

import BodyRecordPage from "./pages/BodyRecordPage";
import HealthRecordPage from "./pages/HealthRecordPage";
import RidingRecordPage from "./pages/RidingRecordPage";
import ExerciseBodyPage from "./pages/ExerciseBodyPage";

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        {/* 기본 진입 */}
        <Route index element={<BodyRecordPage />} />

        {/* 기록 */}
        <Route path="/records/body" element={<BodyRecordPage />} />
        <Route path="/records/health" element={<HealthRecordPage />} />
        <Route path="/records/riding" element={<RidingRecordPage />} />

        {/* 운동 */}
        <Route path="/exercise/items" element={<ExerciseBodyPage />} />
      </Route>
    </Routes>
  );
}
