import AdminOnly from "@/components/common/AdminOnly";
import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

// bike 테이블 전 컬럼 (gear_name 제외 — 엔티티에 매핑돼 있지 않음)
interface Bike {
    id: number;
    name: string;
    brand?: string;
    model?: string;
    modelYear?: number;
    bikeType?: string;
    rideCategory?: string;
    frameSize?: string;
    frameMaterial?: string;
    weightKg?: number;
    wheelSize?: string;
    groupset?: string;
    shiftingType?: string;
    speeds?: number;
    chainringOuter?: number;
    chainringInner?: number;
    cassetteLabel?: string;
    purchaseDate?: string;
    purchasePrice?: number;
    totalDistance?: number;      // meters
    totalTime?: number;          // seconds, 이동시간 (moving)
    totalElapsedTime?: number;   // seconds, 총시간 (정지 포함)
    photoUrl?: string;
    isRetired?: boolean;
    isDefault?: boolean;
    notes?: string;
    createdAt?: string;
    updatedAt?: string;
}

const km = (meters?: number) =>
    ((meters ?? 0) / 1000).toLocaleString(undefined, { maximumFractionDigits: 0 });

const hours = (seconds?: number) =>
    ((seconds ?? 0) / 3600).toLocaleString(undefined, { maximumFractionDigits: 1 });

const won = (v?: number) => (v == null ? null : `${v.toLocaleString()}원`);

const date = (v?: string) => (v ? v.slice(0, 10) : null);

/** 값이 있는 항목만 라벨과 함께 렌더한다. */
function Field({ label, value }: { label: string; value?: string | number | null }) {
    if (value == null || value === '') return null;
    return (
        <div style={{ display: 'flex', gap: 8, fontSize: 13, lineHeight: 1.7 }}>
            <span style={{ color: '#94a3b8', minWidth: 62, flexShrink: 0 }}>{label}</span>
            <span style={{ color: '#334155' }}>{value}</span>
        </div>
    );
}

function BikeCard({ bike, onClick }: { bike: Bike; onClick: () => void }) {
    const retired = Boolean(bike.isRetired);

    const chainring =
        bike.chainringOuter && bike.chainringInner
            ? `${bike.chainringOuter}/${bike.chainringInner}T`
            : bike.chainringOuter
                ? `${bike.chainringOuter}T`
                : null;

    return (
        <div
            onClick={onClick}
            style={{
                border: '1px solid #e5e7eb',
                borderRadius: 10,
                padding: 16,
                cursor: 'pointer',
                background: '#fff',
                opacity: retired ? 0.72 : 1,
            }}
        >
            {bike.photoUrl && (
                <img
                    src={bike.photoUrl}
                    alt={bike.name}
                    style={{
                        width: '100%', height: 150, objectFit: 'cover',
                        borderRadius: 8, marginBottom: 12, display: 'block',
                    }}
                />
            )}

            <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                <strong style={{ fontSize: 16 }}>{bike.name}</strong>
                {bike.isDefault && <span style={badge('#2563eb')}>기본</span>}
                {retired && <span style={badge('#64748b')}>종료</span>}
            </div>

            <div style={{ color: '#666', fontSize: 13, margin: '2px 0 10px' }}>
                {[bike.brand, bike.model].filter(Boolean).join(' · ')}
                {bike.modelYear ? ` (${bike.modelYear})` : ''}
            </div>

            <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginBottom: 12 }}>
                {bike.bikeType && <span style={tag}>{bike.bikeType}</span>}
                {bike.rideCategory && <span style={tag}>{bike.rideCategory}</span>}
                {bike.frameMaterial && <span style={tag}>{bike.frameMaterial}</span>}
                {bike.frameSize && <span style={tag}>{bike.frameSize}</span>}
                {bike.wheelSize && <span style={tag}>{bike.wheelSize}</span>}
                {bike.shiftingType && <span style={tag}>{bike.shiftingType}</span>}
            </div>

            <div style={{ display: 'flex', gap: 20, marginBottom: 12 }}>
                <div>
                    <div style={{ fontSize: 20, fontWeight: 600 }}>{km(bike.totalDistance)}</div>
                    <div style={{ fontSize: 12, color: '#888' }}>km 누적</div>
                </div>
                <div>
                    <div style={{ fontSize: 20, fontWeight: 600 }}>{hours(bike.totalTime)}</div>
                    <div style={{ fontSize: 12, color: '#888' }}>이동시간</div>
                </div>
                <div>
                    <div style={{ fontSize: 20, fontWeight: 600 }}>{hours(bike.totalElapsedTime)}</div>
                    <div style={{ fontSize: 12, color: '#888' }}>총시간</div>
                </div>
            </div>

            <div style={{ borderTop: '1px solid #f1f5f9', paddingTop: 10 }}>
                <Field label="구동계" value={bike.groupset} />
                <Field label="단수" value={bike.speeds ? `${bike.speeds}단` : null} />
                <Field label="체인링" value={chainring} />
                <Field label="카세트" value={bike.cassetteLabel} />
                <Field label="무게" value={bike.weightKg ? `${bike.weightKg} kg` : null} />
                <Field label="구매일" value={date(bike.purchaseDate)} />
                <Field label="구매가" value={won(bike.purchasePrice)} />
                <Field label="등록일" value={date(bike.createdAt)} />
                <Field label="수정일" value={date(bike.updatedAt)} />
            </div>

            {bike.notes && (
                <div style={{
                    marginTop: 10, paddingTop: 10, borderTop: '1px solid #f1f5f9',
                    fontSize: 13, color: '#475569', whiteSpace: 'pre-wrap',
                }}>
                    {bike.notes}
                </div>
            )}
        </div>
    );
}

function Section({ title, bikes, onSelect }: {
    title: string;
    bikes: Bike[];
    onSelect: (id: number) => void;
}) {
    if (bikes.length === 0) return null;
    return (
        <section style={{ marginTop: 28 }}>
            <h3 style={{ margin: '0 0 12px', fontSize: 15, color: '#475569' }}>
                {title} <span style={{ color: '#94a3b8', fontWeight: 400 }}>{bikes.length}대</span>
            </h3>
            <div style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))',
                gap: 16,
            }}>
                {bikes.map((b) => (
                    <BikeCard key={b.id} bike={b} onClick={() => onSelect(b.id)} />
                ))}
            </div>
        </section>
    );
}

export default function BikeListPage() {
    const [bikes, setBikes] = useState<Bike[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const navigate = useNavigate();

    useEffect(() => {
        fetch('/api/bikes')
            .then((r) => {
                if (!r.ok) throw new Error(`불러오기 실패 (${r.status})`);
                return r.json();
            })
            .then(setBikes)
            .catch((e) => setError(e instanceof Error ? e.message : '오류'))
            .finally(() => setLoading(false));
    }, []);

    const active = bikes.filter((b) => !b.isRetired);
    const retired = bikes.filter((b) => b.isRetired);

    return (
        <div style={{ maxWidth: 980, margin: '0 auto', padding: 24 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <h2 style={{ margin: 0 }}>내 자전거</h2>
                <AdminOnly>
                    <Link to="/bikes/register" style={{
                        padding: '8px 14px', background: '#007bff', color: '#fff',
                        borderRadius: 6, textDecoration: 'none', fontSize: 14,
                    }}>+ 자전거 등록</Link>
                </AdminOnly>
            </div>

            {loading && <p style={{ marginTop: 24 }}>불러오는 중…</p>}
            {error && <p style={{ color: '#dc3545', marginTop: 24 }}>{error}</p>}
            {!loading && !error && bikes.length === 0 && (
                <p style={{ marginTop: 24, color: '#666' }}>등록된 자전거가 없습니다.</p>
            )}

            <Section title="🚴 가동중" bikes={active} onSelect={(id) => navigate(`/bikes/${id}`)} />
            <Section title="🏁 종료" bikes={retired} onSelect={(id) => navigate(`/bikes/${id}`)} />
        </div>
    );
}

const tag: React.CSSProperties = {
    fontSize: 11, padding: '2px 8px', background: '#f1f5f9',
    color: '#475569', borderRadius: 4,
};
const badge = (color: string): React.CSSProperties => ({
    fontSize: 11, padding: '2px 8px', background: color, color: '#fff', borderRadius: 4,
});