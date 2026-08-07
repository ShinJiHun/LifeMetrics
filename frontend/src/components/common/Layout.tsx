// src/components/Layout.tsx
import { Outlet } from "react-router-dom";
import SideBar from "@/components/common/SideBar.tsx";
import AdminBadge from "@/components/common/AdminBadge";

export default function Layout() {
    return (
        <div style={{display: "flex", height: "100vh"}}>
            <SideBar/>

            {/* 메인 영역 */}
            <main
                style={{
                    flex: 1,
                    padding: "24px 24px 24px 0",  // 좌측 패딩 0
                    marginLeft: -30,
                    overflowY: "auto",
                    background: "#ffffff",
                }}
            >
                {/* 현재 모드 표시 + 관리자 로그인 진입점 */}
                <div style={{display: "flex", justifyContent: "flex-end", marginBottom: 8}}>
                    <AdminBadge/>
                </div>
                <Outlet/>
            </main>
        </div>
    );
}
