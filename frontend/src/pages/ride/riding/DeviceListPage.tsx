// 센서·기기: 헤드유닛 / 파워미터 / 심박계 / 속도·케이던스 센서
// 사용 시작일~종료일은 AI 라이딩 분석이 "그날 쓰던 센서"를 고를 때 쓰인다.

import {useEffect, useState} from "react";
import type {ChangeEvent, CSSProperties} from "react";
import AdminOnly from "@/components/common/AdminOnly";
import {DEVICE_TYPES, fetchDevices, saveDevice} from "@/api/devices";
import type {Device, DeviceType} from "@/api/devices";

const emptyDevice = (type: DeviceType): Device => ({
    deviceType: type,
    manufacturer: null,
    model: null,
    userLabel: null,
    serialNumber: null,
    firmwareVersion: null,
    isActive: true,
    startDate: null,
    endDate: null,
});

export default function DeviceListPage() {
    const [devices, setDevices] = useState<Device[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [editing, setEditing] = useState<Device | null>(null);

    const load = () => {
        fetchDevices()
            .then(setDevices)
            .catch((e) => setError(e.message))
            .finally(() => setLoading(false));
    };

    useEffect(load, []);

    if (loading) return <div style={S.page}>불러오는 중…</div>;
    if (error) return <div style={{...S.page, color: "#dc3545"}}>{error}</div>;

    return (
        <div style={S.page}>
            <div style={S.titleRow}>
                <h2 style={{margin: 0}}>📡 센서·기기</h2>
                <AdminOnly>
                    <button style={S.primaryBtn} onClick={() => setEditing(emptyDevice("HEAD_UNIT"))}>
                        ➕ 기기 추가
                    </button>
                </AdminOnly>
            </div>
            <p style={S.desc}>
                사용 기간을 적어두면 AI 라이딩 분석이 그날 쓰던 센서를 기준으로 데이터를 해석합니다.
            </p>

            {editing && !editing.id && (
                <DeviceForm device={editing} onCancel={() => setEditing(null)}
                            onSaved={() => { setEditing(null); load(); }}/>
            )}

            {DEVICE_TYPES.map(({type, label, icon}) => {
                const list = devices.filter((d) => d.deviceType === type);
                return (
                    <section key={type} style={{marginBottom: 24}}>
                        <h3 style={S.sectionTitle}>{icon} {label} <span style={S.count}>{list.length}</span></h3>
                        {list.length === 0 ? (
                            <p style={S.empty}>등록된 {label}가 없습니다.</p>
                        ) : (
                            <div style={S.grid}>
                                {list.map((d) =>
                                    editing && editing.id === d.id ? (
                                        <DeviceForm key={d.id} device={editing} onCancel={() => setEditing(null)}
                                                    onSaved={() => { setEditing(null); load(); }}/>
                                    ) : (
                                        <DeviceCard key={d.id} device={d} onEdit={() => setEditing(d)}/>
                                    )
                                )}
                            </div>
                        )}
                    </section>
                );
            })}
        </div>
    );
}

function DeviceCard({device, onEdit}: { device: Device; onEdit: () => void }) {
    const retired = !device.isActive;
    const period = [device.startDate ?? "?", device.endDate ?? (retired ? "?" : "사용 중")].join(" ~ ");
    return (
        <div style={{...S.card, opacity: retired ? 0.6 : 1}}>
            <div style={{display: "flex", alignItems: "center", gap: 8}}>
                <strong style={{fontSize: 15}}>
                    {device.userLabel || [device.manufacturer, device.model].filter(Boolean).join(" ") || "이름 없음"}
                </strong>
                {retired && <span style={S.badge}>사용 중지</span>}
            </div>
            <div style={S.meta}>{[device.manufacturer, device.model].filter(Boolean).join(" · ")}</div>
            <div style={S.meta}>📅 {period}</div>
            {(device.serialNumber || device.firmwareVersion) && (
                <div style={S.meta}>
                    {device.serialNumber && `S/N ${device.serialNumber}`}
                    {device.serialNumber && device.firmwareVersion && " · "}
                    {device.firmwareVersion && `FW ${device.firmwareVersion}`}
                </div>
            )}
            <AdminOnly>
                <button style={{...S.ghostBtn, marginTop: 10}} onClick={onEdit}>✏️ 수정</button>
            </AdminOnly>
        </div>
    );
}

function DeviceForm({device, onCancel, onSaved}: {
    device: Device;
    onCancel: () => void;
    onSaved: () => void;
}) {
    const [form, setForm] = useState<Device>(device);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const set = <K extends keyof Device>(key: K, value: Device[K]) => setForm((f) => ({...f, [key]: value}));
    const text = (key: "manufacturer" | "model" | "userLabel" | "serialNumber" | "firmwareVersion") => ({
        value: form[key] ?? "",
        onChange: (e: ChangeEvent<HTMLInputElement>) => set(key, e.target.value || null),
        style: S.input,
    });

    const handleSave = async () => {
        setSaving(true);
        setError(null);
        try {
            await saveDevice(form);
            onSaved();
        } catch (e) {
            setError(e instanceof Error ? e.message : "저장 실패");
            setSaving(false);
        }
    };

    return (
        <div style={{...S.card, borderColor: "#93c5fd", gridColumn: "1 / -1"}}>
            <div style={S.formGrid}>
                <label style={S.label}>종류
                    <select style={S.input} value={form.deviceType}
                            onChange={(e) => set("deviceType", e.target.value as DeviceType)}>
                        {DEVICE_TYPES.map((t) => <option key={t.type} value={t.type}>{t.icon} {t.label}</option>)}
                    </select>
                </label>
                <label style={S.label}>표시 이름 <input {...text("userLabel")} placeholder="예) 파베로 아씨오마 듀오"/></label>
                <label style={S.label}>제조사 <input {...text("manufacturer")} placeholder="예) Favero"/></label>
                <label style={S.label}>모델 <input {...text("model")} placeholder="예) Assioma DUO"/></label>
                <label style={S.label}>사용 시작일
                    <input type="date" style={S.input} value={form.startDate ?? ""}
                           onChange={(e) => set("startDate", e.target.value || null)}/>
                </label>
                <label style={S.label}>사용 종료일
                    <input type="date" style={S.input} value={form.endDate ?? ""}
                           onChange={(e) => set("endDate", e.target.value || null)}/>
                </label>
                <label style={S.label}>시리얼 번호 <input {...text("serialNumber")}/></label>
                <label style={S.label}>펌웨어 <input {...text("firmwareVersion")}/></label>
            </div>
            <label style={{...S.label, flexDirection: "row", alignItems: "center", gap: 6, marginTop: 10}}>
                <input type="checkbox" checked={!form.isActive} onChange={(e) => set("isActive", !e.target.checked)}/>
                사용 중지 (더 이상 쓰지 않는 기기)
            </label>
            <div style={{display: "flex", gap: 8, marginTop: 12, alignItems: "center"}}>
                <button style={S.primaryBtn} onClick={handleSave} disabled={saving}>{saving ? "저장 중…" : "저장"}</button>
                <button style={S.ghostBtn} onClick={onCancel}>취소</button>
                {error && <span style={{color: "#dc3545", fontSize: 13}}>{error}</span>}
            </div>
        </div>
    );
}

const S: Record<string, CSSProperties> = {
    page: {maxWidth: 980, margin: "0 auto", padding: 24},
    titleRow: {display: "flex", alignItems: "center", justifyContent: "space-between", gap: 8, flexWrap: "wrap"},
    desc: {color: "#94a3b8", fontSize: 13, margin: "6px 0 20px"},
    sectionTitle: {margin: "0 0 10px", fontSize: 15},
    count: {color: "#94a3b8", fontWeight: 400, fontSize: 13},
    empty: {color: "#94a3b8", fontSize: 13, margin: 0},
    grid: {display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(260px, 1fr))", gap: 12},
    card: {border: "1px solid #e5e7eb", borderRadius: 10, padding: 14, background: "#fff"},
    meta: {color: "#64748b", fontSize: 13, marginTop: 4},
    badge: {fontSize: 11, padding: "1px 6px", borderRadius: 999, background: "#f1f5f9", color: "#64748b"},
    formGrid: {display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(200px, 1fr))", gap: 10},
    label: {display: "flex", flexDirection: "column", gap: 4, fontSize: 12, color: "#475569"},
    input: {padding: "6px 8px", border: "1px solid #cbd5e1", borderRadius: 6, fontSize: 13, font: "inherit"},
    primaryBtn: {
        padding: "6px 14px", background: "#2563eb", border: "none", borderRadius: 8,
        color: "#fff", fontSize: 13, fontWeight: 600, cursor: "pointer",
    },
    ghostBtn: {
        padding: "5px 12px", background: "#fff", border: "1px solid #cbd5e1", borderRadius: 8,
        color: "#475569", fontSize: 13, cursor: "pointer",
    },
};
