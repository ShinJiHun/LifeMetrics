// 피팅 데이터: 신체 치수(발 사이즈 포함)와 자전거별 피팅값을 한 화면에서 입력·조회한다.
// 자전거별 피팅값은 자전거 상세의 피팅 기록(bike_fit)과 같은 데이터다.

import {useEffect, useState} from "react";
import {Link} from "react-router-dom";
import BikeFitSection, {fitStyles as S} from "@/components/bike/BikeFitSection";
import RiderMeasurementSection from "@/components/bike/RiderMeasurementSection";
import type {BikeFit} from "@/api/bikeFit";
import type {RiderMeasurement} from "@/api/riderMeasurement";

interface BikeItem {
    id: number;
    name: string;
    brand?: string;
    model?: string;
    isRetired?: boolean;
}

/** LeMond 공식: 안장 높이(BB 중심 ~ 안장 상단) ≈ 인심 × 0.883 */
const LEMOND_RATIO = 0.883;

export default function FittingDataPage() {
    const [bikes, setBikes] = useState<BikeItem[]>([]);
    const [bikeId, setBikeId] = useState<number | null>(null);
    const [bikeError, setBikeError] = useState<string | null>(null);
    const [measurement, setMeasurement] = useState<RiderMeasurement | undefined>();
    const [fit, setFit] = useState<BikeFit | undefined>();

    useEffect(() => {
        fetch("/api/bikes")
            .then((r) => {
                if (!r.ok) throw new Error(`자전거 목록을 불러오지 못했습니다 (${r.status})`);
                return r.json();
            })
            .then((list: BikeItem[]) => {
                // 타는 자전거를 앞에, 종료한 자전거는 뒤로
                const sorted = [...list].sort((a, b) => Number(Boolean(a.isRetired)) - Number(Boolean(b.isRetired)));
                setBikes(sorted);
                if (sorted.length) setBikeId(sorted[0].id);
            })
            .catch((e) => setBikeError(e.message));
    }, []);

    const inseam = measurement?.inseamMm;
    const lemond = inseam ? Math.round(inseam * LEMOND_RATIO) : null;
    const saddle = fit?.saddleHeightMm;

    return (
        <div style={{padding: 24, maxWidth: 960}}>
            <h2 style={{margin: "0 0 4px"}}>🔧 피팅 데이터</h2>
            <p style={{...S.desc, marginBottom: 24}}>
                몸 치수·발 사이즈와 자전거별 피팅값을 한곳에서 기록하고 봅니다.
            </p>

            <RiderMeasurementSection onLoaded={(l) => setMeasurement(l[0])}/>

            <section style={{marginBottom: 16}}>
                <h3 style={{margin: "0 0 8px"}}>🚲 자전거별 피팅값</h3>
                {bikeError && <p style={{color: "#dc3545", fontSize: 13}}>{bikeError}</p>}
                {!bikeError && bikes.length === 0 && <p style={S.empty}>등록된 자전거가 없습니다.</p>}
                {bikes.length > 0 && (
                    <div style={S.dateTabs}>
                        {bikes.map((b) => (
                            <button key={b.id} onClick={() => { setBikeId(b.id); setFit(undefined); }}
                                    style={{...S.dateTab, ...(b.id === bikeId ? S.dateTabActive : {})}}>
                                {b.name}{b.isRetired && " (종료)"}
                            </button>
                        ))}
                    </div>
                )}
            </section>

            {bikeId != null && (
                <>
                    {lemond != null && (
                        <div style={{...S.notes, marginTop: 0, marginBottom: 16}}>
                            참고: 인심 {inseam}mm 기준 LeMond 공식(× {LEMOND_RATIO}) 안장 높이 ≈ <strong>{lemond}mm</strong>
                            {saddle != null && (
                                <> · 이 자전거 현재 {saddle}mm ({saddle - lemond > 0 ? "+" : ""}{Math.round((saddle - lemond) * 10) / 10}mm)</>
                            )}
                            <div style={S.hint}>출발점으로 쓰는 경험식이라 크랭크 길이·클릿·페달 스택에 따라 실제 최적값은 달라집니다.</div>
                        </div>
                    )}
                    <BikeFitSection key={bikeId} bikeId={bikeId} onLoaded={(l) => setFit(l[0])}/>
                    <p style={S.muted}>
                        자전거 상세 화면에서도 같은 피팅 기록을 볼 수 있습니다 → <Link to={`/bikes/${bikeId}`}>자전거 상세</Link>
                    </p>
                </>
            )}
        </div>
    );
}
