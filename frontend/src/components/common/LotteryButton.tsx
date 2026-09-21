import { useNavigate } from "react-router-dom";
import { useAdmin } from "@/lib/admin";

export default function LotteryButton({ style }: { style?: React.CSSProperties }) {
    const navigate = useNavigate();
    const { isAdmin, loading } = useAdmin();

    if (loading || !isAdmin) return null;

    return (
        <button
            type="button"
            onClick={() => navigate("/lottery/lotto/stats")}
            title="복권 관리 페이지로 이동"
            style={{
                display: "inline-flex",
                alignItems: "center",
                gap: 6,
                padding: "6px 12px",
                borderRadius: 999,
                border: "none",
                cursor: "pointer",
                fontSize: 12,
                fontWeight: 700,
                fontFamily: "inherit",
                background: "#fef3c7",
                color: "#92400e",
                ...style,
            }}
        >
            🎰 복권
        </button>
    );
}
