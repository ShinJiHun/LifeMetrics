import "@/styles/body-record.css";
import MetricCard from "./MetricCard.tsx";
import type { MetricKey } from "@/types/MetricKey.ts";
import type { BodySummaryRecord } from "@/types/BodySummaryRecord.ts";

interface Props {
    latest: BodySummaryRecord;
    selected: MetricKey;
    onSelect: (key: MetricKey) => void;
}

export default function BodyMetricCards({
                                            latest,
                                            selected,
                                            onSelect,
                                        }: Props) {
    return (
        <div className="metric-grid-3x2">
            <MetricCard
                title="체중"
                value={latest.weight}
                unit="kg"
                delta={latest.weightDelta}
                active={selected === "weight"}
                onClick={() => onSelect("weight")}
            />

            <MetricCard
                title="골격근량"
                value={latest.skeletalMuscleMass}
                unit="kg"
                delta={latest.skeletalMuscleMassDelta}
                active={selected === "skeletalMuscleMass"}
                onClick={() => onSelect("skeletalMuscleMass")}
            />

            <MetricCard
                title="체지방량"
                value={latest.bodyFatMass}
                unit="kg"
                active={selected === "bodyFatMass"}
                onClick={() => onSelect("bodyFatMass")}
            />

            <MetricCard
                title="체지방률"
                value={latest.bodyFatPercentage}
                unit="%"
                delta={latest.bodyFatPercentageDelta}
                active={selected === "bodyFatPercentage"}
                onClick={() => onSelect("bodyFatPercentage")}
            />

            <MetricCard
                title="BMI"
                value={latest.bmi}
                active={selected === "bmi"}
                onClick={() => onSelect("bmi")}
            />

            <MetricCard
                title="내장지방"
                value={latest.visceralFatLevel}
                active={selected === "visceralFatLevel"}
                onClick={() => onSelect("visceralFatLevel")}
            />
        </div>
    );
}
