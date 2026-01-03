import type { MetricKey } from "./MetricKey";

export type MetricMeta = {
    key: MetricKey;
    label: string;
    unit: string;
    color: string;
};
