export type DeviceType = "HEAD_UNIT" | "SPEED_SENSOR" | "CADENCE_SENSOR" | "HEART_RATE" | "POWER_METER";

export const DEVICE_TYPES: { type: DeviceType; label: string; icon: string }[] = [
    {type: "HEAD_UNIT", label: "헤드유닛", icon: "🖥️"},
    {type: "POWER_METER", label: "파워미터", icon: "⚡"},
    {type: "HEART_RATE", label: "심박계", icon: "❤️"},
    {type: "SPEED_SENSOR", label: "속도 센서", icon: "🌀"},
    {type: "CADENCE_SENSOR", label: "케이던스 센서", icon: "🦵"},
];

export interface Device {
    id?: number;
    deviceType: DeviceType;
    manufacturer: string | null;
    model: string | null;
    userLabel: string | null;
    serialNumber: string | null;
    firmwareVersion: string | null;
    isActive: boolean;
    startDate: string | null;   // YYYY-MM-DD, 사용 시작일
    endDate: string | null;     // YYYY-MM-DD, 사용 종료일
}

export async function fetchDevices(): Promise<Device[]> {
    const res = await fetch(`/api/devices?userId=1`);
    if (!res.ok) throw new Error(`기기 목록을 불러오지 못했습니다 (${res.status})`);
    return res.json();
}

export async function saveDevice(device: Device): Promise<Device> {
    const res = await fetch(device.id ? `/api/devices/${device.id}` : `/api/devices?userId=1`, {
        method: device.id ? "PUT" : "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify(device),
    });
    if (!res.ok) throw new Error(`저장 실패 (${res.status})`);
    return res.json();
}
