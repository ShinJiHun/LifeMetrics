import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

interface Bike {
    id: number;
    name: string;
    brand?: string;
    model?: string;
    modelYear?: number;
    bikeType?: string;
    rideCategory?: string;
    frameMaterial?: string;
    frameSize?: string;
    weightKg?: number;
    groupset?: string;
    shiftingType?: string;
    speeds?: number;
    chainringOuter?: number;
    chainringInner?: number;
    cassetteLabel?: string;
    totalDistance?: number; // meters
    isDefault?: boolean;
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

    return (
        <div style={{ maxWidth: 720, margin: '0 auto', padding: 24 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <h2 style={{ margin: 0 }}>내 자전거</h2>
                <Link to="/bikes/register" style={{
                    padding: '8px 14px', background: '#007bff', color: '#fff',
                    borderRadius: 6, textDecoration: 'none', fontSize: 14,
                }}>+ 자전거 등록</Link>
            </div>

            {loading && <p style={{ marginTop: 24 }}>불러오는 중…</p>}
            {error && <p style={{ color: '#dc3545', marginTop: 24 }}>{error}</p>}
            {!loading && !error && bikes.length === 0 && (
                <p style={{ marginTop: 24, color: '#666' }}>등록된 자전거가 없습니다. 우측 상단에서 등록하세요.</p>
            )}

            <div style={{
                display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))',
                gap: 16, marginTop: 20,
            }}>
                {bikes.map((b) => (
                    <div key={b.id}
                         onClick={() => navigate(`/bikes/${b.id}`)}
                         style={{
                             border: '1px solid #e5e7eb', borderRadius: 10, padding: 16,
                             cursor: 'pointer', background: '#fff',
                         }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                            <strong style={{ fontSize: 16 }}>{b.name}</strong>
                            {b.isDefault && <span style={badge('#2563eb')}>기본</span>}
                        </div>
                        <div style={{ color: '#666', fontSize: 13, margin: '2px 0 10px' }}>
                            {[b.brand, b.model].filter(Boolean).join(' · ')}
                            {b.modelYear ? ` (${b.modelYear})` : ''}
                        </div>

                        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginBottom: 10 }}>
                            {b.bikeType && <span style={tag}>{b.bikeType}</span>}
                            {b.rideCategory && <span style={tag}>{b.rideCategory}</span>}
                            {b.frameMaterial && <span style={tag}>{b.frameMaterial}</span>}
                            {b.frameSize && <span style={tag}>{b.frameSize}</span>}
                        </div>

                        <div style={{ fontSize: 20, fontWeight: 600 }}>
                            {((b.totalDistance ?? 0) / 1000).toLocaleString(undefined, { maximumFractionDigits: 0 })} km
                            <span style={{ fontSize: 12, color: '#888', fontWeight: 400 }}> 누적</span>
                        </div>

                        <div style={{ marginTop: 10, fontSize: 13, color: '#444', lineHeight: 1.6 }}>
                            {b.groupset && <div>⚙️ {b.groupset}{b.speeds ? ` · ${b.speeds}단` : ''}</div>}
                            {b.chainringOuter && b.chainringInner && (
                                <div>🔩 {b.chainringOuter}/{b.chainringInner}T{b.cassetteLabel ? ` · ${b.cassetteLabel}` : ''}</div>
                            )}
                        </div>
                    </div>
                ))}
            </div>
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
