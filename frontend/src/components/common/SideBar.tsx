import { NavLink } from "react-router-dom";

const linkStyle = ({ isActive }: { isActive: boolean }) => ({
  padding: "10px 14px",
  textDecoration: "none",
  color: isActive ? "#2563eb" : "#111",
  fontWeight: isActive ? "bold" : "normal",
});

const subLinkStyle = ({ isActive }: { isActive: boolean }) => ({
  padding: "8px 14px 8px 28px",
  textDecoration: "none",
  color: isActive ? "#2563eb" : "#555",
  fontWeight: isActive ? "bold" : "normal",
  fontSize: 14,
});

export default function SideBar() {
  return (
      <aside style={{ width: 220, borderRight: "1px solid #e5e7eb", padding: 16 }}>
        <h3>📊 기록</h3>
        <nav style={{ display: "flex", flexDirection: "column", gap: 6 }}>
          <NavLink to="/records/body" style={linkStyle}>
            🧍 신체 기록
          </NavLink>

          {/* 헬스/피트니스 - 하위 메뉴 */}
          <div>
            <div style={{ padding: "10px 14px", color: "#111", fontWeight: "bold" }}>
              🏋️ 피트니스
            </div>
            <nav style={{ display: "flex", flexDirection: "column", gap: 2 }}>
              <NavLink to="/records/health/history" style={subLinkStyle}>
                📋 기록
              </NavLink>
              <NavLink to="/records/health/log" style={subLinkStyle}>
                ✏️ 입력
              </NavLink>
            </nav>
          </div>

          <NavLink to="/records/riding" style={linkStyle}>
            🚴 라이딩 기록
          </NavLink>
        </nav>

        <h3 style={{ marginTop: 24 }}>🗺️ 계획</h3>
        <nav style={{ display: "flex", flexDirection: "column", gap: 6 }}>
          <NavLink to="/plan/brevet" style={linkStyle}>
            🏅 랜도너스 계획
          </NavLink>
          <NavLink to="/plan/touring" style={linkStyle}>
            🏕️ 투어링 계획
          </NavLink>
        </nav>

        <h3 style={{ marginTop: 24 }}>🤖 AI</h3>
        <nav style={{ display: "flex", flexDirection: "column", gap: 6 }}>
          <NavLink to="/persona" style={linkStyle}>
            🧑‍💻 신지훈 페르소나 챗
          </NavLink>
        </nav>
      </aside>
  );
}
