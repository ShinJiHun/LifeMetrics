// 로또 번호 하나를 공(볼) 모양 뱃지로 표시. 공식 로또 색상 구간을 따른다.
export function lottoBallColor(n: number): string {
    if (n <= 10) return "#fbc400";
    if (n <= 20) return "#69c8f2";
    if (n <= 30) return "#ff7272";
    if (n <= 40) return "#aaa";
    return "#b0d840";
}

export default function LottoNumberBall({
    n,
    size = 30,
    dim = false,
}: {
    n: number;
    size?: number;
    dim?: boolean;
}) {
    return (
        <span
            style={{
                display: "inline-flex",
                alignItems: "center",
                justifyContent: "center",
                width: size,
                height: size,
                borderRadius: "50%",
                background: lottoBallColor(n),
                color: "#fff",
                fontWeight: 700,
                fontSize: size * 0.42,
                opacity: dim ? 0.45 : 1,
                boxShadow: "inset 0 -2px 3px rgba(0,0,0,0.15)",
                flexShrink: 0,
            }}
        >
            {n}
        </span>
    );
}
