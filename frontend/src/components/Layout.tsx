import { Outlet } from "react-router-dom";
import SideBar from "./SideBar";

export default function Layout() {
  return (
    <div style={{ display: "flex", height: "100vh" }}>
      <SideBar />
      <main style={{ flex: 1, padding: 24, overflowY: "auto" }}>
        <Outlet />
      </main>
    </div>
  );
}
