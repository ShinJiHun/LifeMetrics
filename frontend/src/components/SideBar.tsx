import { NavLink } from "react-router-dom";

const linkStyle = ({ isActive }: { isActive: boolean }) => ({
  padding: "10px 14px",
  textDecoration: "none",
  color: isActive ? "#2563eb" : "#111",
  fontWeight: isActive ? "bold" : "normal",
});

export default function SideBar() {
  return (
    <aside
      style={{
        width: 220,
        borderRight: "1px solid #e5e7eb",
        padding: 16,
      }}
    >
      <h3>📊 기록</h3>
      <nav style={{ display: "flex", flexDirection: "column", gap: 6 }}>
        <NavLink to="/records/body" style={linkStyle}>
          🧍 신체 기록
        </NavLink>
        <NavLink to="/records/health" style={linkStyle}>
          🏋️ 헬스 기록
        </NavLink>
        <NavLink to="/records/riding" style={linkStyle}>
          🚴 라이딩 기록
        </NavLink>
      </nav>

      <h3 style={{marginTop: 24}}>🗺️ 계획</h3>
      <nav style={{display: "flex", flexDirection: "column", gap: 6}}>
        <NavLink to="/plan/brevet" style={linkStyle}>
          🏅 랜도너스 계획
        </NavLink>
        <NavLink to="/plan/touring" style={linkStyle}>
          🏕️ 투어링 계획
        </NavLink>
      </nav>
    </aside>
  );
}
