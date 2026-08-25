import { useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
    BarChart,
    Bar,
    XAxis,
    YAxis,
    CartesianGrid,
    Tooltip,
    Legend,
} from 'recharts';

interface Bike {
    id: number;
    name: string;
    brand?: string;
    model?: string;
    modelYear?: number;
    photoUrl?: string;
    totalDistance?: number;
    totalTime?: number;
}

interface GearUsageSummary {
    frontGear: number;
    rearGear: number;
    terrain: 'UPHILL' | 'FLAT' | 'DOWNHILL';
    durationSec: number;
    distance: number;
}

const km = (meters?: number) =>
    ((meters ?? 0) / 1000).toLocaleString(undefined, { maximumFractionDigits: 0 });

const hours = (seconds?: number) =>
    ((seconds ?? 0) / 3600).toLocaleString(undefined, { maximumFractionDigits: 1 });

// dataviz 스킬 검증된 카테고리 팔레트(1·2·3번 슬롯) — UPHILL/FLAT/DOWNHILL 고정 순서
const TERRAIN_COLOR: Record<string, string> = {
    UPHILL: '#2a78d6',
    FLAT: '#eb6834',
    DOWNHILL: '#1baf7a',
};
const TERRAIN_LABEL: Record<string, string> = {
    UPHILL: '업힐',
    FLAT: '평지',
    DOWNHILL: '다운힐',
};
const TERRAIN_ORDER = ['UPHILL', 'FLAT', 'DOWNHILL'] as const;

function minutes(sec: number) {
    return Math.round((sec / 60) * 10) / 10;
}

/** 체인링(아우터/이너) 하나에 대한 코그별 사용 시간 막대 차트. */
function ChainringChart({ title, teeth, rows }: {
    title: string;
    teeth: number;
    rows: GearUsageSummary[];
}) {
    const cogs = Array.from(new Set(rows.map((r) => r.rearGear))).sort((a, b) => a - b);

    const chartData = cogs.map((cog) => {
        const entry: Record<string, number | string> = { cog: `${cog}T` };
        for (const terrain of TERRAIN_ORDER) {
            const match = rows.find((r) => r.rearGear === cog && r.terrain === terrain);
            entry[terrain] = match ? minutes(match.durationSec) : 0;
        }
        return entry;
    });

    const chartWidth = Math.max(cogs.length * 56, 320);

    return (
        <div style={{ marginBottom: 32 }}>
            <h4 style={{ margin: '0 0 4px' }}>{title} <span style={{ color: '#94a3b8', fontWeight: 400, fontSize: 13 }}>{teeth}T</span></h4>
            {chartData.length === 0 ? (
                <p style={{ color: '#94a3b8', fontSize: 13 }}>이 체인링으로 기록된 기어 사용량이 없습니다.</p>
            ) : (
                <div style={{
                    width: '100%',
                    border: '1px solid #e5e7eb',
                    borderRadius: 8,
                    overflowX: 'auto',
                    overflowY: 'hidden',
                    padding: '8px 4px',
                }}>
                    <BarChart width={chartWidth} height={260} data={chartData} margin={{ top: 8, right: 16, bottom: 8, left: 4 }}>
                        <CartesianGrid strokeDasharray="3 3" stroke="#e1e0d9" vertical={false} />
                        <XAxis dataKey="cog" tick={{ fontSize: 12, fill: '#52514e' }} axisLine={{ stroke: '#c3c2b7' }} tickLine={false} />
                        <YAxis
                            width={40}
                            tick={{ fontSize: 12, fill: '#898781' }}
                            axisLine={false}
                            tickLine={false}
                            label={{ value: '분', position: 'insideTopLeft', fontSize: 11, fill: '#898781' }}
                        />
                        <Tooltip
                            formatter={(value: number | undefined, name: string | undefined) => [value != null ? `${value}분` : "-", (name && TERRAIN_LABEL[name]) ?? name ?? ""]}
                            labelFormatter={(label) => `코그 ${label}`}
                        />
                        <Legend formatter={(value) => TERRAIN_LABEL[value] ?? value} wrapperStyle={{ fontSize: 12 }} />
                        {TERRAIN_ORDER.map((terrain) => (
                            <Bar key={terrain} dataKey={terrain} fill={TERRAIN_COLOR[terrain]} radius={[4, 4, 0, 0]} maxBarSize={24} />
                        ))}
                    </BarChart>
                </div>
            )}
        </div>
    );
}

export default function BikeDetailPage() {
    const { id } = useParams<{ id: string }>();
    const [bike, setBike] = useState<Bike | null>(null);
    const [gearUsage, setGearUsage] = useState<GearUsageSummary[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        if (!id) return;
        setLoading(true);
        Promise.all([
            fetch(`/api/bikes/${id}`).then((r) => {
                if (!r.ok) throw new Error(`자전거 정보를 불러오지 못했습니다 (${r.status})`);
                return r.json();
            }),
            fetch(`/api/bikes/${id}/gear-usage`).then((r) => {
                if (!r.ok) throw new Error(`기어 사용량을 불러오지 못했습니다 (${r.status})`);
                return r.json();
            }),
        ])
            .then(([bikeRes, gearRes]) => {
                setBike(bikeRes);
                setGearUsage(gearRes);
            })
            .catch((e) => setError(e instanceof Error ? e.message : '오류'))
            .finally(() => setLoading(false));
    }, [id]);

    // front_gear는 FIT에서 관측된 실제 체인링 톱니수(T) — 큰 쪽이 아우터, 작은 쪽이 이너
    const { outerTeeth, innerTeeth, outerRows, innerRows } = useMemo(() => {
        const fronts = Array.from(new Set(gearUsage.map((g) => g.frontGear))).sort((a, b) => b - a);
        const outer = fronts[0];
        const inner = fronts[1];
        return {
            outerTeeth: outer,
            innerTeeth: inner,
            outerRows: outer != null ? gearUsage.filter((g) => g.frontGear === outer) : [],
            innerRows: inner != null ? gearUsage.filter((g) => g.frontGear === inner) : [],
        };
    }, [gearUsage]);

    if (loading) return <div style={{ maxWidth: 980, margin: '0 auto', padding: 24 }}>불러오는 중…</div>;
    if (error) return <div style={{ maxWidth: 980, margin: '0 auto', padding: 24, color: '#dc3545' }}>{error}</div>;
    if (!bike) return null;

    return (
        <div style={{ maxWidth: 980, margin: '0 auto', padding: 24 }}>
            <Link to="/bikes" style={{ fontSize: 13, color: '#64748b', textDecoration: 'none' }}>&larr; 내 자전거</Link>

            <div style={{ display: 'flex', gap: 20, alignItems: 'center', margin: '12px 0 24px' }}>
                {bike.photoUrl && (
                    <img
                        src={bike.photoUrl}
                        alt={bike.name}
                        style={{ width: 120, height: 90, objectFit: 'cover', borderRadius: 8 }}
                    />
                )}
                <div>
                    <h2 style={{ margin: '0 0 4px' }}>{bike.name}</h2>
                    <div style={{ color: '#666', fontSize: 13, marginBottom: 8 }}>
                        {[bike.brand, bike.model].filter(Boolean).join(' · ')}
                        {bike.modelYear ? ` (${bike.modelYear})` : ''}
                    </div>
                    <div style={{ display: 'flex', gap: 20 }}>
                        <div>
                            <div style={{ fontSize: 18, fontWeight: 600 }}>{km(bike.totalDistance)}</div>
                            <div style={{ fontSize: 12, color: '#888' }}>km 누적</div>
                        </div>
                        <div>
                            <div style={{ fontSize: 18, fontWeight: 600 }}>{hours(bike.totalTime)}</div>
                            <div style={{ fontSize: 12, color: '#888' }}>이동시간</div>
                        </div>
                    </div>
                </div>
            </div>

            <section>
                <h3 style={{ margin: '0 0 4px' }}>기어 사용량</h3>
                <p style={{ margin: '0 0 20px', color: '#94a3b8', fontSize: 13 }}>
                    코그(뒷기어)별로 업힐/평지/다운힐에서 사용한 시간을 합산했습니다.
                </p>

                {gearUsage.length === 0 ? (
                    <p style={{ color: '#94a3b8', fontSize: 14 }}>이 자전거로 기록된 기어 사용량 데이터가 아직 없습니다.</p>
                ) : (
                    <>
                        {outerTeeth != null && <ChainringChart title="아우터 체인링" teeth={outerTeeth} rows={outerRows} />}
                        {innerTeeth != null && <ChainringChart title="이너 체인링" teeth={innerTeeth} rows={innerRows} />}
                    </>
                )}
            </section>
        </div>
    );
}
