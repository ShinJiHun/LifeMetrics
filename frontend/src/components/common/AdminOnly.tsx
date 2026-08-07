// src/components/common/AdminOnly.tsx
//
// 관리자에게만 보이는 UI 를 감싼다. 서버가 실제 권한을 강제하므로
// 여기서 숨기는 건 "보여줘 봐야 눌러도 막히는" 버튼을 감추기 위한 것이다.

import type { ReactNode } from "react";
import { Navigate } from "react-router-dom";
import { useAdmin } from "@/lib/admin";

export default function AdminOnly({ children }: { children: ReactNode }) {
    const { isAdmin, loading } = useAdmin();
    if (loading || !isAdmin) return null;
    return <>{children}</>;
}

/**
 * 관리자 전용 라우트. 보기 전용 방문자가 URL 을 직접 입력해도 첫 화면으로 돌려보낸다.
 * 상태 조회가 끝나기 전에 튕기지 않도록 loading 중에는 아무것도 렌더하지 않는다.
 */
export function RequireAdmin({ children }: { children: ReactNode }) {
    const { isAdmin, loading } = useAdmin();
    if (loading) return null;
    if (!isAdmin) return <Navigate to="/" replace />;
    return <>{children}</>;
}