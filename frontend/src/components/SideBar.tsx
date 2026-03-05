// src/components/Sidebar.tsx
import { useState } from "react";
import { NavLink, useLocation } from "react-router-dom";
import "@/styles/sidebar.css";

interface SubMenu {
    label: string;
    path: string;
}

interface MenuItem {
    label: string;
    path: string;
    subMenus?: SubMenu[];
}

const MENU_ITEMS: MenuItem[] = [
    { label: "신체 기록", path: "/records/body" },
    {
        label: "헬스 기록",
        path: "/records/health",
        subMenus: [
            { label: "종목 입력", path: "/records/health/items" },
            { label: "운동 기록 입력", path: "/records/health/log" },
            { label: "운동 기록 보기", path: "/records/health/history" },
        ],
    },
    { label: "라이딩 기록", path: "/records/riding" },
];

export default function Sidebar() {
    const location = useLocation();
    const [expandedMenu, setExpandedMenu] = useState<string | null>(null);
    const [isOpen, setIsOpen] = useState(false);

    const isSubMenuActive = (item: MenuItem) => {
        return item.subMenus?.some((sub) => location.pathname === sub.path);
    };

    const handleMenuClick = (item: MenuItem) => {
        if (item.subMenus) {
            setExpandedMenu(expandedMenu === item.path ? null : item.path);
        }
    };

    const isExpanded = (item: MenuItem) => {
        return expandedMenu === item.path || isSubMenuActive(item);
    };

    const toggleMenu = () => setIsOpen(!isOpen);
    const closeMenu = () => setIsOpen(false);

    return (
        <>
            {/* 햄버거 버튼 */}
            <button className={`menu-toggle ${isOpen ? "open" : ""}`} onClick={toggleMenu}>
                <span></span>
                <span></span>
                <span></span>
            </button>

            {/* 오버레이 */}
            <div
                className={`sidebar-overlay ${isOpen ? "visible" : ""}`}
                onClick={closeMenu}
            />

            {/* 사이드바 */}
            <aside className={`sidebar ${isOpen ? "open" : ""}`}>
                <div className="sidebar-logo">
                    <h1>Health</h1>
                </div>

                <nav className="sidebar-nav">
                    <ul className="menu-list">
                        {MENU_ITEMS.map((item) => (
                            <li key={item.path} className="menu-item">
                                {item.subMenus ? (
                                    <div
                                        className={`menu-link has-submenu ${isExpanded(item) ? "expanded" : ""}`}
                                        onClick={() => handleMenuClick(item)}
                                    >
                                        <span>{item.label}</span>
                                        <span className="arrow">{isExpanded(item) ? "▼" : "▶"}</span>
                                    </div>
                                ) : (
                                    <NavLink
                                        to={item.path}
                                        className={({ isActive }) =>
                                            `menu-link ${isActive ? "active" : ""}`
                                        }
                                        onClick={closeMenu}
                                    >
                                        {item.label}
                                    </NavLink>
                                )}

                                {item.subMenus && isExpanded(item) && (
                                    <ul className="submenu-list">
                                        {item.subMenus.map((sub) => (
                                            <li key={sub.path}>
                                                <NavLink
                                                    to={sub.path}
                                                    className={({ isActive }) =>
                                                        `submenu-link ${isActive ? "active" : ""}`
                                                    }
                                                    onClick={closeMenu}
                                                >
                                                    {sub.label}
                                                </NavLink>
                                            </li>
                                        ))}
                                    </ul>
                                )}
                            </li>
                        ))}
                    </ul>
                </nav>
            </aside>
        </>
    );
}