// src/lib/admin.tsx
//
// 관리자(쓰기 권한) 상태를 앱 전역에서 공유한다.
//
// 실제 권한 검사는 백엔드 AdminWriteFilter 가 모든 쓰기 요청에 대해 수행한다.
// 여기서 하는 건 UI 표시 판단일 뿐이므로, 이 값을 조작해도 서버가 막는다.

import { createContext, useContext, useEffect, useState } from "react";
import type { ReactNode } from "react";
import api from "@/lib/axios";

interface AdminContextValue {
    /** 관리자로 로그인된 상태인지 */
    isAdmin: boolean;
    /** 최초 상태 조회가 끝났는지 (깜빡임 방지용) */
    loading: boolean;
    /** 성공하면 true. 비밀번호가 틀리면 false */
    login: (password: string) => Promise<boolean>;
    logout: () => Promise<void>;
}

const AdminContext = createContext<AdminContextValue>({
    isAdmin: false,
    loading: true,
    login: async () => false,
    logout: async () => {},
});

export function AdminProvider({ children }: { children: ReactNode }) {
    const [isAdmin, setIsAdmin] = useState(false);
    const [loading, setLoading] = useState(true);

    // 쿠키는 HttpOnly 라 JS 에서 못 읽는다. 서버에 물어봐야 한다.
    useEffect(() => {
        api.get("/admin/status")
            .then((res) => setIsAdmin(Boolean(res.data?.admin)))
            .catch(() => setIsAdmin(false))
            .finally(() => setLoading(false));
    }, []);

    const login = async (password: string) => {
        try {
            const res = await api.post("/admin/login", { password });
            const ok = Boolean(res.data?.admin);
            setIsAdmin(ok);
            return ok;
        } catch {
            setIsAdmin(false);
            return false;
        }
    };

    const logout = async () => {
        try {
            await api.post("/admin/logout");
        } finally {
            setIsAdmin(false);
        }
    };

    return (
        <AdminContext.Provider value={{ isAdmin, loading, login, logout }}>
            {children}
        </AdminContext.Provider>
    );
}

export function useAdmin() {
    return useContext(AdminContext);
}