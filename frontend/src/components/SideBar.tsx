import { NavLink } from "react-router-dom";

const linkStyle = ({ isActive }: { isActive: boolean }) => ({
    textDecoration: "none",
    color: isActive ? "#4f46e5" : "#374151",
    fontWeight: isActive ? 600 : 400,
    padding: "6px 0",
});

const sectionTitleStyle = {
    marginTop: 24,
    marginBottom: 8,
    fontSize: 13,
    fontWeight: 700 as const,
    color: "#6b7280",
};

export default function SideBar() {
    return (
        <aside
            style={{
                width: 200,
                padding: 20,
                borderRight: "1px solid #e5e7eb",
                background: "#fafafa",
            }}
        >
            <h2 style={{ marginBottom: 24 }}>Health</h2>

            {/* 📊 기록 */}
            <div>
                <div style={sectionTitleStyle}>기록</div>

                <nav style={{ display: "flex", flexDirection: "column", gap: 4 }}>
                    <NavLink to="/" style={linkStyle}>
                        신체 기록
                    </NavLink>

                    <NavLink to="/records/health" style={linkStyle}>
                        헬스 기록
                    </NavLink>

                    <NavLink to="/records/riding" style={linkStyle}>
                        라이딩 기록
                    </NavLink>
                </nav>
            </div>

            {/* 🏋️ 운동 */}
            <div>
                <div style={sectionTitleStyle}>운동</div>

                <nav style={{ display: "flex", flexDirection: "column", gap: 4 }}>
                    <NavLink
                        to="/records/health/exercise"
                        style={linkStyle}
                    >
                        운동 기록 입력
                    </NavLink>
                </nav>
            </div>
        </aside>
    );
}
