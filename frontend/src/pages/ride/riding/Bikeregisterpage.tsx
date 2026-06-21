import { useState } from 'react';

type Spec = {
    brand?: string; model?: string; modelYear?: number | string;
    bikeType?: string; rideCategory?: string; frameMaterial?: string;
    frameSize?: string; weightKg?: number | string; wheelSize?: string;
    groupset?: string; shiftingType?: string; speeds?: number | string;
    chainringOuter?: number | string; chainringInner?: number | string; cassetteLabel?: string;
};

// API(snake_case) -> 폼(camelCase)
function fromApi(j: any): Spec {
    return {
        brand: j.brand ?? '', model: j.model ?? '', modelYear: j.model_year ?? '',
        bikeType: j.bike_type ?? '', rideCategory: j.ride_category ?? '',
        frameMaterial: j.frame_material ?? '', frameSize: j.frame_size ?? '',
        weightKg: j.weight_kg ?? '', wheelSize: j.wheel_size ?? '',
        groupset: j.groupset ?? '', shiftingType: j.shifting_type ?? '', speeds: j.speeds ?? '',
        chainringOuter: j.chainring_outer ?? '', chainringInner: j.chainring_inner ?? '',
        cassetteLabel: j.cassette_label ?? '',
    };
}

export default function BikeRegisterPage() {
    const [form, setForm] = useState<Spec>({});
    const [query, setQuery] = useState('');
    const [busy, setBusy] = useState('');
    const [notes, setNotes] = useState('');
    const [sources, setSources] = useState<string[]>([]);
    const [error, setError] = useState('');
    const [savedId, setSavedId] = useState<number | null>(null);

    const set = (k: keyof Spec, v: string) => setForm((f) => ({ ...f, [k]: v }));

    const applySpec = (j: any) => {
        setForm((f) => ({ ...f, ...fromApi(j) }));
        setNotes(j.confidence_notes ?? '');
        setSources(Array.isArray(j.sources) ? j.sources : []);
    };

    const search = async () => {
        if (!query.trim()) return;
        setError(''); setBusy('search'); setSavedId(null);
        try {
            const res = await fetch('/api/bikes/search-spec', {
                method: 'POST', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ model: query }),
            });
            if (!res.ok) throw new Error('검색 실패 (' + res.status + ')');
            applySpec(await res.json());
        } catch (e) { setError(e instanceof Error ? e.message : '오류'); }
        finally { setBusy(''); }
    };

    const onImage = async (e: React.ChangeEvent<HTMLInputElement>) => {
        const file = e.target.files?.[0];
        if (!file) return;
        setError(''); setBusy('image'); setSavedId(null);
        try {
            const fd = new FormData();
            fd.append('image', file);
            const res = await fetch('/api/bikes/extract-image', { method: 'POST', body: fd });
            if (!res.ok) throw new Error('이미지 분석 실패 (' + res.status + ')');
            applySpec(await res.json());
        } catch (e) { setError(e instanceof Error ? e.message : '오류'); }
        finally { setBusy(''); }
    };

    const save = async () => {
        if (!form.brand || !form.model) { setError('브랜드/제품명은 필수입니다.'); return; }
        setError(''); setBusy('save');
        try {
            const num = (v: any) => (v === '' || v == null ? null : Number(v));
            const payload = {
                ...form,
                modelYear: num(form.modelYear), weightKg: num(form.weightKg), speeds: num(form.speeds),
                chainringOuter: num(form.chainringOuter), chainringInner: num(form.chainringInner),
            };
            const res = await fetch('/api/bikes', {
                method: 'POST', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload),
            });
            if (!res.ok) throw new Error('저장 실패 (' + res.status + ')');
            const saved = await res.json();
            setSavedId(saved.id);
        } catch (e) { setError(e instanceof Error ? e.message : '오류'); }
        finally { setBusy(''); }
    };

    const field = (label: string, k: keyof Spec, hl = false) => (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
            <label style={{ fontSize: 13, color: hl ? '#b45309' : '#555' }}>
                {label}{hl ? ' ⚠ 확인' : ''}
            </label>
            <input value={(form[k] ?? '') as string} onChange={(e) => set(k, e.target.value)}
                   style={{ padding: '8px 10px', border: '1px solid ' + (hl ? '#f59e0b' : '#ccc'),
                       borderRadius: 6, fontSize: 14 }} />
        </div>
    );

    return (
        <div style={{ maxWidth: 640, margin: '0 auto', padding: 24 }}>
            <h2>자전거 등록</h2>

            <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
                <input value={query} onChange={(e) => setQuery(e.target.value)}
                       onKeyDown={(e) => e.key === 'Enter' && search()}
                       placeholder="모델명 검색 (예: Gusto GTR Pro TE)"
                       style={{ flex: 1, padding: '8px 10px', border: '1px solid #ccc', borderRadius: 6, fontSize: 14 }} />
                <button onClick={search} disabled={busy !== ''} style={btn}>
                    {busy === 'search' ? '검색 중…' : '모델 검색'}
                </button>
            </div>
            <label style={{ display: 'inline-block', marginTop: 10, fontSize: 13, color: '#555' }}>
                또는 스펙 이미지 업로드:{' '}
                <input type="file" accept="image/*" onChange={onImage} disabled={busy !== ''} />
            </label>
            {busy === 'image' && <span style={{ marginLeft: 8, fontSize: 13 }}>분석 중…</span>}

            {notes && (
                <p style={{ marginTop: 12, padding: 10, background: '#fffbeb', border: '1px solid #fde68a',
                    borderRadius: 6, fontSize: 13, color: '#92400e' }}>⚠ {notes}</p>
            )}

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginTop: 16 }}>
                {field('브랜드 *', 'brand')}
                {field('제품명 *', 'model')}
                {field('연식', 'modelYear')}
                {field('타입 (ROAD/GRAVEL…)', 'bikeType')}
                {field('카테고리', 'rideCategory')}
                {field('프레임 소재', 'frameMaterial')}
                {field('사이즈', 'frameSize')}
                {field('무게(kg)', 'weightKg')}
                {field('휠 규격', 'wheelSize')}
                {field('구동계', 'groupset')}
                {field('변속(DI2/AXS…)', 'shiftingType')}
                {field('단수', 'speeds')}
                {field('체인링(큰)', 'chainringOuter', true)}
                {field('체인링(작은)', 'chainringInner', true)}
                {field('카세트', 'cassetteLabel')}
            </div>

            <button onClick={save} disabled={busy !== ''}
                    style={{ ...btn, width: '100%', marginTop: 20, background: '#16a34a' }}>
                {busy === 'save' ? '저장 중…' : '등록'}
            </button>

            {error && <p style={{ color: '#dc3545', marginTop: 12 }}>{error}</p>}
            {savedId && <p style={{ color: '#16a34a', marginTop: 12 }}>등록 완료 ✓ (id {savedId})</p>}

            {sources.length > 0 && (
                <div style={{ marginTop: 16, fontSize: 12, color: '#888' }}>
                    출처: {sources.map((s, i) => (
                    <a key={i} href={s} target="_blank" rel="noreferrer" style={{ marginRight: 8 }}>[{i + 1}]</a>
                ))}
                </div>
            )}
        </div>
    );
}

const btn: React.CSSProperties = {
    padding: '8px 14px', border: 'none', borderRadius: 6,
    background: '#007bff', color: '#fff', fontSize: 14, cursor: 'pointer',
};