// src/components/Layout.tsx
import { useEffect, useState } from "react";
import { Outlet, useLocation } from "react-router-dom";
import SideBar from "@/components/common/SideBar.tsx";
import AdminBadge from "@/components/common/AdminBadge";
import "@/styles/sidebar.css";

export default function Layout() {
    const [sidebarOpen, setSidebarOpen] = useState(false);
    const { pathname } = useLocation();

    // 페이지 이동 시 모바일 사이드바 자동으로 닫기
    useEffect(() => {
        setSidebarOpen(false);
    }, [pathname]);

    return (
        <div style={{display: "flex", height: "100vh"}}>
            <button
                className={`menu-toggle${sidebarOpen ? " open" : ""}`}
                onClick={() => setSidebarOpen((v) => !v)}
                aria-label="메뉴 열기"
            >
                <span/>
                <span/>
                <span/>
            </button>

            {sidebarOpen && (
                <div
                    className="sidebar-overlay visible"
                    onClick={() => setSidebarOpen(false)}
                />
            )}

            <SideBar open={sidebarOpen}/>

            {/* 메인 영역 */}
            <main className="app-main">
                {/* 현재 모드 표시 + 관리자 로그인 진입점 */}
                <div style={{display: "flex", justifyContent: "flex-end", marginBottom: 8}}>
                    <AdminBadge/>
                </div>
                <Outlet/>
            </main>
        </div>
    );
}
