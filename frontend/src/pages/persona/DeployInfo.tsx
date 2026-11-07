import { useEffect, useState } from "react";

// deploy.sh 가 static 루트에 쓰는 배포 정보 (로컬 npm run dev 에는 없어서 아무것도 안 보임)
type DeployInfoData = {
    env: "dev" | "prod";
    deployedAt: string;
    builtAt: string;
    commit: string;
};

function isDeployInfo(v: unknown): v is DeployInfoData {
    const o = v as Partial<DeployInfoData> | null;
    return typeof o?.env === "string" && typeof o.deployedAt === "string" && typeof o.commit === "string";
}

export default function DeployInfo() {
    const [info, setInfo] = useState<DeployInfoData | null>(null);

    useEffect(() => {
        let alive = true;
        fetch("/deploy.json", { cache: "no-store" })
            .then((r) => (r.ok ? r.json() : null))
            .then((v) => {
                if (alive && isDeployInfo(v)) setInfo(v);
            })
            .catch(() => {});
        return () => {
            alive = false;
        };
    }, []);

    if (!info) return null;

    const isDev = info.env === "dev";
    const title =
        info.builtAt && info.builtAt !== info.deployedAt
            ? `빌드 ${info.builtAt} · 배포 ${info.deployedAt} · ${info.commit}`
            : `배포 ${info.deployedAt} · ${info.commit}`;

    return (
        <div
            title={title}
            style={{
                position: "absolute",
                right: 16,
                bottom: 12,
                display: "inline-flex",
                alignItems: "center",
                gap: 6,
                fontSize: 11,
                color: "#8a90a6",
                fontVariantNumeric: "tabular-nums",
                userSelect: "text",
            }}
        >
            <span
                style={{
                    padding: "1px 6px",
                    borderRadius: 999,
                    fontWeight: 700,
                    background: isDev ? "#fef3c7" : "#e0e7ff",
                    color: isDev ? "#92400e" : "#3730a3",
                }}
            >
                {info.env}
            </span>
            <span>배포 {info.deployedAt}</span>
            <span style={{ opacity: 0.7 }}>{info.commit}</span>
        </div>
    );
}
