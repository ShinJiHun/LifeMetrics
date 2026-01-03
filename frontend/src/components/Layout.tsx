// src/components/Layout.tsx
import { Outlet } from "react-router-dom";
import SideBar from "@/components/SideBar";

export default function Layout() {
    return (
        <div style={{ display: "flex", height: "100vh" }}>
            {/* 사이드바 */}
            <SideBar />

            {/* 메인 영역 */}
            <main
                style={{
                    flex: 1,
                    padding: 24,
                    overflowY: "auto",
                    background: "#ffffff",
                }}
            >
                <Outlet />
            </main>
        </div>
    );
}
